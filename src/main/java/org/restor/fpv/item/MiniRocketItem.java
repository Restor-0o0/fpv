package org.restor.fpv.item;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
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


public class MiniRocketItem extends Item {
    public MiniRocketItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PartsPiles.placeOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.fpv.mini_rocket.impact",
            DropChargeItem.fmt(MiniRocketEntity.impactDamage())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.mini_rocket.blast",
            DropChargeItem.fmt(MiniRocketEntity.blastRadius())).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.mini_rocket.terrain",
            MiniRocketEntity.blocksPunched()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.mini_rocket.load",
            org.restor.fpv.entity.DroneEntity.MAX_ROCKETS).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.fpv.ordnance.unload").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.fpv.mini_rocket.fire").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.component_place"));
    }
}
