package org.restor.fpv.assembly;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.item.DroneItem;

import java.util.List;

// Item form of a built modular drone; the whole assembly lives in its NBT.
public class ModularDroneItem extends DroneItem {
    public ModularDroneItem() {
        super(ModEntities.MODULAR, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag() && stack.getTag().contains("Assembly")) {
            DroneAssembly a = DroneAssembly.load(stack.getTag().getCompound("Assembly"));
            FrameSpec spec = a.frameSpec();
            if (!a.frame.isEmpty())   tooltip.add(Component.literal("§7" + a.frame.getHoverName().getString()));
            if (!a.battery.isEmpty()) tooltip.add(Component.literal("§8+ " + a.battery.getHoverName().getString()));
            if (!a.camera.isEmpty())  tooltip.add(Component.literal("§8+ " + a.camera.getHoverName().getString()));
            if (spec != null) {
                tooltip.add(Component.literal(String.format("§8%d motors, %.0f g, %.0f N",
                    spec.motorCount(), a.totalMassKg() * 1000, a.totalThrustN())));
                float[] trim = a.computeTrim();
                if (Math.abs(trim[0]) > 1 || Math.abs(trim[1]) > 1 || Math.abs(trim[2]) > 1) {
                    tooltip.add(Component.literal("§c⚠ несбалансированные моторы/пропы (перекос в полёте)"));
                }
            }
        }
    }
}
