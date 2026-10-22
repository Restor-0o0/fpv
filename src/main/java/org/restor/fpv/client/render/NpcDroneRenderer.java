package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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
public class NpcDroneRenderer extends EntityRenderer<NpcDroneEntity, NpcDroneRenderer.State> {
    public static class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        NpcDroneEntity entity;
    }

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(Fpv.MODID, "textures/entity/drone.png");

    public NpcDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(NpcDroneEntity entity, State state, float partialTick) {
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

    private void render(NpcDroneEntity e, float partialTick, PoseStack pose,
                        SubmitBuffers buffer, int packedLight) {

        DroneAssembly assembly = e.getAssembly();
        FrameSpec spec = assembly.frameSpec();
        DroneVisual v = DroneVisual.of(spec);

        pose.pushPose();
        pose.translate(0, e.getBbHeight() * 0.5, 0);
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        pose.rotate(Axis.YP.rotationDegrees(180f - yaw));
        // Bank into the turn a little so the AI flight doesn't read as a sliding brick.
        float bank = Mth.clamp((float) e.getDeltaMovement().horizontalDistance() * 55f, 0f, 18f);
        pose.rotate(Axis.XP.rotationDegrees(-bank));
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

    private void part(ItemStack stack, PoseStack pose, SubmitBuffers buffer,
                      int light, float x, float y, float z, float spinDeg, float scale) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        if (spinDeg != 0) pose.rotate(Axis.YP.rotationDegrees(spinDeg));
        buffer.item(stack, pose, light);
        pose.popPose();
    }

}
