package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.DroneVisual;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.ModularDroneEntity;
import org.restor.fpv.Config;
import org.restor.fpv.assembly.MountPoint;
import org.restor.fpv.entity.DroneEntity;

/**
 * Composes the drone visual from the PART ITEM MODELS: the frame model is rendered
 * at the entity center and every installed component's model at its FrameSpec mount
 * point. Give a part a 3D Blockbench model (assets/fpv/models/item/<part>.json) and
 * it is rendered assembled — no code changes needed.
 */
public class ModularDroneRenderer extends EntityRenderer<ModularDroneEntity, ModularDroneRenderer.State> {
    public static class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        ModularDroneEntity entity;
    }

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("fpv", "textures/entity/drone.png");

    public ModularDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ModularDroneEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
    }

    @Override
    public void submit(State state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        SubmitBuffers buffer = new SubmitBuffers(collector);
        render(state.entity, state.partialTick, poseStack, buffer, state.lightCoords);
        buffer.flush();
        super.submit(state, poseStack, collector, camera);
    }

    private void render(ModularDroneEntity entity, float partialTick, PoseStack poseStack,
                        SubmitBuffers buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        boolean fpvSelf = mc.getCameraEntity() != null
            && (mc.getCameraEntity() == entity || mc.getCameraEntity().getVehicle() == entity)
            && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;

        DroneAssembly assembly = entity.getAssembly();
        FrameSpec spec = assembly.frameSpec();

        // FPV self-view only keeps the front motors and props visible; rendering the
        // assembled frame here puts the camera inside its own model.
        if (fpvSelf) {
            if (Config.FPV_PROPS_IN_VIEW.get()) {
                poseStack.pushPose();
                DroneRenderer.applyFpvViewmodelPose(poseStack, entity, partialTick);
                float s = DroneVisual.of(spec).scale() * Config.FPV_PROPS_SCALE.get().floatValue();
                poseStack.scale(s, s, s);
                renderAssembly(entity, assembly, spec, poseStack, buffer, packedLight, partialTick, true);
                poseStack.popPose();
            }
            return;
        }


        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() / 2, 0);
        Quaternionf q = new Quaternionf(modelQuat(entity.yRotO, entity.xRotO, entity.getPrevRoll()))
            .slerp(modelQuat(entity.getYRot(), entity.getXRot(), entity.getRoll()), partialTick);
        poseStack.mulPose(q);
        if (spec != null && spec.isTailsitter()) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        }
        float s = DroneVisual.of(spec).scale();
        poseStack.scale(s, s, s);
        renderAssembly(entity, assembly, spec, poseStack, buffer, packedLight, partialTick, false);
        poseStack.popPose();
    }

    /** Draws the assembled drone, optionally limiting FPV self-view to its front props. */
    private void renderAssembly(ModularDroneEntity entity, DroneAssembly assembly, FrameSpec spec,
                                 PoseStack poseStack, SubmitBuffers buffer, int packedLight, float partialTick,
                                 boolean propsOnly) {
        DroneVisual v = DroneVisual.of(spec);
        if (!propsOnly) FrameModels.render(assembly.frame, poseStack, buffer, packedLight, v.frameScale());
        if (spec != null) {
            if (!propsOnly) {
                renderPart(assembly.battery, poseStack, buffer, packedLight,
                    spec.batteryMount().x(), spec.batteryMount().y(), spec.batteryMount().z(), 0, v.batteryScale());
                renderCameraPart(assembly.camera, poseStack, buffer, packedLight,
                    spec.cameraMount().x(), spec.cameraMount().y(), spec.cameraMount().z(), v.cameraScale());
                renderPart(assembly.module, poseStack, buffer, packedLight,
                    spec.moduleMount().x(), spec.moduleMount().y(), spec.moduleMount().z(), 0, v.moduleScale());
                renderPart(assembly.firmware, poseStack, buffer, packedLight,
                    spec.moduleMount().x(), spec.moduleMount().y() + 0.06f, spec.moduleMount().z() - 0.09f, 0,
                    v.electronicsScale());
            }
            float spin = (entity.tickCount + partialTick) * entity.getVisualThrottle() * 40f;
            boolean axial = spec.role() == org.restor.fpv.assembly.FrameRole.INTERCEPTOR;
            for (int i = 0; i < spec.motorCount(); i++) {
                MountPoint m = spec.motorMounts().get(i);
                float x = m.x() * v.armSpread();
                float z = m.z() * v.armSpread();
                if (propsOnly && z > 0) continue;
                float propSpin = i % 2 == 0 ? spin : -spin;
                if (axial) {
                    renderRotorPart(assembly.motors[i], poseStack, buffer, packedLight, x, m.y(), z, 0,
                        v.motorScale());
                    renderRotorPart(assembly.props[i], poseStack, buffer, packedLight,
                        x, m.y(), z + v.propLift(), propSpin, v.propScale());
                    continue;
                }
                renderPart(assembly.motors[i], poseStack, buffer, packedLight, x, m.y(), z, 0, v.motorScale());
                // Props sit slightly above the motor and spin with throttle,
                // alternating direction per slot (props-out layout).
                renderPart(assembly.props[i], poseStack, buffer, packedLight,
                    x, m.y() + v.propLift(), z, propSpin, v.propScale());
            }
        }
    }

    private void renderPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                            int light, float x, float y, float z, float spinDeg, float scale) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scale, scale, scale);
        if (spinDeg != 0) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spinDeg));
        }
        buffer.item(stack, poseStack, light);
        poseStack.popPose();
    }

    /** Lays a rotor on its side so its thrust axis runs along the fuselage. */
    private void renderRotorPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                                 int light, float x, float y, float z, float spinDeg, float scale) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        poseStack.scale(scale, scale, scale);
        if (spinDeg != 0) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spinDeg));
        buffer.item(stack, poseStack, light);
        poseStack.popPose();
    }

    private void renderCameraPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                                  int light, float x, float y, float z, float scale) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(22.5f));
        poseStack.scale(scale, scale, scale);
        buffer.item(stack, poseStack, light);
        poseStack.popPose();
    }

    // Same composition as DroneRenderer: Ry(yaw−180)·Rx(−pitch)·Rz(roll).
    private static Quaternionf modelQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(180f - yaw))
            .rotateX((float) Math.toRadians(-pitch))
            .rotateZ((float) Math.toRadians(-roll));
    }

}
