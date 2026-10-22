package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.restor.fpv.block.RaceGateBlock;
import org.restor.fpv.client.ClientRaceState;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;

import java.util.ArrayList;
import java.util.List;

// Draws each gate as a solid banded hoop oriented by its yaw/pitch, with its route order
// number at the centre and a tube leading to the next marker.
public class RaceGateRenderer implements BlockEntityRenderer<RaceGateBlockEntity, RaceGateRenderer.State> {
    public static class State extends BlockEntityRenderState {
        RaceGateBlockEntity be;
    }

    private final Font font;

    public RaceGateRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RaceGateBlockEntity be, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumbling);
        state.be = be;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        SubmitBuffers buffer = new SubmitBuffers(collector);
        render(state.be, pose, buffer, camera);
        buffer.flush();
    }

    private void render(RaceGateBlockEntity gate, PoseStack pose, SubmitBuffers buffer, CameraRenderState camera) {
        if (!ClientRaceState.active()) return;

        int c = gate.getColor();
        float a = ((c >> 24) & 0xFF) / 255f; if (a <= 0f) a = 1f;

        float half = gate.getSize() / 2f;
        Vec3 worldCenter = gate.center();
        Vector3f center = local(gate.getBlockPos(), worldCenter);
        Vector3f n = gate.normal().normalize();

        VertexConsumer vc = buffer.getBuffer(RaceFrames.type());
        Matrix4f mat = pose.last().pose();
        int idx = gate.getRouteIndex();
        int hit = ClientRaceState.gatesHit();
        boolean next = idx > 0 && idx == hit + 1;

        if (idx <= 0 || idx > hit) {
            float[] main = next ? RaceFrames.NEXT : RaceFrames.shade(c, 1f);
            float[] alt = next ? RaceFrames.WHITE : RaceFrames.shade(c, 0.32f);
            RaceFrames.hoop(vc, mat, center, n, half, RaceGateBlock.FRAME_RADIUS, main, alt, a);
        }

        boolean activeSegment = idx > 0 && idx == hit;
        float[] lineColor = activeSegment ? RaceFrames.NEXT : RaceFrames.shade(c, 0.85f);
        renderRouteLine(gate, center, vc, mat, lineColor, a);

        if (idx > 0) renderLabel(String.valueOf(idx), center, pose, buffer, camera, 0.05f, 0xFFFFFFFF);
        if (next) {
            Vector3f[] axes = RaceFrames.basis(n);
            Vector3f label = new Vector3f(center).add(new Vector3f(axes[1]).mul(half + 0.9f));
            renderLabel("GATE", label, pose, buffer, camera, 0.09f, 0xFF40FF60);
        }
    }

    private static Vector3f local(BlockPos origin, Vec3 world) {
        return new Vector3f((float) (world.x - origin.getX()), (float) (world.y - origin.getY()),
            (float) (world.z - origin.getZ()));
    }

    private static void renderRouteLine(RaceGateBlockEntity gate, Vector3f from, VertexConsumer vc, Matrix4f mat,
                                        float[] color, float alpha) {
        BlockPos next = gate.getNextRoutePos();
        if (next == null) return;
        if (gate.getRouteIndex() > 0 && gate.getRouteIndex() < ClientRaceState.gatesHit()) return;
        Vec3 source = gate.center();
        BlockPos origin = gate.getBlockPos();
        Vec3 prevTarget = routeCenter(gate, gate.getPrevRoutePos(), source);
        Vec3 target = routeCenter(gate, next, source);
        Vec3 nextTarget = routeCenter(gate, nextNextPos(gate, next), target);
        Vector3f prevPoint = local(origin, prevTarget);
        Vector3f to = local(origin, target);
        Vector3f nextPoint = local(origin, nextTarget);

        float handleOut = curveHandle(from, prevPoint, to);
        float handleIn = curveHandle(to, from, nextPoint);
        Vector3f p1 = new Vector3f(from).add(tangent(from, prevPoint, to, handleOut));
        Vector3f p2 = new Vector3f(to).sub(tangent(to, from, nextPoint, handleIn));

        List<Vector3f> points = new ArrayList<>();
        points.add(new Vector3f(from));
        for (int i = 1; i <= 24; i++) points.add(cubic(from, p1, p2, to, i / 24.0f));
        RaceFrames.ribbon(vc, mat, points, 0.11f, color, alpha, true);
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

    private void renderLabel(String s, Vector3f at, PoseStack pose, SubmitBuffers buffer,
                             CameraRenderState camera, float scale, int color) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.orientation);
        pose.scale(-scale, -scale, scale);
        buffer.collector().submitText(pose, -font.width(s) / 2f, -4f, net.minecraft.network.chat.Component.literal(s).getVisualOrderText(),
            false, Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, 0, 0);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RaceGateBlockEntity be) {
        return be.getRenderBoundingBox();
    }
}
