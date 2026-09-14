package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.item.ModItems;

public class GogglesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemRenderer itemRenderer;

    public GogglesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                        ItemRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!stack.is(ModItems.FPV_GOGGLES.get())) return;
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        pose.translate(0f, -0.25f, 0f);
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        pose.scale(0.625f, -0.625f, -0.625f);
        itemRenderer.renderStatic(player, stack, ItemDisplayContext.HEAD, false, pose, buffer,
            player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
    }
}
