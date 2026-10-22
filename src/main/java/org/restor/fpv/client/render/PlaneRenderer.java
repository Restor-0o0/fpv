package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.item.ModItems;
import org.joml.Quaternionf;

public class PlaneRenderer extends EntityRenderer<PlaneEntity, PlaneRenderer.State> {
    public static class State extends net.minecraft.client.renderer.entity.state.EntityRenderState {
        PlaneEntity entity;
    }

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Fpv.MODID, "textures/entity/plane.png");
    private static final double FAR_RENDER_DISTANCE_SQR = 2048.0 * 2048.0;
    private static final float MODEL_SCALE = 1.95f;

    private final java.util.Map<net.minecraft.world.item.Item, ItemStack> models = new java.util.IdentityHashMap<>();

    public PlaneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(PlaneEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ, float partialTicks) {
        if (entity instanceof org.restor.fpv.entity.KamikazeWingEntity) {
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
    public void extractRenderState(PlaneEntity entity, State state, float partialTick) {
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

    private void render(PlaneEntity entity, float partialTick, PoseStack poseStack,
                        SubmitBuffers buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != null
            && (mc.getCameraEntity() == entity || mc.getCameraEntity().getVehicle() == entity)
            && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() / 2, 0);

        Quaternionf q;
        if (entity instanceof org.restor.fpv.entity.KamikazeWingEntity wing && wing.isAttackRun()
            && entity.getDeltaMovement().lengthSqr() > 1.0e-4) {
            net.minecraft.world.phys.Vec3 v = entity.getDeltaMovement();
            double len = v.length();
            float vyaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
            float vpitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, v.y / len))));
            q = modelQuat(vyaw, vpitch, 0f);
        } else {
            q = new Quaternionf(modelQuat(entity.yRotO, entity.xRotO, entity.getPrevRoll()))
                .slerp(modelQuat(entity.getYRot(), entity.getXRot(), entity.getRoll()), partialTick);
        }
        poseStack.rotate(q);

        float scale = MODEL_SCALE * entity.getModelScale();
        poseStack.scale(scale, scale, scale);
        buffer.item(modelStack(entity), poseStack, packedLight);
        renderExtras(entity, partialTick, poseStack, buffer, packedLight);

        poseStack.popPose();
    }

    /** Called inside the oriented, model-scaled pose so subclasses can bolt parts on. */
    protected void renderExtras(PlaneEntity entity, float partialTick, PoseStack poseStack,
                                SubmitBuffers buffer, int packedLight) {
    }

    protected ItemStack cachedStack(net.minecraft.world.item.Item item) {
        return models.computeIfAbsent(item, ItemStack::new);
    }

    private ItemStack modelStack(PlaneEntity entity) {
        net.minecraft.world.item.Item item = entity.getModelItem();
        if (item == null) item = ModItems.PLANE.get();
        return models.computeIfAbsent(item, ItemStack::new);
    }

    private static Quaternionf modelQuat(float yaw, float pitch, float roll) {
        return new Quaternionf()
            .rotateY((float) Math.toRadians(180f - yaw))
            .rotateX((float) Math.toRadians(-pitch))
            .rotateZ((float) Math.toRadians(-roll));
    }

}
