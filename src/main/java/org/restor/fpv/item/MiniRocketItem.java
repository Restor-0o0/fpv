package org.restor.fpv.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.restor.fpv.block.PartsPiles;
import org.restor.fpv.entity.MiniRocketEntity;

import java.util.List;

public class MiniRocketItem extends Item {
    public MiniRocketItem() {
        super(new Item.Properties());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PartsPiles.placeOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.fpv.mini_rocket.impact",
            DropChargeItem.fmt(MiniRocketEntity.impactDamage())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.mini_rocket.blast",
            DropChargeItem.fmt(MiniRocketEntity.blastRadius())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.mini_rocket.terrain",
            MiniRocketEntity.blocksPunched()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.mini_rocket.load",
            org.restor.fpv.entity.DroneEntity.MAX_ROCKETS).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.fpv.ordnance.unload").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.fpv.mini_rocket.fire").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.component_place"));
    }
}
