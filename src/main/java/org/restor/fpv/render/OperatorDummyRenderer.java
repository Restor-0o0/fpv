package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.restor.fpv.entity.OperatorDummyEntity;

import java.util.UUID;

// Pilot stand-in rendered with the ACTUAL PLAYER SKIN (resolved from the pilot's
// UUID), posed holding a controller.
public class OperatorDummyRenderer extends EntityRenderer<OperatorDummyEntity> {
    private final PlayerModel<AbstractClientPlayer> model;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public OperatorDummyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        model = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        slimModel = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(OperatorDummyEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);

        PlayerInfo info = resolvePilot(entity);
        boolean slim = info != null && info.getSkin().model() == PlayerSkin.Model.SLIM;
        ResourceLocation skin = info != null ? info.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
        PlayerModel<AbstractClientPlayer> m = slim ? slimModel : model;

        poseStack.pushPose();
        // Standard living-entity model space: rotate to facing, flip, drop to feet.
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(0f, -1.501f, 0f);

        pose(m);
        var vc = buffer.getBuffer(RenderType.entityTranslucent(skin));
        m.renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        renderHeadGear(entity, m, poseStack, buffer, packedLight);
        poseStack.popPose();

        renderRadio(entity, partialTick, poseStack, buffer, packedLight);
    }

    // Same placement vanilla's CustomHeadLayer uses, so head gear sits on the stand-in
    // exactly as it does on the player.
    private static void renderHeadGear(OperatorDummyEntity entity, PlayerModel<AbstractClientPlayer> m,
                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        var gear = entity.getHeadGear();
        if (gear.isEmpty()) return;
        poseStack.pushPose();
        m.head.translateAndRotate(poseStack);
        poseStack.translate(0f, -0.25f, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.scale(0.625f, -0.625f, -0.625f);
        Minecraft.getInstance().getItemRenderer().renderStatic(gear,
            net.minecraft.world.item.ItemDisplayContext.HEAD, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    private static void renderRadio(OperatorDummyEntity entity, float partialTick, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight) {
        var radio = entity.getRadio();
        if (radio.isEmpty()) return;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.translate(0f, 1.12f, -0.34f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-55f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.scale(0.9f, 0.9f, 0.9f);
        Minecraft.getInstance().getItemRenderer().renderStatic(radio,
            net.minecraft.world.item.ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    // Static "operator" pose: standing, both arms forward holding the radio.
    private static void pose(PlayerModel<AbstractClientPlayer> m) {
        m.setAllVisible(true);
        m.young = false;
        m.crouching = false;
        m.head.setRotation(0.25f, 0, 0); // looking slightly down at the sticks
        m.hat.copyFrom(m.head);
        m.body.setRotation(0, 0, 0);
        m.jacket.copyFrom(m.body);
        m.rightArm.setRotation(-1.1f, -0.25f, 0);
        m.leftArm.setRotation(-1.1f, 0.25f, 0);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightLeg.setRotation(0, 0, 0);
        m.leftLeg.setRotation(0, 0, 0);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    private static PlayerInfo resolvePilot(OperatorDummyEntity entity) {
        UUID id = entity.getPilotId();
        var conn = Minecraft.getInstance().getConnection();
        return id != null && conn != null ? conn.getPlayerInfo(id) : null;
    }

    // The stand-in marks where the pilot's real body is; a pitch-black silhouette in
    // an unlit spot reads as a glitch, so keep a floor under its block light.
    @Override
    protected int getBlockLightLevel(OperatorDummyEntity entity, net.minecraft.core.BlockPos pos) {
        return Math.max(super.getBlockLightLevel(entity, pos), 4);
    }

    @Override
    public ResourceLocation getTextureLocation(OperatorDummyEntity entity) {
        PlayerInfo info = resolvePilot(entity);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
    }
}
