package org.restor.fpv.component;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;


public class FirmwareItem extends ComponentItem {
    private final FirmwareType firmwareType;

    public FirmwareItem(Item.Properties props, ComponentStats stats, FirmwareType firmwareType) {
        super(props, stats);
        this.firmwareType = firmwareType;
    }

    public FirmwareType getFirmwareType() {
        return firmwareType;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip.fpv.firmware." + firmwareType.id()));
        for (org.restor.fpv.entity.DroneMode mode : firmwareType.modes()) {
            tooltip.accept(Component.literal(" • ").append(Component.translatable(mode.translationKey())));
        }
    }
}
