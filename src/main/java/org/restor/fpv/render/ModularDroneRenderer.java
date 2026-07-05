package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.ModularDroneEntity;
import org.restor.fpv.assembly.MountPoint;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.entity.DroneEntity;

/**
 * Composes the drone visual from the PART ITEM MODELS: the frame model is rendered
 * at the entity center and every installed component's model at its FrameSpec mount
 * point. Give a part a 3D Blockbench model (assets/fpv/models/item/<part>.json) and
 * it is rendered assembled — no code changes needed.
 */
public class ModularDroneRenderer extends EntityRenderer<ModularDroneEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("fpv", "textures/entity/drone.png");

    public ModularDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ModularDroneEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        boolean fpvSelf = mc.getCameraEntity() != null
            && (mc.getCameraEntity() == entity || mc.getCameraEntity().getVehicle() == entity)
            && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;

        DroneAssembly assembly = entity.getAssembly();
        FrameSpec spec = assembly.frameSpec();
        if (fpvSelf && !shouldShowPropsInFpv(assembly)) {
            return;
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() / 2, 0);
        Quaternionf q = new Quaternionf(modelQuat(entity.yRotO, entity.xRotO, entity.getPrevRoll()))
            .slerp(modelQuat(entity.getYRot(), entity.getXRot(), entity.getRoll()), partialTick);
        poseStack.mulPose(q);

        if (!fpvSelf) renderPart(assembly.frame, poseStack, buffer, packedLight, 0, 0, 0, 0);
        if (spec != null) {
            if (!fpvSelf) {
                renderPart(assembly.battery, poseStack, buffer, packedLight,
                    spec.batteryMount().x(), spec.batteryMount().y(), spec.batteryMount().z(), 0);
                renderPart(assembly.camera, poseStack, buffer, packedLight,
                    spec.cameraMount().x(), spec.cameraMount().y(), spec.cameraMount().z(), 0);
            }
            float spin = (entity.tickCount + partialTick) * entity.getVisualThrottle() * 40f;
            for (int i = 0; i < spec.motorCount(); i++) {
                MountPoint m = spec.motorMounts().get(i);
                renderPart(assembly.motors[i], poseStack, buffer, packedLight, m.x(), m.y(), m.z(), 0);
                // Props sit slightly above the motor and spin with throttle,
                // alternating direction per slot (props-out layout).
                renderPart(assembly.props[i], poseStack, buffer, packedLight,
                    m.x(), m.y() + 0.06f, m.z(), i % 2 == 0 ? spin : -spin);
            }
        }
        poseStack.popPose();
    }

    private static boolean shouldShowPropsInFpv(DroneAssembly assembly) {
        if (!(assembly.frame.getItem() instanceof ComponentItem ci)) return false;
        float size = ci.getStats().frameSize();
        return size >= 5.0f && size <= 8.5f;
    }

    private void renderPart(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                            int light, float x, float y, float z, float spinDeg) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        if (spinDeg != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spinDeg));
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
            light, OverlayTexture.NO_OVERLAY, poseStack, buffer, null, 0);
        poseStack.popPose();
    }

    // Same composition as DroneRenderer: Ry(yaw−180)·Rx(−pitch)·Rz(roll).
    private static Quaternionf modelQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(yaw - 180f))
            .rotateX((float) Math.toRadians(-pitch))
            .rotateZ((float) Math.toRadians(roll));
    }

    @Override
    public ResourceLocation getTextureLocation(ModularDroneEntity entity) {
        return TEXTURE;
    }
}
