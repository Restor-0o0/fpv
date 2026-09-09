package org.restor.fpv.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FirmwareItem extends ComponentItem {
    private final FirmwareType firmwareType;

    public FirmwareItem(ComponentStats stats, FirmwareType firmwareType) {
        super(stats);
        this.firmwareType = firmwareType;
    }

    public FirmwareType getFirmwareType() {
        return firmwareType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.fpv.firmware." + firmwareType.id()));
        for (org.restor.fpv.entity.DroneMode mode : firmwareType.modes()) {
            tooltip.add(Component.literal(" • ").append(Component.translatable(mode.translationKey())));
        }
    }
}
