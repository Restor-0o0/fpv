package org.restor.fpv.assembly;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.WarheadType;

import java.util.ArrayList;
import java.util.List;

/**
 * Ready-made builds handed out in the creative tab, so a session that is just about
 * flying does not start with a trip through the assembly station. The parts lists live
 * in DronePresetSpecs.
 */
public final class DronePresets {
    private DronePresets() {}

    public static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        for (DronePresetSpecs.Build build : DronePresetSpecs.ALL) out.add(item(build));
        return out;
    }

    /**
     * Every preset folded into a card, so a bay can be filled straight out of the creative
     * tab. A carded aircraft drops whatever was in its own bay: cards must not nest.
     */
    public static List<ItemStack> cards() {
        List<ItemStack> out = new ArrayList<>();
        for (DronePresetSpecs.Build build : DronePresetSpecs.ALL) {
            ItemStack card = DroneCardItem.of(assemble(build), build.warhead());
            card.set(DataComponents.CUSTOM_NAME, name(build));
            out.add(card);
        }
        return out;
    }

    private static DroneAssembly assemble(DronePresetSpecs.Build build) {
        DroneAssembly a = new DroneAssembly();
        a.frame = stack(build.frame());
        a.battery = stack(build.battery());
        a.camera = stack(build.camera());
        a.module = stack(build.module());
        a.fitStockAvionics();
        for (int i = 0; i < build.motorCount(); i++) {
            a.motors[i] = stack(build.motor());
            a.props[i] = stack(build.prop());
        }
        return a;
    }

    private static ItemStack item(DronePresetSpecs.Build build) {
        DroneAssembly a = assemble(build);
        List<DronePresetSpecs.Card> bay = build.bay();
        for (int i = 0; i < bay.size() && i < DroneAssembly.BAY_SIZE; i++) {
            DronePresetSpecs.Card card = bay.get(i);
            ItemStack stack = DroneCardItem.of(assemble(card.build()), card.build().warhead());
            stack.setCount(card.count());
            a.bay[i] = stack;
        }

        WarheadType warhead = build.warhead();
        ItemStack stack = new ItemStack(build.wing()
            ? ModItems.MODULAR_PLANE.get() : ModItems.MODULAR_DRONE.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            t.put("Assembly", a.save());
            if (warhead != null) t.putString("Warhead", warhead.id());
        });
        stack.set(DataComponents.CUSTOM_NAME, name(build));
        return stack;
    }

    private static Component name(DronePresetSpecs.Build build) {
        return Component.translatable("preset.fpv." + build.id())
            .withStyle(net.minecraft.ChatFormatting.AQUA);
    }

    private static ItemStack stack(ComponentStats stats) {
        java.util.function.Supplier<? extends Item> item = ModComponents.itemFor(stats);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }
}
