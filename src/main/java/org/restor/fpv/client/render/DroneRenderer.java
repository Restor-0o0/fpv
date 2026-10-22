package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.Config;
import org.restor.fpv.Fpv;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.FrameSpecs;
import org.restor.fpv.assembly.MountPoint;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.entity.DroneEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class DroneRenderer extends EntityRenderer<DroneEntity, DroneRenderer.State> {
    public static class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        DroneEntity entity;
    }

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Fpv.MODID, "textures/entity/drone.png");
    private static final double FAR_RENDER_DISTANCE_SQR = 1024.0 * 1024.0;

    public DroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(DroneEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ, float partialTicks) {
        if (entity instanceof org.restor.fpv.entity.KamikazeDroneEntity) {
            double dx = entity.getX() - camX;
            double dy = entity.getY() - camY;
            double dz = entity.getZ() - camZ;
            if (dx * dx + dy * dy + dz * dz < FAR_RENDER_DISTANCE_SQR) return true;
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ, partialTicks);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DroneEntity entity, State state, float partialTick) {
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

    private void render(DroneEntity entity, float partialTick, PoseStack poseStack,
                        SubmitBuffers buffer, int packedLight) {
        // FPV self-view only keeps the front motors and props visible. Drawing the frame,
        // camera and payload here encloses the viewpoint inside the drone model.
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean fpvSelf = mc.getCameraEntity() != null
            && (mc.getCameraEntity() == entity || mc.getCameraEntity().getVehicle() == entity)
            && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
        if (fpvSelf) {
            if (Config.FPV_PROPS_IN_VIEW.get()) {
                poseStack.pushPose();
                applyFpvViewmodelPose(poseStack, entity, partialTick);
                float s = getScale(entity) * Config.FPV_PROPS_SCALE.get().floatValue();
                poseStack.scale(s, s, s);
                renderAssembledDrone(entity, poseStack, buffer, packedLight, entity.tickCount + partialTick, true);
                poseStack.popPose();
            }
            return;
        }

        float scale = getScale(entity);

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() / 2, 0);

        // Interpolate orientation as one quaternion — per-axis Euler lerps jerk near
        // pitch ±90 where the decomposition flips yaw/roll by 180°.
        Quaternionf q = new Quaternionf(modelQuat(entity.yRotO, entity.xRotO, entity.getPrevRoll()))
            .slerp(modelQuat(entity.getYRot(), entity.getXRot(), entity.getRoll()), partialTick);
        poseStack.rotate(q);
        if (entity.isTailsitter()) poseStack.rotate(Axis.XP.rotationDegrees(90));

        poseStack.scale(scale, scale, scale);

        renderAssembledDrone(entity, poseStack, buffer, packedLight, entity.tickCount + partialTick, false);
        VertexConsumer consumer = buffer.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE));
        renderPayload(entity, consumer, poseStack, packedLight);

        poseStack.popPose();
    }

    /**
     * Locks the pose to the FPV camera so whatever is drawn after sits FIXED in the goggles
     * (screen space), independent of the drone's attitude, then parks it low + ahead so the
     * front props peek into the bottom of the view. Caller pushes/pops and draws props after.
     *
     * The camera view Minecraft builds from the FPV angles is Rz(roll)·Rx(pitch)·Ry(yaw+180)
     * (see GameRenderer). We recover those exact angles from the same quaternion FpvCamera
     * feeds the camera, rebuild that rotation and cancel it — landing in true screen space.
     */
    public static void applyFpvViewmodelPose(PoseStack ps, DroneEntity e, float partialTick) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double ex = Mth.lerp(partialTick, e.xOld, e.getX());
        double ey = Mth.lerp(partialTick, e.yOld, e.getY());
        double ez = Mth.lerp(partialTick, e.zOld, e.getZ());

        Quaternionf qCam = new Quaternionf(
                DroneEntity.attitudeQuat(e.yRotO, e.xRotO, e.getPrevRoll()))
            .slerp(DroneEntity.attitudeQuat(e.getYRot(), e.getXRot(), e.getRoll()), partialTick)
            .rotateX((float) Math.toRadians(-e.getCameraAngle()));
        float[] a = DroneEntity.decomposeAttitude(qCam);
        Quaternionf rView = new Quaternionf()
            .rotateZ((float) Math.toRadians(a[2]))
            .rotateX((float) Math.toRadians(a[1]))
            .rotateY((float) Math.toRadians(a[0] + 180f));

        ps.translate(camPos.x - ex, camPos.y - ey, camPos.z - ez); // undo world placement → pose = view
        ps.rotate(rView.conjugate());                             // undo view rotation → screen space
        ps.translate(0.0, Config.FPV_PROPS_DOWN.get(), -Config.FPV_PROPS_FORWARD.get());
        // Tilt the prop disc by the camera uptilt so the props lie as the real camera sees them.
        ps.rotate(Axis.XP.rotationDegrees(e.getCameraAngle()));
    }

    // Same composition the old Axis-based code produced: Ry(yaw−180)·Rx(−pitch)·Rz(roll).
    private static Quaternionf modelQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(180f - yaw))
            .rotateX((float) Math.toRadians(-pitch))
            .rotateZ((float) Math.toRadians(-roll));
    }

    private float getScale(DroneEntity entity) {
        return defaultBuild(entity).visual().scale();
    }

    private void renderAssembledDrone(DroneEntity entity, PoseStack poseStack, SubmitBuffers buffer,
                                      int packedLight, float tick, boolean propsOnly) {
        DefaultBuild build = defaultBuild(entity);
        if (!propsOnly) {
            FrameModels.render(build.frame, poseStack, buffer, packedLight, build.visual().frameScale());
            renderPart(build.flightController, poseStack, buffer, packedLight,
                0, 0.03f, -0.01f, 0, build.visual().electronicsScale());
            renderPart(build.videoTransmitter, poseStack, buffer, packedLight,
                0, 0.04f, 0.12f, 0, build.visual().electronicsScale() * 0.9f);
            renderPart(build.battery, poseStack, buffer, packedLight,
                build.spec.batteryMount().x(), build.spec.batteryMount().y(), build.spec.batteryMount().z(), 0, build.visual().batteryScale());
            renderCameraPart(build.camera, poseStack, buffer, packedLight,
                build.spec.cameraMount().x(), build.spec.cameraMount().y(), build.spec.cameraMount().z(), build.visual().cameraScale());
            if (!build.module.isEmpty()) {
                renderPart(build.module, poseStack, buffer, packedLight,
                    build.spec.moduleMount().x(), build.spec.moduleMount().y(), build.spec.moduleMount().z(), 0, build.visual().moduleScale());
            }
        }
        float spin = entity.getVisualThrottle() * tick * 40f;
        boolean axial = build.spec.role() == org.restor.fpv.assembly.FrameRole.INTERCEPTOR;
        for (int i = 0; i < build.spec.motorCount(); i++) {
            MountPoint m = build.spec.motorMounts().get(i);
            float x = m.x() * build.visual().armSpread();
            float z = m.z() * build.visual().armSpread();
            if (propsOnly && z > 0) continue;
            float propSpin = i % 2 == 0 ? spin : -spin;
            if (axial) {
                renderRotorPart(build.motor, poseStack, buffer, packedLight, x, m.y(), z, 0,
                    build.visual().motorScale());
                renderRotorPart(build.prop, poseStack, buffer, packedLight,
                    x, m.y(), z + build.visual().propLift(), propSpin, build.visual().propScale());
                continue;
            }
            renderPart(build.motor, poseStack, buffer, packedLight, x, m.y(), z, 0, build.visual().motorScale());
            renderPart(build.prop, poseStack, buffer, packedLight, x, m.y() + build.visual().propLift(), z,
                propSpin, build.visual().propScale());
        }
        if (!propsOnly) renderTypeDetails(entity, build, poseStack, buffer, packedLight);
    }

    private void renderPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                            int light, float x, float y, float z, float spinDeg) {
        renderPart(stack, poseStack, buffer, light, x, y, z, spinDeg, 1.0f);
    }

    private void renderPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                            int light, float x, float y, float z, float spinDeg, float scale) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scale, scale, scale);
        if (spinDeg != 0) poseStack.rotate(Axis.YP.rotationDegrees(spinDeg));
        buffer.item(stack, poseStack, light);
        poseStack.popPose();
    }

    /** Lays a rotor on its side so its thrust axis runs along the fuselage. */
    private void renderRotorPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                                 int light, float x, float y, float z, float spinDeg, float scale) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.rotate(Axis.XP.rotationDegrees(90));
        poseStack.scale(scale, scale, scale);
        if (spinDeg != 0) poseStack.rotate(Axis.YP.rotationDegrees(spinDeg));
        buffer.item(stack, poseStack, light);
        poseStack.popPose();
    }

    private void renderCameraPart(ItemStack stack, PoseStack poseStack, SubmitBuffers buffer,
                                  int light, float x, float y, float z, float scale) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.rotate(Axis.XP.rotationDegrees(22.5f));
        poseStack.scale(scale, scale, scale);
        buffer.item(stack, poseStack, light);
        poseStack.popPose();
    }

    private void renderTypeDetails(DroneEntity entity, DefaultBuild build, PoseStack poseStack,
                                   SubmitBuffers buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE));
        if (entity instanceof org.restor.fpv.entity.TinyWhoopDroneEntity
            || entity instanceof org.restor.fpv.entity.CinewhoopDroneEntity) {
            float radius = entity instanceof org.restor.fpv.entity.TinyWhoopDroneEntity ? 0.12f : 0.16f;
            for (MountPoint m : build.spec.motorMounts()) {
                renderDuct(consumer, poseStack, packedLight, m.x() * build.visual().armSpread(), m.y() + 0.01f, m.z() * build.visual().armSpread(), radius);
            }
        }
        if (entity instanceof org.restor.fpv.entity.RemoteRacingDroneEntity) {
            Matrix4f mat = poseStack.last().pose();
            renderBox(consumer, mat, packedLight, 0.14f, 0.08f, 0.10f, 0.17f, 0.42f, 0.13f, 0.02f, 0.02f, 0.02f);
            renderBox(consumer, mat, packedLight, -0.17f, 0.08f, 0.10f, -0.14f, 0.34f, 0.13f, 0.02f, 0.02f, 0.02f);
        }
        if (entity instanceof org.restor.fpv.entity.RacingDroneEntity || entity instanceof org.restor.fpv.entity.RemoteRacingDroneEntity) {
            Matrix4f mat = poseStack.last().pose();
            renderBox(consumer, mat, packedLight, -0.10f, 0.11f, -0.02f, 0.10f, 0.13f, 0.02f, 0.03f, 0.03f, 0.03f);
        }
    }

    private static DefaultBuild defaultBuild(DroneEntity entity) {
        if (entity instanceof org.restor.fpv.entity.TinyWhoopDroneEntity) {
            return new DefaultBuild(FrameSpecs.FRAME_TINY_WHOOP,
                new ItemStack(ModComponents.FRAME_TINY_WHOOP.get()),
                new ItemStack(ModComponents.MOTOR_1103_8000.get()),
                new ItemStack(ModComponents.PROP_65MM.get()),
                new ItemStack(ModComponents.BATTERY_1S_300.get()),
                new ItemStack(ModComponents.CAMERA_STANDARD.get()),
                new ItemStack(ModComponents.FC_F411.get()),
                new ItemStack(ModComponents.VTX_25MW.get()),
                ItemStack.EMPTY);
        }
        if (entity instanceof org.restor.fpv.entity.CinewhoopDroneEntity) {
            return new DefaultBuild(FrameSpecs.FRAME_CINEWHOOP,
                new ItemStack(ModComponents.FRAME_CINEWHOOP.get()),
                new ItemStack(ModComponents.MOTOR_1404_4500.get()),
                new ItemStack(ModComponents.PROP_3X2X3.get()),
                new ItemStack(ModComponents.BATTERY_4S_650.get()),
                new ItemStack(ModComponents.CAMERA_WIDE.get()),
                new ItemStack(ModComponents.FC_F411.get()),
                new ItemStack(ModComponents.VTX_200MW.get()),
                ItemStack.EMPTY);
        }
        ItemStack stockModule = entity instanceof org.restor.fpv.entity.KamikazeDroneEntity
            ? new ItemStack(ModComponents.MODULE_WARHEAD.get())
            : new ItemStack(ModComponents.MODULE_PAYLOAD_BAY.get());
        return new DefaultBuild(FrameSpecs.FRAME_5INCH,
            new ItemStack(ModComponents.FRAME_5INCH.get()),
            new ItemStack(ModComponents.MOTOR_2207_2450.get()),
            new ItemStack(ModComponents.PROP_5X4X3.get()),
            new ItemStack(ModComponents.BATTERY_4S_1300.get()),
            new ItemStack(ModComponents.CAMERA_STANDARD.get()),
            new ItemStack(ModComponents.FC_H7.get()),
            new ItemStack(ModComponents.VTX_800MW.get()),
            stockModule);
    }

    private record DefaultBuild(FrameSpec spec, ItemStack frame, ItemStack motor, ItemStack prop,
                                ItemStack battery, ItemStack camera, ItemStack flightController,
                                ItemStack videoTransmitter, ItemStack module) {
        org.restor.fpv.assembly.DroneVisual visual() {
            return org.restor.fpv.assembly.DroneVisual.of(spec);
        }
    }

    private void renderFrame(DroneEntity entity, VertexConsumer consumer, PoseStack poseStack, int packedLight) {
        Matrix4f mat = poseStack.last().pose();
        boolean tiny = entity instanceof org.restor.fpv.entity.TinyWhoopDroneEntity;
        boolean cine = entity instanceof org.restor.fpv.entity.CinewhoopDroneEntity;
        boolean remote = entity instanceof org.restor.fpv.entity.RemoteRacingDroneEntity;
        boolean kamikaze = entity instanceof org.restor.fpv.entity.KamikazeDroneEntity;

        // Central body
        renderBox(consumer, mat, packedLight, -0.22f, -0.08f, -0.18f, 0.22f, 0.08f, 0.22f,
            kamikaze ? 0.18f : 0.12f, kamikaze ? 0.20f : 0.12f, kamikaze ? 0.12f : 0.12f);
        // Top plate and battery strap make the stock quads read as assembled drones.
        renderBox(consumer, mat, packedLight, -0.16f, 0.08f, -0.12f, 0.16f, 0.14f, 0.18f, 0.05f, 0.05f, 0.05f);
        renderBox(consumer, mat, packedLight, -0.06f, 0.14f, -0.16f, 0.06f, 0.18f, 0.20f, 0.8f, 0.12f, 0.08f);

        float al = tiny ? 0.42f : cine ? 0.48f : 0.55f;
        float at = tiny ? 0.025f : 0.04f;

        renderBox(consumer, mat, packedLight, 0.0f, -at, -0.0f, al, at, -al, 0.4f, 0.4f, 0.4f);
        renderBox(consumer, mat, packedLight, -0.0f, -at, -0.0f, -al, at, -al, 0.4f, 0.4f, 0.4f);
        renderBox(consumer, mat, packedLight, 0.0f, -at, 0.0f, al, at, al, 0.4f, 0.4f, 0.4f);
        renderBox(consumer, mat, packedLight, -0.0f, -at, 0.0f, -al, at, al, 0.4f, 0.4f, 0.4f);

        if (cine || tiny) {
            float duct = tiny ? 0.16f : 0.20f;
            renderDuct(consumer, poseStack, packedLight, al, 0.01f, -al, duct);
            renderDuct(consumer, poseStack, packedLight, -al, 0.01f, -al, duct);
            renderDuct(consumer, poseStack, packedLight, al, 0.01f, al, duct);
            renderDuct(consumer, poseStack, packedLight, -al, 0.01f, al, duct);
        }
        if (remote) {
            renderBox(consumer, mat, packedLight, 0.18f, 0.12f, 0.10f, 0.22f, 0.48f, 0.14f, 0.02f, 0.02f, 0.02f);
            renderBox(consumer, mat, packedLight, -0.22f, 0.12f, 0.10f, -0.18f, 0.40f, 0.14f, 0.02f, 0.02f, 0.02f);
        }
    }

    private void renderMotorsAndProps(DroneEntity entity, VertexConsumer consumer, PoseStack poseStack, int packedLight,
                                       float tick, float throttle) {
        float al = entity instanceof org.restor.fpv.entity.TinyWhoopDroneEntity ? 0.42f
            : entity instanceof org.restor.fpv.entity.CinewhoopDroneEntity ? 0.48f : 0.55f;
        float spin = throttle * 40;

        renderMotorAt(consumer, poseStack, packedLight, al, 0, -al, tick, spin, true);
        renderMotorAt(consumer, poseStack, packedLight, -al, 0, -al, tick, spin, false);
        renderMotorAt(consumer, poseStack, packedLight, al, 0, al, tick, spin, false);
        renderMotorAt(consumer, poseStack, packedLight, -al, 0, al, tick, spin, true);
    }

    private void renderDuct(VertexConsumer consumer, PoseStack poseStack, int packedLight,
                            float x, float y, float z, float radius) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f mat = poseStack.last().pose();
        float t = radius * 0.22f;
        renderBox(consumer, mat, packedLight, -radius, -0.025f, -radius, radius, 0.025f, -radius + t, 0.08f, 0.08f, 0.08f);
        renderBox(consumer, mat, packedLight, -radius, -0.025f, radius - t, radius, 0.025f, radius, 0.08f, 0.08f, 0.08f);
        renderBox(consumer, mat, packedLight, -radius, -0.025f, -radius, -radius + t, 0.025f, radius, 0.08f, 0.08f, 0.08f);
        renderBox(consumer, mat, packedLight, radius - t, -0.025f, -radius, radius, 0.025f, radius, 0.08f, 0.08f, 0.08f);
        poseStack.popPose();
    }

    private void renderMotorAt(VertexConsumer consumer, PoseStack poseStack, int packedLight,
                               float x, float y, float z, float tick, float spinRate, boolean cw) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // Motor bell
        Matrix4f mat = poseStack.last().pose();
        renderBox(consumer, mat, packedLight, -0.06f, -0.03f, -0.06f, 0.06f, 0.06f, 0.06f, 0.3f, 0.3f, 0.3f);
        renderBox(consumer, mat, packedLight, -0.08f, 0.02f, -0.08f, 0.08f, 0.03f, 0.08f, 0.25f, 0.25f, 0.25f);

        // Propeller (spinning blur when throttle > 0)
        if (spinRate > 1) {
            float angle = tick * spinRate * (cw ? 1 : -1);
            poseStack.rotate(Axis.YP.rotationDegrees(angle));
            float pr = 0.2f, pw = 0.015f;
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -pr, 0.04f, -pw, pr, 0.05f, pw, 0.4f, 0.6f, 0.9f);
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -pw, 0.04f, -pr, pw, 0.05f, pr, 0.4f, 0.6f, 0.9f);
        } else {
            // Static props
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -0.15f, 0.04f, -0.01f, 0.15f, 0.05f, 0.01f, 0.4f, 0.6f, 0.9f);
            renderBox(consumer, poseStack.last().pose(), packedLight,
                -0.01f, 0.04f, -0.15f, 0.01f, 0.05f, 0.15f, 0.4f, 0.6f, 0.9f);
        }

        poseStack.popPose();
    }

    private void renderCamera(VertexConsumer consumer, PoseStack poseStack, int packedLight) {
        Matrix4f mat = poseStack.last().pose();
        // Camera on front
        renderBox(consumer, mat, packedLight, -0.05f, 0.03f, -0.25f, 0.05f, 0.08f, -0.18f, 0.1f, 0.1f, 0.1f);
        renderBox(consumer, mat, packedLight, -0.025f, 0.04f, -0.26f, 0.025f, 0.07f, -0.25f, 0.3f, 0.6f, 1.0f);
    }

    private void renderPayload(DroneEntity entity, VertexConsumer consumer, PoseStack poseStack, int packedLight) {
        // The fixed warhead is now the same painted item model used by modular builds.
        // Preset drones with a payload bay still draw their currently loaded charges here.
        if (!(entity instanceof org.restor.fpv.entity.KamikazeDroneEntity)) {
            renderDropCharges(entity, consumer, poseStack, packedLight);
        }
    }

    // Small dark charges strapped under the belly, one per loaded payload (FPV_IDEAS §4).
    private void renderDropCharges(DroneEntity entity, VertexConsumer consumer, PoseStack poseStack, int packedLight) {
        int ammo = entity.getPayloadAmmo();
        if (ammo <= 0) return;
        Matrix4f mat = poseStack.last().pose();
        float startX = -0.06f * (ammo - 1);
        for (int i = 0; i < ammo; i++) {
            float cx = startX + i * 0.12f;
            renderBox(consumer, mat, packedLight, cx - 0.045f, -0.18f, -0.06f, cx + 0.045f, -0.09f, 0.06f,
                0.55f, 0.15f, 0.12f);
        }
    }

    private void renderBox(VertexConsumer consumer, Matrix4f mat, int packedLight,
                           float x1, float y1, float z1, float x2, float y2, float z2,
                           float r, float g, float b) {
        float u1 = 0, v1 = 0, u2 = 1, v2 = 1;

        // Top
        consumer.addVertex(mat, x1, y2, z1).setColor(r, g, b, 1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);
        consumer.addVertex(mat, x2, y2, z1).setColor(r, g, b, 1).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);
        consumer.addVertex(mat, x2, y2, z2).setColor(r, g, b, 1).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);
        consumer.addVertex(mat, x1, y2, z2).setColor(r, g, b, 1).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 1, 0);

        // Bottom
        consumer.addVertex(mat, x1, y1, z1).setColor(r, g, b, 1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);
        consumer.addVertex(mat, x1, y1, z2).setColor(r, g, b, 1).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);
        consumer.addVertex(mat, x2, y1, z2).setColor(r, g, b, 1).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);
        consumer.addVertex(mat, x2, y1, z1).setColor(r, g, b, 1).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, -1, 0);

        // Front
        consumer.addVertex(mat, x1, y1, z1).setColor(r, g, b, 1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);
        consumer.addVertex(mat, x2, y1, z1).setColor(r, g, b, 1).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);
        consumer.addVertex(mat, x2, y2, z1).setColor(r, g, b, 1).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);
        consumer.addVertex(mat, x1, y2, z1).setColor(r, g, b, 1).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, -1);

        // Back
        consumer.addVertex(mat, x1, y1, z2).setColor(r, g, b, 1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(mat, x1, y2, z2).setColor(r, g, b, 1).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(mat, x2, y2, z2).setColor(r, g, b, 1).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);
        consumer.addVertex(mat, x2, y1, z2).setColor(r, g, b, 1).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0, 0, 1);

        // Right
        consumer.addVertex(mat, x2, y1, z1).setColor(r, g, b, 1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
        consumer.addVertex(mat, x2, y1, z2).setColor(r, g, b, 1).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
        consumer.addVertex(mat, x2, y2, z2).setColor(r, g, b, 1).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);
        consumer.addVertex(mat, x2, y2, z1).setColor(r, g, b, 1).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(1, 0, 0);

        // Left
        consumer.addVertex(mat, x1, y1, z1).setColor(r, g, b, 1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
        consumer.addVertex(mat, x1, y2, z1).setColor(r, g, b, 1).setUv(u2, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
        consumer.addVertex(mat, x1, y2, z2).setColor(r, g, b, 1).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
        consumer.addVertex(mat, x1, y1, z2).setColor(r, g, b, 1).setUv(u1, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(-1, 0, 0);
    }

}
