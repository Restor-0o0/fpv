package org.restor.fpv.item;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;


/** Head-slot wearable that carries the video feed. Not an ArmorItem: it grants no
 *  protection and needs no armour model. */
public class FpvGogglesItem extends Item {
    public FpvGogglesItem(Item.Properties props) {
        super(props.stacksTo(16).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.fpv.goggles").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.goggles.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.goggles.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
