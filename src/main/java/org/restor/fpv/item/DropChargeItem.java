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
import org.restor.fpv.warhead.WarheadType;


public class DropChargeItem extends Item {
    public DropChargeItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        return PartsPiles.placeOn(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        WarheadType blast = WarheadType.DROP;
        tooltip.accept(Component.translatable("tooltip.fpv.warhead.power", fmt(blast.power()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.ordnance.terrain", fmt(blast.blockPower()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.ordnance.mob", fmt(blast.mobDamageMul()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.drop_charge.load").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.fpv.ordnance.unload").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.fpv.drop_charge.fuze").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.component_place"));
    }

    static String fmt(float v) {
        String out = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (out.endsWith("0")) out = out.substring(0, out.length() - 1);
        return out.endsWith(".") ? out.substring(0, out.length() - 1) : out;
    }
}
