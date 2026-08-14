package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

// Draws each gate as a glowing coloured square frame oriented by its yaw/pitch, plus its
// route order number floating at the centre.
public class RaceGateRenderer implements BlockEntityRenderer<RaceGateBlockEntity> {
    private final Font font;

    public RaceGateRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RaceGateBlockEntity gate) {
        return gate.renderBoundingBox();
    }

    @Override
    public void render(RaceGateBlockEntity gate, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!FPVOverlay.raceActive()) return;

        int c = gate.getColor();
        float a = ((c >> 24) & 0xFF) / 255f; if (a <= 0f) a = 1f;
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;

        float half = gate.getSize() / 2f;
        Vec3 worldCenter = gate.center();
        Vector3f center = new Vector3f(
            (float) (worldCenter.x - gate.getBlockPos().getX()),
            (float) (worldCenter.y - gate.getBlockPos().getY()),
            (float) (worldCenter.z - gate.getBlockPos().getZ()));

        // In-plane basis from the frame normal.
        Vector3f n = gate.normal().normalize();
        Vector3f ref = Math.abs(n.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(ref).cross(n).normalize();
        Vector3f up = new Vector3f(n).cross(right).normalize();

        Vector3f[] corners = {
            corner(center, right, up, -half, -half),
            corner(center, right, up,  half, -half),
            corner(center, right, up,  half,  half),
            corner(center, right, up, -half,  half),
        };

        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = pose.last().pose();
        Matrix3f nrm = pose.last().normal();
        if (gate.getRouteIndex() <= 0 || (FPVOverlay.raceActive() && gate.getRouteIndex() > FPVOverlay.raceGatesHit())) {
            for (int i = 0; i < 4; i++) thickLine(vc, mat, nrm, corners[i], corners[(i + 1) % 4], right, up, n, r, g, b, a);
        }
        renderRouteLine(gate, center, vc, mat, nrm, r, g, b, a * 0.7f);

        int idx = gate.getRouteIndex();
        if (idx > 0) renderNumber(String.valueOf(idx), center, pose, buffer);
    }

    private static Vector3f corner(Vector3f c, Vector3f right, Vector3f up, float u, float v) {
        return new Vector3f(
            c.x + right.x * u + up.x * v,
            c.y + right.y * u + up.y * v,
            c.z + right.z * u + up.z * v);
    }

    private static void line(VertexConsumer vc, Matrix4f mat, Matrix3f nrm,
                             Vector3f p1, Vector3f p2, float r, float g, float b, float a) {
        float dx = p2.x - p1.x, dy = p2.y - p1.y, dz = p2.z - p1.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) len = 1f;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        Vector3f n = nrm.transform(new Vector3f(nx, ny, nz));
        vc.addVertex(mat, p1.x, p1.y, p1.z).setColor(r, g, b, a).setNormal(n.x, n.y, n.z);
        vc.addVertex(mat, p2.x, p2.y, p2.z).setColor(r, g, b, a).setNormal(n.x, n.y, n.z);
    }

    private static void thickLine(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, Vector3f p1, Vector3f p2,
                                  Vector3f right, Vector3f up, Vector3f normal, float r, float g, float b, float a) {
        float s = 0.11f;
        line(vc, mat, nrm, p1, p2, r, g, b, a);
        offsetLine(vc, mat, nrm, p1, p2, right, s, r, g, b, a);
        offsetLine(vc, mat, nrm, p1, p2, right, -s, r, g, b, a);
        offsetLine(vc, mat, nrm, p1, p2, up, s, r, g, b, a);
        offsetLine(vc, mat, nrm, p1, p2, up, -s, r, g, b, a);
        offsetLine(vc, mat, nrm, p1, p2, normal, s, r, g, b, a);
        offsetLine(vc, mat, nrm, p1, p2, normal, -s, r, g, b, a);
    }

    private static void offsetLine(VertexConsumer vc, Matrix4f mat, Matrix3f nrm, Vector3f p1, Vector3f p2,
                                   Vector3f axis, float amount, float r, float g, float b, float a) {
        Vector3f o = new Vector3f(axis).normalize().mul(amount);
        line(vc, mat, nrm, new Vector3f(p1).add(o), new Vector3f(p2).add(o), r, g, b, a);
    }

    private static void renderRouteLine(RaceGateBlockEntity gate, Vector3f from, VertexConsumer vc, Matrix4f mat,
                                        Matrix3f nrm, float r, float g, float b, float a) {
        BlockPos next = gate.getNextRoutePos();
        if (next == null) return;
        if (gate.getRouteIndex() > 0 && gate.getRouteIndex() < FPVOverlay.raceGatesHit()) return;
        Vec3 source = gate.center();
        Vec3 prevTarget = routeCenter(gate, gate.getPrevRoutePos(), source);
        Vec3 target = routeCenter(gate, next, source);
        Vec3 nextTarget = routeCenter(gate, nextNextPos(gate, next), target);
        Vector3f prevPoint = new Vector3f(
            (float) (prevTarget.x - gate.getBlockPos().getX()),
            (float) (prevTarget.y - gate.getBlockPos().getY()),
            (float) (prevTarget.z - gate.getBlockPos().getZ()));
        Vector3f to = new Vector3f(
            (float) (target.x - gate.getBlockPos().getX()),
            (float) (target.y - gate.getBlockPos().getY()),
            (float) (target.z - gate.getBlockPos().getZ()));
        Vector3f nextPoint = new Vector3f(
            (float) (nextTarget.x - gate.getBlockPos().getX()),
            (float) (nextTarget.y - gate.getBlockPos().getY()),
            (float) (nextTarget.z - gate.getBlockPos().getZ()));

        float handleOut = curveHandle(from, prevPoint, to);
        float handleIn = curveHandle(to, from, nextPoint);
        Vector3f p1 = new Vector3f(from).add(tangent(from, prevPoint, to, handleOut));
        Vector3f p2 = new Vector3f(to).sub(tangent(to, from, nextPoint, handleIn));

        Vector3f prev = new Vector3f(from);
        for (int i = 1; i <= 24; i++) {
            float t = i / 24.0f;
            Vector3f point = cubic(from, p1, p2, to, t);
            line(vc, mat, nrm, prev, point, r, g, b, a);
            prev = point;
        }
    }

    private static Vec3 routeCenter(RaceGateBlockEntity gate, BlockPos pos, Vec3 fallback) {
        if (pos == null) return fallback;
        Level level = gate.getLevel();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RaceGateBlockEntity nextGate) return nextGate.center();
            if (be instanceof RaceStartBlockEntity start) return start.frameCenter();
        }
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static BlockPos nextNextPos(RaceGateBlockEntity gate, BlockPos pos) {
        Level level = gate.getLevel();
        if (level != null && pos != null && level.getBlockEntity(pos) instanceof RaceGateBlockEntity nextGate) {
            return nextGate.getNextRoutePos();
        }
        return null;
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

    private void renderNumber(String s, Vector3f center, PoseStack pose, MultiBufferSource buffer) {
        pose.pushPose();
        pose.translate(center.x, center.y, center.z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.05f, -0.05f, 0.05f);
        Matrix4f m = pose.last().pose();
        font.drawInBatch(s, -font.width(s) / 2f, -4f, 0xFFFFFFFF, false, m, buffer,
            Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RaceGateBlockEntity gate) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
