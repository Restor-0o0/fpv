package org.restor.fpv.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// A payload/utility module for the frame's module slot. The ModuleType decides the
// installed behaviour; frame compatibility rules live in DroneAssembly.isCompatible.
public class ModuleItem extends ComponentItem {
    private final ModuleType moduleType;

    public ModuleItem(ComponentStats stats, ModuleType moduleType) {
        super(stats);
        this.moduleType = moduleType;
    }

    public ModuleType getModuleType() {
        return moduleType;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.fpv.module." + moduleType.name().toLowerCase()));
    }
}
