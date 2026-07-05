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

// Draws each gate as a glowing coloured square frame oriented by its yaw/pitch, plus its
// route order number floating at the centre.
public class RaceGateRenderer implements BlockEntityRenderer<RaceGateBlockEntity> {
    private final Font font;

    public RaceGateRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(RaceGateBlockEntity gate, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
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
        for (int i = 0; i < 4; i++) line(vc, mat, nrm, corners[i], corners[(i + 1) % 4], r, g, b, a);
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
        vc.vertex(mat, p1.x, p1.y, p1.z).color(r, g, b, a).normal(nrm, nx, ny, nz).endVertex();
        vc.vertex(mat, p2.x, p2.y, p2.z).color(r, g, b, a).normal(nrm, nx, ny, nz).endVertex();
    }

    private static void renderRouteLine(RaceGateBlockEntity gate, Vector3f from, VertexConsumer vc, Matrix4f mat,
                                        Matrix3f nrm, float r, float g, float b, float a) {
        BlockPos next = gate.getNextRoutePos();
        if (next == null) return;
        Vec3 target = targetCenter(gate, next);
        Vector3f to = new Vector3f(
            (float) (target.x - gate.getBlockPos().getX()),
            (float) (target.y - gate.getBlockPos().getY()),
            (float) (target.z - gate.getBlockPos().getZ()));
        float lift = Math.min(4.0f, Math.max(1.0f, from.distance(to) * 0.12f));
        Vector3f control = new Vector3f(from).add(to).mul(0.5f).add(0.0f, lift, 0.0f);
        Vector3f prev = new Vector3f(from);
        for (int i = 1; i <= 18; i++) {
            float t = i / 18.0f;
            Vector3f point = quadratic(from, control, to, t);
            line(vc, mat, nrm, prev, point, r, g, b, a);
            prev = point;
        }
    }

    private static Vec3 targetCenter(RaceGateBlockEntity gate, BlockPos pos) {
        Level level = gate.getLevel();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RaceGateBlockEntity nextGate) return nextGate.center();
        }
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static Vector3f quadratic(Vector3f a, Vector3f b, Vector3f c, float t) {
        float inv = 1.0f - t;
        return new Vector3f(
            inv * inv * a.x + 2.0f * inv * t * b.x + t * t * c.x,
            inv * inv * a.y + 2.0f * inv * t * b.y + t * t * c.y,
            inv * inv * a.z + 2.0f * inv * t * b.z + t * t * c.z);
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
