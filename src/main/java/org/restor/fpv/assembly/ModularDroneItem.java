package org.restor.fpv.assembly;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.item.DroneItem;


// Item form of a built modular drone; the whole assembly lives in its NBT.
public class ModularDroneItem extends DroneItem {
    public ModularDroneItem(Item.Properties props) {
        super(props, ModEntities.MODULAR, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains("Assembly")) {
            DroneAssembly a = DroneAssembly.load(data.copyTag().getCompoundOrEmpty("Assembly"));
            FrameSpec spec = a.frameSpec();
            if (!a.frame.isEmpty())   tooltip.accept(Component.literal("§7" + a.frame.getHoverName().getString()));
            if (!a.battery.isEmpty()) tooltip.accept(Component.literal("§8+ " + a.battery.getHoverName().getString()));
            if (!a.camera.isEmpty())  tooltip.accept(Component.literal("§8+ " + a.camera.getHoverName().getString()));
            tooltip.accept(Component.translatable("tooltip.fpv.assembly_condition",
                Math.round(a.minimumCondition() / 10f)));
            if (spec != null) {
                tooltip.accept(Component.literal(String.format("§8%d motors, %.0f g, %.0f N",
                    spec.motorCount(), a.totalMassKg() * 1000, a.totalThrustN())));
                float[] trim = a.computeTrim();
                if (Math.abs(trim[0]) > 1 || Math.abs(trim[1]) > 1 || Math.abs(trim[2]) > 1) {
                    tooltip.accept(Component.translatable("tooltip.fpv.assembly.unbalanced"));
                }
            }
            if (!a.isComplete()) tooltip.accept(Component.translatable("tooltip.fpv.assembly_incomplete"));
        }
    }
}
