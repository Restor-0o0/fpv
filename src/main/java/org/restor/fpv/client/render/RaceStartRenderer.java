package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;
import org.restor.fpv.client.ClientRaceState;

import java.util.ArrayList;
import java.util.List;

public class RaceStartRenderer implements BlockEntityRenderer<RaceStartBlockEntity> {
    private final Font font;

    public RaceStartRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RaceStartBlockEntity start) {
        return start.renderBoundingBox();
    }

    @Override
    public void render(RaceStartBlockEntity start, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (start.getRouteSize() <= 0) return;
        int c = start.getColor();
        float a = ((c >> 24) & 0xFF) / 255f;
        if (a <= 0f) a = 1f;

        VertexConsumer vc = buffer.getBuffer(RaceFrames.type());
        Matrix4f mat = pose.last().pose();
        BlockPos origin = start.getBlockPos();
        float half = start.getSize() / 2.0f;
        Vector3f center = local(origin, start.frameCenter());
        Vector3f n = start.normal().normalize();

        RaceFrames.hoop(vc, mat, center, n, half, org.restor.fpv.block.RaceGateBlock.FRAME_RADIUS,
            RaceFrames.shade(c, 1f), RaceFrames.WHITE, a);
        renderPlateText(pose, buffer, center, n, half, "START", 0xFF40FF40);

        if (ClientRaceState.active()) {
            BlockPos finishPos = start.getFinishPos();
            if (finishPos != null) {
                Vec3 fn = start.finishNormal();
                Vector3f finishNormal = new Vector3f((float) fn.x, (float) fn.y, (float) fn.z);
                if (finishNormal.lengthSquared() > 1e-6f) {
                    Vector3f finishCenter = local(origin, start.finishCenter());
                    RaceFrames.hoop(vc, mat, finishCenter, finishNormal, half, org.restor.fpv.block.RaceGateBlock.FRAME_RADIUS,
                        RaceFrames.WHITE, RaceFrames.DARK, a);
                    renderPlateText(pose, buffer, finishCenter, finishNormal, half, "FINISH", 0xFFFFFFFF);
                }
            }
            renderFirstSegment(start, vc, mat, a);
        }
    }

    private void renderPlateText(PoseStack pose, MultiBufferSource buffer, Vector3f center, Vector3f normal,
                                 float half, String text, int color) {
        Vector3f[] axes = RaceFrames.basis(normal);
        Vector3f p = new Vector3f(center)
            .add(new Vector3f(axes[1]).mul(half + 1.0f))
            .add(new Vector3f(axes[2]).mul(0.36f));
        pose.pushPose();
        pose.translate(p.x, p.y, p.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f(normal).normalize()));
        pose.scale(-0.14f, -0.14f, 0.14f);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(text, -font.width(text) / 2f, -4f, color, false, m, buffer,
            Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        pose.popPose();
    }

    private static Vector3f local(BlockPos origin, Vec3 world) {
        return new Vector3f((float) (world.x - origin.getX()), (float) (world.y - origin.getY()),
            (float) (world.z - origin.getZ()));
    }

    private static void renderFirstSegment(RaceStartBlockEntity start, VertexConsumer vc, Matrix4f mat, float alpha) {
        if (ClientRaceState.gatesHit() > 0) return;
        BlockPos first = start.getFirstRoutePos();
        if (first == null) return;
        Level level = start.getLevel();
        if (level == null) return;

        Vec3 target = new Vec3(first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5);
        Vec3 nextTarget = target;
        BlockEntity be = level.getBlockEntity(first);
        if (be instanceof RaceGateBlockEntity gate) {
            target = gate.center();
            nextTarget = routeCenter(level, gate.getNextRoutePos(), target);
        }

        BlockPos origin = start.getBlockPos();
        Vector3f from = local(origin, start.frameCenter());
        Vector3f to = local(origin, target);
        Vector3f nextPoint = local(origin, nextTarget);
        Vector3f dir = new Vector3f(to).sub(from);
        if (dir.lengthSquared() < 1e-4f) return;
        float handleOut = curveHandle(from, from, to);
        float handleIn = curveHandle(to, from, nextPoint);
        Vector3f p1 = new Vector3f(from).add(dir.normalize().mul(handleOut));
        Vector3f p2 = new Vector3f(to).sub(tangent(to, from, nextPoint, handleIn));

        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(from));
        for (int i = 1; i <= 24; i++) points.add(cubic(from, p1, p2, to, i / 24.0f));
        RaceFrames.ribbon(vc, mat, points, 0.11f, RaceFrames.NEXT, alpha, true);
    }

    private static Vec3 routeCenter(Level level, BlockPos pos, Vec3 fallback) {
        if (pos == null) return fallback;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RaceGateBlockEntity gate) return gate.center();
        if (be instanceof RaceStartBlockEntity start) return start.frameCenter();
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static Vector3f tangent(Vector3f point, Vector3f prevPoint, Vector3f nextPoint, float handle) {
        Vector3f t = new Vector3f(nextPoint).sub(prevPoint);
        if (t.lengthSquared() < 1e-4f) t.set(nextPoint).sub(point);
        if (t.lengthSquared() < 1e-4f) return new Vector3f();
        return t.normalize().mul(handle);
    }

    private static float curveHandle(Vector3f point, Vector3f prevPoint, Vector3f nextPoint) {
        float inLen = point.distance(prevPoint);
        float outLen = point.distance(nextPoint);
        float span = Math.min(inLen, outLen);
        if (span < 1e-4f) span = Math.max(inLen, outLen);
        if (span < 1e-4f) return 0.0f;
        Vector3f in = new Vector3f(point).sub(prevPoint);
        Vector3f out = new Vector3f(nextPoint).sub(point);
        float turn = 0.0f;
        if (in.lengthSquared() >= 1e-4f && out.lengthSquared() >= 1e-4f) {
            turn = (1.0f - Math.max(-1.0f, Math.min(1.0f, in.normalize().dot(out.normalize())))) * 0.5f;
        }
        float handle = span * (0.28f + 0.55f * turn);
        return Math.min(span * 0.85f, Math.max(1.2f, handle));
    }

    private static Vector3f cubic(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float t) {
        float inv = 1.0f - t;
        float inv2 = inv * inv;
        float t2 = t * t;
        return new Vector3f(
            inv2 * inv * a.x + 3.0f * inv2 * t * b.x + 3.0f * inv * t2 * c.x + t2 * t * d.x,
            inv2 * inv * a.y + 3.0f * inv2 * t * b.y + 3.0f * inv * t2 * c.y + t2 * t * d.y,
            inv2 * inv * a.z + 3.0f * inv2 * t * b.z + 3.0f * inv * t2 * c.z + t2 * t * d.z);
    }

    @Override
    public boolean shouldRenderOffScreen(RaceStartBlockEntity start) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
