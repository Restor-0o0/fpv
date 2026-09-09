package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.DroneVisual;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.ModularPlaneEntity;
import org.restor.fpv.assembly.MountPoint;
import org.restor.fpv.entity.PlaneEntity;

/**
 * The airframe model comes from the frame item; everything else is composed on top from
 * the part item models, the same way the bench-built quads are drawn. A wing prop spins
 * around the thrust axis, not the vertical one.
 */
public class ModularPlaneRenderer extends PlaneRenderer {
    public ModularPlaneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void renderExtras(PlaneEntity entity, float partialTick, PoseStack poseStack,
                                MultiBufferSource buffer, int packedLight) {
        if (!(entity instanceof ModularPlaneEntity plane)) return;
        DroneAssembly a = plane.getAssembly();
        FrameSpec spec = a.frameSpec();
        if (spec == null) return;
        DroneVisual v = DroneVisual.of(spec);

        // Pack, payload and the flight stack all ride inside the fuselage on a real wing,
        // so only the camera pod and the power train are drawn.
        renderPart(a.camera, poseStack, buffer, packedLight,
            spec.cameraMount(), 0f, v.cameraScale(), false);

        float spin = (entity.tickCount + partialTick) * entity.getVisualThrottle() * 40f;
        for (int i = 0; i < spec.motorCount(); i++) {
            MountPoint m = spec.motorMounts().get(i);
            // Wing motors and props are modelled nose-forward; behind the centre of mass they
            // are the same parts turned around, which is a pusher installation.
            boolean pusher = m.z() > 0f;
            renderPart(a.motors[i], poseStack, buffer, packedLight, m, 0f, v.motorScale(), pusher);
            renderPart(a.props[i], poseStack, buffer, packedLight, m, spin, v.propScale(), pusher);
        }
    }

    private void renderPart(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer,
                            int light, MountPoint at, float spinDeg, float scale, boolean flip) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(at.x(), at.y(), at.z());
        poseStack.scale(scale, scale, scale);
        if (flip) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        if (spinDeg != 0) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(spinDeg));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
            light, OverlayTexture.NO_OVERLAY, poseStack, buffer, null, 0);
        poseStack.popPose();
    }
}
