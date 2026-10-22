package org.restor.fpv.component;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;


// A payload/utility module for the frame's module slot. The ModuleType decides the
// installed behaviour; frame compatibility rules live in DroneAssembly.isCompatible.
public class ModuleItem extends ComponentItem {
    private final ModuleType moduleType;

    public ModuleItem(Item.Properties props, ComponentStats stats, ModuleType moduleType) {
        super(props, stats);
        this.moduleType = moduleType;
    }

    public ModuleType getModuleType() {
        return moduleType;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip.fpv.module." + moduleType.name().toLowerCase()));
    }
}
