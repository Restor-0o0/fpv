package org.restor.fpv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.restor.fpv.Fpv;
import org.restor.fpv.race.GhostFrame;
import org.restor.fpv.race.GhostRecording;
import org.restor.fpv.replay.ReplayRecording;
import org.restor.fpv.render.FPVOverlay;

// Client-only playback of race ghosts (FPV_IDEAS §1 "Рендер"). Ghosts are NOT server entities:
// no collisions, no physics, no chunk loading — just a translucent model drawn each frame by
// interpolating between two recorded telemetry frames. Playback is tick-synced to the live run
// via the run's startTime, so the ghost and the pilot leave the gate together.
@Mod.EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class GhostRenderer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ResourceLocation TEXTURE = new ResourceLocation(Fpv.MODID, "textures/entity/drone.png");
    private static final float ALPHA = 0.42f;

    // A ghost queued for playback and the colour that identifies which record it is.
    private record Ghost(GhostRecording rec, float r, float g, float b) {}

    private static final java.util.List<Ghost> GHOSTS = new java.util.ArrayList<>();
    private static long startTime;

    // Called from GhostDataPacket.handle when a run starts. personal/overall may be null; when
    // personalIsOverall the player owns the track record, so it's drawn once in a "record" colour.
    public static void setGhosts(long startTime, boolean personalIsOverall,
                                 GhostRecording personal, GhostRecording overall) {
        GHOSTS.clear();
        GhostRenderer.startTime = startTime;
        if (personal != null) {
            if (personalIsOverall) GHOSTS.add(new Ghost(personal, 0.75f, 0.45f, 1.0f)); // violet = your record
            else GHOSTS.add(new Ghost(personal, 0.30f, 0.85f, 1.0f));                    // cyan = personal best
        }
        if (overall != null) GHOSTS.add(new Ghost(overall, 1.0f, 0.80f, 0.25f));        // gold = track record
    }

    public static void clear() { GHOSTS.clear(); }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (MC.level == null) return;

        boolean renderRaceGhosts = !GHOSTS.isEmpty() && FPVOverlay.raceActive();
        ReplayRecording replay = ReplayManager.playback();
        if (!renderRaceGhosts && replay == null) return;

        float partial = event.getPartialTick();
        float elapsed = (MC.level.getGameTime() - startTime) + partial;
        if (elapsed < 0) elapsed = 0;

        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(TEXTURE));

        if (renderRaceGhosts) {
            for (Ghost ghost : GHOSTS) {
                drawGhost(ghost, elapsed, cam, pose, vc);
            }
        }
        if (replay != null) drawReplay(replay, ReplayManager.renderFrame(partial), cam, pose, vc);
        buffers.endBatch(RenderType.entityTranslucentCull(TEXTURE));
    }

    private static void drawGhost(Ghost ghost, float elapsed, Vec3 cam, PoseStack pose, VertexConsumer vc) {
        GhostRecording rec = ghost.rec();
        int n = rec.frameCount();
        if (n == 0) return;

        // Frame index = ticks since run start; hold the final frame once the ghost has finished.
        float idxf = Math.min(elapsed, n - 1f);
        int i0 = (int) Math.floor(idxf);
        int i1 = Math.min(i0 + 1, n - 1);
        float t = idxf - i0;
        GhostFrame f0 = rec.frame(i0);
        GhostFrame f1 = rec.frame(i1);

        drawModel(rec.modelType(), f0, f1, t, cam, pose, vc, ghost.r(), ghost.g(), ghost.b());
    }

    private static void drawReplay(ReplayRecording replay, double frame, Vec3 cam,
                                   PoseStack pose, VertexConsumer vc) {
        int i0 = (int) Math.floor(frame);
        int i1 = Math.min(i0 + 1, replay.frameCount() - 1);
        float t = (float) (frame - i0);
        drawModel(replay.modelType(), replay.frame(i0), replay.frame(i1), t,
            cam, pose, vc, 1.0f, 0.35f, 0.22f);
    }

    private static void drawModel(int modelType, GhostFrame f0, GhostFrame f1, float t,
                                  Vec3 cam, PoseStack pose, VertexConsumer vc,
                                  float r, float g, float b) {
        double x = Mth.lerp(t, f0.x(), f1.x());
        double y = Mth.lerp(t, f0.y(), f1.y());
        double z = Mth.lerp(t, f0.z(), f1.z());

        // Slerp the attitude to avoid the yaw/roll flip that per-axis Euler lerp hits near ±90°
        // pitch (same reasoning as DroneRenderer).
        Quaternionf q = modelQuat(f0.yaw(), f0.pitch(), f0.roll())
            .slerp(modelQuat(f1.yaw(), f1.pitch(), f1.roll()), t);

        pose.pushPose();
        pose.translate(x - cam.x, y - cam.y, z - cam.z);
        pose.mulPose(q);
        float s = scaleFor(modelType);
        pose.scale(s, s, s);

        Matrix4f mat = pose.last().pose();
        int light = 0xF000F0; // full bright so ghosts read in shadow
        if (modelType == 3) drawPlane(vc, mat, light, r, g, b);
        else drawQuad(vc, mat, light, r, g, b);
        pose.popPose();
    }

    // Same composition DroneRenderer uses for the world-space model: Ry(yaw-180)Rx(-pitch)Rz(roll).
    private static Quaternionf modelQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(180f - yaw))
            .rotateX((float) Math.toRadians(-pitch))
            .rotateZ((float) Math.toRadians(-roll));
    }

    private static float scaleFor(int modelType) {
        return switch (modelType) {
            case 1 -> 0.35f; // tiny whoop
            case 2 -> 0.5f;  // cinewhoop
            case 3 -> 1.0f;  // plane
            default -> 0.6f; // 5" quad
        };
    }

    private static void drawQuad(VertexConsumer vc, Matrix4f mat, int light,
                                 float r, float g, float b) {
        // Central body.
        box(vc, mat, light, -0.18f, -0.06f, -0.14f, 0.18f, 0.06f, 0.18f, r, g, b);
        // Four arms + prop discs at the corners.
        float al = 0.5f, at = 0.03f;
        float[][] corners = {{al, -al}, {-al, -al}, {al, al}, {-al, al}};
        for (float[] c : corners) {
            float cx = c[0], cz = c[1];
            box(vc, mat, light, Math.min(0, cx), -at, Math.min(0, cz), Math.max(0, cx), at, Math.max(0, cz), r, g, b);
            box(vc, mat, light, cx - 0.2f, 0.03f, cz - 0.2f, cx + 0.2f, 0.05f, cz + 0.2f, r, g, b);
        }
    }

    private static void drawPlane(VertexConsumer vc, Matrix4f mat, int light,
                                  float r, float g, float b) {
        box(vc, mat, light, -0.08f, -0.05f, -0.5f, 0.08f, 0.06f, 0.5f, r, g, b);   // fuselage
        box(vc, mat, light, -0.7f, -0.01f, -0.05f, 0.7f, 0.02f, 0.18f, r, g, b);   // wings
        box(vc, mat, light, -0.25f, -0.01f, 0.38f, 0.25f, 0.02f, 0.5f, r, g, b);   // tailplane
        box(vc, mat, light, -0.02f, 0.02f, 0.4f, 0.02f, 0.22f, 0.5f, r, g, b);     // fin
    }

    // Translucent axis-aligned box in the current pose (mirrors DroneRenderer.renderBox but with alpha).
    private static void box(VertexConsumer c, Matrix4f mat, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b) {
        float a = ALPHA;
        q(c, mat, light, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a, 0, 1, 0);   // top
        q(c, mat, light, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1, r, g, b, a, 0, -1, 0);  // bottom
        q(c, mat, light, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a, 0, 0, -1);  // front
        q(c, mat, light, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2, r, g, b, a, 0, 0, 1);   // back
        q(c, mat, light, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a, 1, 0, 0);   // right
        q(c, mat, light, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2, r, g, b, a, -1, 0, 0);  // left
    }

    private static void q(VertexConsumer c, Matrix4f mat, int light,
                          float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          float r, float g, float b, float a, float nx, float ny, float nz) {
        c.vertex(mat, ax, ay, az).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
        c.vertex(mat, bx, by, bz).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
        c.vertex(mat, cx, cy, cz).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
        c.vertex(mat, dx, dy, dz).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
    }
}
