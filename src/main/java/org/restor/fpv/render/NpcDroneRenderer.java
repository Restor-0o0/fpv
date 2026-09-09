package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.Fpv;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.DroneVisual;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.MountPoint;
import org.restor.fpv.entity.NpcDroneEntity;

/** NPC drones are real builds, so they render from the same part models as a player drone. */
public class NpcDroneRenderer extends EntityRenderer<NpcDroneEntity> {
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Fpv.MODID, "textures/entity/drone.png");

    public NpcDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(NpcDroneEntity e, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        super.render(e, entityYaw, partialTick, pose, buffer, packedLight);

        DroneAssembly assembly = e.getAssembly();
        FrameSpec spec = assembly.frameSpec();
        DroneVisual v = DroneVisual.of(spec);

        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        // Bank into the turn a little so the AI flight doesn't read as a sliding brick.
        float bank = Mth.clamp((float) e.getDeltaMovement().horizontalDistance() * 55f, 0f, 18f);
        pose.mulPose(Axis.XP.rotationDegrees(-bank));
        pose.scale(v.scale(), v.scale(), v.scale());

        part(assembly.frame, pose, buffer, packedLight, 0, 0, 0, 0, v.frameScale());
        if (spec != null) {
            part(assembly.battery, pose, buffer, packedLight,
                spec.batteryMount().x(), spec.batteryMount().y(), spec.batteryMount().z(), 0, v.batteryScale());
            part(assembly.camera, pose, buffer, packedLight,
                spec.cameraMount().x(), spec.cameraMount().y(), spec.cameraMount().z(), 0, v.cameraScale());
            part(assembly.module, pose, buffer, packedLight,
                spec.moduleMount().x(), spec.moduleMount().y(), spec.moduleMount().z(), 0, v.moduleScale());

            float spin = (e.tickCount + partialTick) * 45f;
            for (int i = 0; i < spec.motorCount(); i++) {
                MountPoint m = spec.motorMounts().get(i);
                float x = m.x() * v.armSpread();
                float z = m.z() * v.armSpread();
                part(assembly.motors[i], pose, buffer, packedLight, x, m.y(), z, 0, v.motorScale());
                part(assembly.props[i], pose, buffer, packedLight,
                    x, m.y() + v.propLift(), z, i % 2 == 0 ? spin : -spin, v.propScale());
            }
        }
        pose.popPose();
    }

    private void part(ItemStack stack, PoseStack pose, MultiBufferSource buffer,
                      int light, float x, float y, float z, float spinDeg, float scale) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        if (spinDeg != 0) pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
            light, OverlayTexture.NO_OVERLAY, pose, buffer, null, 0);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(NpcDroneEntity entity) {
        return TEXTURE;
    }
}
