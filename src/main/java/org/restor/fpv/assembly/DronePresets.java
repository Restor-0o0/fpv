package org.restor.fpv.assembly;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.compat.CustomData;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.WarheadType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ready-made builds handed out in the creative tab, so a session that is just about
 * flying does not start with a trip through the assembly station.
 */
public final class DronePresets {
    private DronePresets() {}

    public static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();

        out.add(build("freestyle_5", ModComponents.FRAME_5INCH, ModComponents.MOTOR_2207_2450,
            ModComponents.PROP_5X4X3, ModComponents.BATTERY_4S_1300, ModComponents.CAMERA_STANDARD, null));
        out.add(build("whoop", ModComponents.FRAME_TINY_WHOOP, ModComponents.MOTOR_1103_8000,
            ModComponents.PROP_65MM, ModComponents.BATTERY_1S_300, ModComponents.CAMERA_STANDARD, null));
        out.add(build("cine", ModComponents.FRAME_CINEWHOOP, ModComponents.MOTOR_1404_4500,
            ModComponents.PROP_3X2X3, ModComponents.BATTERY_4S_650, ModComponents.CAMERA_WIDE, null));
        out.add(build("longrange_7", ModComponents.FRAME_7INCH, ModComponents.MOTOR_2806_1300,
            ModComponents.PROP_5X4X2, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD, null));
        out.add(build("bomber_5", ModComponents.FRAME_5INCH, ModComponents.MOTOR_2207_2450,
            ModComponents.PROP_5X4X3, ModComponents.BATTERY_4S_1300, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_PAYLOAD_BAY));
        out.add(build("kamikaze_5", ModComponents.FRAME_5INCH, ModComponents.MOTOR_2207_2450,
            ModComponents.PROP_5X4X3, ModComponents.BATTERY_4S_1300, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.HE));
        out.add(build("interceptor", ModComponents.FRAME_INTERCEPTOR, ModComponents.MOTOR_2207_2450,
            ModComponents.PROP_5X4X3, ModComponents.BATTERY_4S_1300, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.PROXIMITY));
        out.add(build("strike_6", ModComponents.FRAME_HEXA_6INCH, ModComponents.MOTOR_2306_1700,
            ModComponents.PROP_5X4X3, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.FRAG));
        out.add(build("cargo_8", ModComponents.FRAME_OCTO_8INCH, ModComponents.MOTOR_3115_900,
            ModComponents.PROP_5X4X2, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_WIDE,
            ModComponents.MODULE_STORAGE));
        out.add(build("agro_6", ModComponents.FRAME_HEXA_6INCH, ModComponents.MOTOR_2306_1700,
            ModComponents.PROP_5X4X3, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_WIDE,
            ModComponents.MODULE_PAYLOAD_BAY));
        out.add(build("agro_8", ModComponents.FRAME_OCTO_8INCH, ModComponents.MOTOR_3115_900,
            ModComponents.PROP_5X4X2, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_WIDE,
            ModComponents.MODULE_PAYLOAD_BAY));

        out.add(build("plane_trainer", ModComponents.PLANE_FRAME_TRAINER, ModComponents.MOTOR_2814_1100,
            ModComponents.PROP_8X6, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD, null));
        out.add(build("plane_recon", ModComponents.PLANE_FRAME_GLIDER, ModComponents.MOTOR_2814_1100,
            ModComponents.PROP_8X6, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_WIDE, null));
        out.add(build("plane_racer", ModComponents.PLANE_FRAME_WING, ModComponents.MOTOR_3548_900,
            ModComponents.PROP_10X5, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD, null));
        out.add(build("plane_delta_strike", ModComponents.PLANE_FRAME_DELTA, ModComponents.MOTOR_3548_900,
            ModComponents.PROP_10X5, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.HE));
        out.add(build("plane_bomber", ModComponents.PLANE_FRAME_STRIKE, ModComponents.MOTOR_3548_900,
            ModComponents.PROP_10X5, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.HEAVY));
        out.add(build("plane_cargo", ModComponents.PLANE_FRAME_CARGO, ModComponents.MOTOR_4250_650,
            ModComponents.PROP_12X6, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_WIDE,
            ModComponents.MODULE_STORAGE));

        out.add(withBay(build("plane_spotter", ModComponents.PLANE_FRAME_SCOUT, ModComponents.MOTOR_2814_1100,
            ModComponents.PROP_8X6, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_WIDE,
            ModComponents.MODULE_TARGETING),
            card(4, ModComponents.FRAME_5INCH, ModComponents.MOTOR_2207_2450, ModComponents.PROP_5X4X3,
                ModComponents.BATTERY_4S_1300, ModComponents.CAMERA_STANDARD,
                ModComponents.MODULE_WARHEAD, WarheadType.HE),
            card(2, ModComponents.PLANE_FRAME_KAMIKAZE, ModComponents.MOTOR_3548_900, ModComponents.PROP_10X5,
                ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD,
                ModComponents.MODULE_WARHEAD, WarheadType.HEAVY)));
        out.add(build("plane_kamikaze", ModComponents.PLANE_FRAME_KAMIKAZE, ModComponents.MOTOR_3548_900,
            ModComponents.PROP_10X5, ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.HE));
        out.add(withBay(build("plane_carrier", ModComponents.PLANE_FRAME_CARRIER, ModComponents.MOTOR_4250_650,
            ModComponents.PROP_12X6, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_WIDE, null),
            card(4, ModComponents.FRAME_5INCH, ModComponents.MOTOR_2207_2450, ModComponents.PROP_5X4X3,
                ModComponents.BATTERY_4S_1300, ModComponents.CAMERA_STANDARD,
                ModComponents.MODULE_WARHEAD, WarheadType.FRAG),
            card(2, ModComponents.FRAME_7INCH, ModComponents.MOTOR_2806_1300, ModComponents.PROP_5X4X2,
                ModComponents.BATTERY_6S_1100, ModComponents.CAMERA_STANDARD,
                ModComponents.MODULE_WARHEAD, WarheadType.SHAPED)));
        out.add(build("plane_loiter", ModComponents.PLANE_FRAME_LOITER, ModComponents.MOTOR_4250_650,
            ModComponents.PROP_12X6, ModComponents.BATTERY_6S_5000, ModComponents.CAMERA_STANDARD,
            ModComponents.MODULE_WARHEAD, WarheadType.HEAVY));

