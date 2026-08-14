package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

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
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        float a = ((c >> 24) & 0xFF) / 255f;
        if (a <= 0f) a = 1f;

        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = pose.last().pose();
        Matrix3f nrm = pose.last().normal();
        float half = start.getSize() / 2.0f;
        Vec3 worldCenter = start.frameCenter();
        Vector3f center = new Vector3f(
            (float) (worldCenter.x - start.getBlockPos().getX()),
            (float) (worldCenter.y - start.getBlockPos().getY()),
            (float) (worldCenter.z - start.getBlockPos().getZ()));
        Vector3f n = start.normal().normalize();
        Vector3f ref = Math.abs(n.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(ref).cross(n).normalize();
        Vector3f up = new Vector3f(n).cross(right).normalize();
        Vector3f[] corners = {
            corner(center, right, up, -half, -half),
            corner(center, right, up,  half, -half),
            corner(center, right, up,  half,  half),
            corner(center, right, up, -half,  half),
        };
        for (int i = 0; i < 4; i++) line(vc, mat, nrm, corners[i], corners[(i + 1) % 4], r, g, b, a);
        renderStartText(pose, buffer, center, n, up);
        vc = buffer.getBuffer(RenderType.lines());
        if (FPVOverlay.raceActive()) renderFirstSegment(start, vc, mat, nrm, r, g, b, a * 0.7f);
    }

    private void renderStartText(PoseStack pose, MultiBufferSource buffer, Vector3f center, Vector3f normal, Vector3f up) {
        Vector3f p = new Vector3f(center).add(new Vector3f(up).mul(1.25f)).add(new Vector3f(normal).mul(0.04f));
        pose.pushPose();
        pose.translate(p.x, p.y, p.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f(normal).normalize()));
        pose.scale(-0.06f, -0.06f, 0.06f);
        Matrix4f m = pose.last().pose();
        String s = "START";
        font.drawInBatch(s, -font.width(s) / 2f, -4f, 0xFF40FF40, false, m, buffer,
            Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        pose.popPose();
    }

    private static Vector3f corner(Vector3f c, Vector3f right, Vector3f up, float u, float v) {
        return new Vector3f(c.x + right.x * u + up.x * v, c.y + right.y * u + up.y * v, c.z + right.z * u + up.z * v);
    }

    private static void renderFirstSegment(RaceStartBlockEntity start, VertexConsumer vc, Matrix4f mat,
                                           Matrix3f nrm, float r, float g, float b, float a) {
        if (FPVOverlay.raceGatesHit() > 0) return;
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

        Vec3 source = start.frameCenter();
        Vector3f from = new Vector3f(
            (float) (source.x - start.getBlockPos().getX()),
            (float) (source.y - start.getBlockPos().getY()),
            (float) (source.z - start.getBlockPos().getZ()));
        Vector3f to = new Vector3f(
            (float) (target.x - start.getBlockPos().getX()),
            (float) (target.y - start.getBlockPos().getY()),
            (float) (target.z - start.getBlockPos().getZ()));
        Vector3f nextPoint = new Vector3f(
            (float) (nextTarget.x - start.getBlockPos().getX()),
            (float) (nextTarget.y - start.getBlockPos().getY()),
            (float) (nextTarget.z - start.getBlockPos().getZ()));
        Vector3f dir = new Vector3f(to).sub(from);
        if (dir.lengthSquared() < 1e-4f) return;
        float handleOut = curveHandle(from, from, to);
        float handleIn = curveHandle(to, from, nextPoint);
        Vector3f p1 = new Vector3f(from).add(dir.normalize().mul(handleOut));
        Vector3f p2 = new Vector3f(to).sub(tangent(to, from, nextPoint, handleIn));

        Vector3f prev = new Vector3f(from);
        for (int i = 1; i <= 24; i++) {
            float t = i / 24.0f;
            Vector3f point = cubic(from, p1, p2, to, t);
            line(vc, mat, nrm, prev, point, r, g, b, a);
            prev = point;
        }
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
}
