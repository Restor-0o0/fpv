package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.PlaneEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class PlaneRenderer extends EntityRenderer<PlaneEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Fpv.MODID, "textures/entity/plane.png");

    public PlaneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PlaneEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != null
            && (mc.getCameraEntity() == entity || mc.getCameraEntity().getVehicle() == entity)
            && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            return;
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() / 2, 0);

        Quaternionf q = new Quaternionf(modelQuat(entity.yRotO, entity.xRotO, entity.getPrevRoll()))
            .slerp(modelQuat(entity.getYRot(), entity.getXRot(), entity.getRoll()), partialTick);
        poseStack.mulPose(q);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        renderAirframe(consumer, poseStack, packedLight, entity.getVisualThrottle(),
            entity.tickCount + partialTick);

        poseStack.popPose();
    }

    private static Quaternionf modelQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(yaw - 180f))
            .rotateX((float) Math.toRadians(-pitch))
            .rotateZ((float) Math.toRadians(roll));
    }

    private void renderAirframe(VertexConsumer consumer, PoseStack poseStack, int packedLight,
                                float throttle, float tick) {
        Matrix4f mat = poseStack.last().pose();

        // Fuselage
        renderBox(consumer, mat, packedLight, -0.12f, -0.1f, -0.6f, 0.12f, 0.1f, 0.5f, 0.75f, 0.75f, 0.78f);
        // Nose cone
        renderBox(consumer, mat, packedLight, -0.08f, -0.07f, -0.75f, 0.08f, 0.07f, -0.6f, 0.35f, 0.35f, 0.4f);
        // (No canopy — these are unmanned FPV aircraft.)
        // Main wings
        renderBox(consumer, mat, packedLight, -0.85f, -0.03f, -0.05f, 0.85f, 0.03f, 0.2f, 0.65f, 0.65f, 0.7f);
        // Horizontal stabilizer
        renderBox(consumer, mat, packedLight, -0.35f, -0.02f, 0.42f, 0.35f, 0.02f, 0.55f, 0.6f, 0.6f, 0.65f);
        // Vertical fin
        renderBox(consumer, mat, packedLight, -0.02f, 0.02f, 0.4f, 0.02f, 0.32f, 0.55f, 0.55f, 0.55f, 0.6f);

        // Propeller
        poseStack.pushPose();
        poseStack.translate(0, 0, -0.75f);
        float spin = throttle * 45;
        if (spin > 1) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tick * spin));
            float pr = 0.28f, pw = 0.02f;
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -pr, -pw, -0.01f, pr, pw, 0.01f, 0.2f, 0.2f, 0.2f);
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -pw, -pr, -0.01f, pw, pr, 0.01f, 0.2f, 0.2f, 0.2f);
        } else {
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -0.03f, -0.03f, -0.02f, 0.03f, 0.03f, 0.02f, 0.15f, 0.15f, 0.15f);
        }
        poseStack.popPose();
    }

    private void renderBox(VertexConsumer consumer, Matrix4f mat, int packedLight,
                           float x1, float y1, float z1, float x2, float y2, float z2,
                           float r, float g, float b) {
        float u1 = 0, v1 = 0, u2 = 1, v2 = 1;

        consumer.vertex(mat, x1, y2, z1).color(r, g, b, 1).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(mat, x2, y2, z1).color(r, g, b, 1).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(mat, x2, y2, z2).color(r, g, b, 1).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();
        consumer.vertex(mat, x1, y2, z2).color(r, g, b, 1).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();

        consumer.vertex(mat, x1, y1, z1).color(r, g, b, 1).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();
        consumer.vertex(mat, x1, y1, z2).color(r, g, b, 1).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();
        consumer.vertex(mat, x2, y1, z2).color(r, g, b, 1).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();
        consumer.vertex(mat, x2, y1, z1).color(r, g, b, 1).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();

        consumer.vertex(mat, x1, y1, z1).color(r, g, b, 1).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();
        consumer.vertex(mat, x2, y1, z1).color(r, g, b, 1).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();
        consumer.vertex(mat, x2, y2, z1).color(r, g, b, 1).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();
        consumer.vertex(mat, x1, y2, z1).color(r, g, b, 1).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();

        consumer.vertex(mat, x1, y1, z2).color(r, g, b, 1).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        consumer.vertex(mat, x1, y2, z2).color(r, g, b, 1).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        consumer.vertex(mat, x2, y2, z2).color(r, g, b, 1).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        consumer.vertex(mat, x2, y1, z2).color(r, g, b, 1).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();

        consumer.vertex(mat, x2, y1, z1).color(r, g, b, 1).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();
        consumer.vertex(mat, x2, y1, z2).color(r, g, b, 1).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();
        consumer.vertex(mat, x2, y2, z2).color(r, g, b, 1).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();
        consumer.vertex(mat, x2, y2, z1).color(r, g, b, 1).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();

        consumer.vertex(mat, x1, y1, z1).color(r, g, b, 1).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
        consumer.vertex(mat, x1, y2, z1).color(r, g, b, 1).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
        consumer.vertex(mat, x1, y2, z2).color(r, g, b, 1).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
        consumer.vertex(mat, x1, y1, z2).color(r, g, b, 1).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(PlaneEntity entity) {
        return TEXTURE;
    }
}