        return out;
    }

    /**
     * Every preset folded into a card, so a bay can be filled straight out of the creative
     * tab. A carded aircraft drops whatever was in its own bay: cards must not nest.
     */
    public static List<ItemStack> cards() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack preset : all()) {
            var data = CustomData.get(preset);
            if (data == null) continue;
            var tag = data.copyTag();
            DroneAssembly a = DroneAssembly.load(tag.getCompound("Assembly"));
            for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) a.bay[i] = ItemStack.EMPTY;
            WarheadType warhead = tag.contains("Warhead") ? WarheadType.byId(tag.getString("Warhead")) : null;
            ItemStack card = DroneCardItem.of(a, warhead);
            Component name = (preset.hasCustomHoverName() ? preset.getHoverName() : null);
            if (name != null) card.setHoverName(name);
            out.add(card);
        }
        return out;
    }

    private static ItemStack build(String id, Supplier<? extends Item> frame, Supplier<? extends Item> motor,
                                   Supplier<? extends Item> prop, Supplier<? extends Item> battery,
                                   Supplier<? extends Item> camera, Supplier<? extends Item> module) {
        return build(id, frame, motor, prop, battery, camera, module, null);
    }

    private static DroneAssembly assemble(Supplier<? extends Item> frame, Supplier<? extends Item> motor,
                                          Supplier<? extends Item> prop, Supplier<? extends Item> battery,
                                          Supplier<? extends Item> camera, Supplier<? extends Item> module) {
        DroneAssembly a = new DroneAssembly();
        a.frame = new ItemStack(frame.get());
        a.battery = new ItemStack(battery.get());
        a.camera = new ItemStack(camera.get());
        if (module != null) a.module = new ItemStack(module.get());
        float size = frame.get() instanceof org.restor.fpv.component.ComponentItem ci
            ? ci.getStats().frameSize() : 5f;
        a.fc = new ItemStack((size >= 6f ? ModComponents.FC_H7 : ModComponents.FC_F411).get());
        a.vtx = new ItemStack((size >= 6f ? ModComponents.VTX_800MW
            : size >= 3f ? ModComponents.VTX_200MW : ModComponents.VTX_25MW).get());
        FrameSpec spec = FrameSpecs.byItem(frame.get());
        int motors = spec != null ? spec.motorCount() : 4;
        for (int i = 0; i < motors; i++) {
            a.motors[i] = new ItemStack(motor.get());
            a.props[i] = new ItemStack(prop.get());
        }
        return a;
    }

    private static ItemStack card(int count, Supplier<? extends Item> frame, Supplier<? extends Item> motor,
                                  Supplier<? extends Item> prop, Supplier<? extends Item> battery,
                                  Supplier<? extends Item> camera, Supplier<? extends Item> module,
                                  WarheadType warhead) {
        ItemStack stack = DroneCardItem.of(assemble(frame, motor, prop, battery, camera, module), warhead);
        stack.setCount(count);
        return stack;
    }

    private static ItemStack withBay(ItemStack preset, ItemStack... cards) {
        var data = CustomData.get(preset);
        if (data == null) return preset;
        DroneAssembly a = DroneAssembly.load(data.copyTag().getCompound("Assembly"));
        for (int i = 0; i < cards.length && i < DroneAssembly.BAY_SIZE; i++) a.bay[i] = cards[i];
        CustomData.update(preset, t -> t.put("Assembly", a.save()));
        return preset;
    }

    private static ItemStack build(String id, Supplier<? extends Item> frame, Supplier<? extends Item> motor,
                                   Supplier<? extends Item> prop, Supplier<? extends Item> battery,
                                   Supplier<? extends Item> camera, Supplier<? extends Item> module,
                                   WarheadType warhead) {
        DroneAssembly a = assemble(frame, motor, prop, battery, camera, module);
        FrameSpec spec = FrameSpecs.byItem(frame.get());

        boolean wing = spec != null && spec.isFixedWing();
        ItemStack stack = new ItemStack(wing ? ModItems.MODULAR_PLANE.get() : ModItems.MODULAR_DRONE.get());
        CustomData.update(stack, t -> {
            t.put("Assembly", a.save());
            if (warhead != null) t.putString("Warhead", warhead.id());
        });
        stack.setHoverName(Component.translatable("preset.fpv." + id).withStyle(net.minecraft.ChatFormatting.AQUA));
        return stack;
    }
}
