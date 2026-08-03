package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.NpcDroneEntity;

// Box-composed hostile quad: dark gunmetal body, red LED "eye" at the nose, four arms with
// always-spinning props (it is always powered). Yaw-only orientation — the AI flies level.
public class HostileDroneRenderer extends EntityRenderer<NpcDroneEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Fpv.MODID, "textures/entity/drone.png");

    public HostileDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(NpcDroneEntity e, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        super.render(e, entityYaw, partialTick, pose, buffer, packedLight);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));

        Matrix4f mat = pose.last().pose();
        // Body + top plate
        box(vc, mat, packedLight, -0.16f, -0.06f, -0.14f, 0.16f, 0.06f, 0.16f, 0.13f, 0.13f, 0.15f);
        box(vc, mat, packedLight, -0.11f, 0.06f, -0.09f, 0.11f, 0.10f, 0.11f, 0.09f, 0.09f, 0.10f);
        // LED eye at the nose (front = -Z) — colour identifies the drone type:
        // kamikaze = red, patrol = amber, interceptor = blue (white-hot while hunting).
        float lr = 0.95f, lg = 0.10f, lb = 0.10f;
        if (e instanceof org.restor.fpv.entity.PatrolDroneEntity) {
            lr = 1.0f; lg = 0.62f; lb = 0.10f;
        } else if (e instanceof org.restor.fpv.entity.InterceptorDroneEntity ic) {
            if (ic.isHunting()) { lr = 0.85f; lg = 0.95f; lb = 1.0f; }
            else { lr = 0.15f; lg = 0.45f; lb = 1.0f; }
        }
        box(vc, mat, packedLight, -0.05f, -0.02f, -0.17f, 0.05f, 0.04f, -0.14f, lr, lg, lb);

        // Arms + motors + spinning props
        float al = 0.34f;
        float spin = (e.tickCount + partialTick) * 55f;
        float[][] corners = {{al, -al}, {-al, -al}, {al, al}, {-al, al}};
        for (int i = 0; i < 4; i++) {
            float cx = corners[i][0], cz = corners[i][1];
            box(vc, mat, packedLight, Math.min(0, cx), -0.025f, Math.min(0, cz),
                Math.max(0, cx), 0.025f, Math.max(0, cz), 0.20f, 0.20f, 0.22f);
            box(vc, mat, packedLight, cx - 0.05f, -0.02f, cz - 0.05f, cx + 0.05f, 0.07f, cz + 0.05f,
                0.25f, 0.25f, 0.27f);
            // Prop blur disc
            pose.pushPose();
            pose.translate(cx, 0.09f, cz);
            pose.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? spin : -spin));
            Matrix4f pm = pose.last().pose();
            box(vc, pm, packedLight, -0.17f, 0f, -0.015f, 0.17f, 0.012f, 0.015f, 0.55f, 0.15f, 0.15f);
            box(vc, pm, packedLight, -0.015f, 0f, -0.17f, 0.015f, 0.012f, 0.17f, 0.55f, 0.15f, 0.15f);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void box(VertexConsumer c, Matrix4f mat, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b) {
        quad(c, mat, light, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, 0, 1, 0);
        quad(c, mat, light, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1, r, g, b, 0, -1, 0);
        quad(c, mat, light, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, 0, 0, -1);
        quad(c, mat, light, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2, r, g, b, 0, 0, 1);
        quad(c, mat, light, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, 1, 0, 0);
        quad(c, mat, light, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2, r, g, b, -1, 0, 0);
    }

    private static void quad(VertexConsumer c, Matrix4f mat, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float nx, float ny, float nz) {
        c.vertex(mat, ax, ay, az).color(r, g, b, 1f).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
        c.vertex(mat, bx, by, bz).color(r, g, b, 1f).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
        c.vertex(mat, cx, cy, cz).color(r, g, b, 1f).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
        c.vertex(mat, dx, dy, dz).color(r, g, b, 1f).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nx, ny, nz).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(NpcDroneEntity entity) {
        return TEXTURE;
    }
}
