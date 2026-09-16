package org.restor.fpv.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.component.ModComponents;

/** Rolls plausible NPC drone builds: a matching frame/motor/prop set plus a role module. */
public final class NpcDroneBuilds {
    private NpcDroneBuilds() {}

    public enum Role { KAMIKAZE, PATROL, INTERCEPTOR, BOMBER, ROCKET, SCOUT, JAMMER }

    private record Chassis(Supplier<Item> frame, Supplier<Item> motor, Supplier<Item> prop,
                           Supplier<Item> battery) {}

    private static final Chassis FIVE = new Chassis(ModComponents.FRAME_5INCH,
        ModComponents.MOTOR_2207_2450, ModComponents.PROP_5X4X3, ModComponents.BATTERY_4S_1300);
    private static final Chassis FIVE_LIGHT = new Chassis(ModComponents.FRAME_5INCH,
        ModComponents.MOTOR_2306_1700, ModComponents.PROP_5X4X2, ModComponents.BATTERY_4S_650);
    private static final Chassis SEVEN = new Chassis(ModComponents.FRAME_7INCH,
        ModComponents.MOTOR_2806_1300, ModComponents.PROP_5X4X3, ModComponents.BATTERY_6S_1100);
    private static final Chassis CINE = new Chassis(ModComponents.FRAME_CINEWHOOP,
        ModComponents.MOTOR_1404_4500, ModComponents.PROP_3X2X3, ModComponents.BATTERY_4S_650);
    private static final Chassis HEXA = new Chassis(ModComponents.FRAME_HEXA_6INCH,
        ModComponents.MOTOR_2806_1300, ModComponents.PROP_5X4X3, ModComponents.BATTERY_6S_1100);
    private static final Chassis OCTO = new Chassis(ModComponents.FRAME_OCTO_8INCH,
        ModComponents.MOTOR_3115_900, ModComponents.PROP_8X6, ModComponents.BATTERY_6S_5000);

    public static DroneAssembly roll(RandomSource rnd, Role role) {
        Chassis c = switch (role) {
            case KAMIKAZE -> pick(rnd, FIVE, FIVE_LIGHT, CINE);
            case PATROL -> pick(rnd, FIVE, SEVEN, HEXA);
            case INTERCEPTOR -> pick(rnd, FIVE_LIGHT, FIVE);
            case BOMBER -> pick(rnd, OCTO, HEXA);
            case ROCKET -> pick(rnd, SEVEN, HEXA);
            case SCOUT -> pick(rnd, FIVE_LIGHT, CINE);
            case JAMMER -> pick(rnd, SEVEN, HEXA);
        };
        Supplier<Item> module = switch (role) {
            case KAMIKAZE -> ModComponents.MODULE_WARHEAD;
            case PATROL -> pick(rnd, ModComponents.MODULE_DART_GUN, ModComponents.MODULE_DART_GUN,
                                     ModComponents.MODULE_PAYLOAD_BAY, ModComponents.MODULE_STORAGE);
            case INTERCEPTOR -> ModComponents.MODULE_WARHEAD;
            case BOMBER -> ModComponents.MODULE_PAYLOAD_BAY;
            case ROCKET -> ModComponents.MODULE_ROCKET_POD;
            case SCOUT -> ModComponents.MODULE_TARGETING;
            case JAMMER -> ModComponents.MODULE_STORAGE;
        };

        DroneAssembly a = new DroneAssembly();
        a.frame = stack(c.frame());
        a.battery = stack(c.battery());
        a.camera = stack(rnd.nextBoolean() ? ModComponents.CAMERA_STANDARD : ModComponents.CAMERA_WIDE);
        a.module = stack(module);
        a.fitStockAvionics();

        FrameSpec spec = a.frameSpec();
        int motors = spec != null ? spec.motorCount() : 4;
        for (int i = 0; i < motors; i++) {
            a.motors[i] = stack(c.motor());
            a.props[i] = stack(c.prop());
        }
        return a;
    }

    @SafeVarargs
    private static <T> T pick(RandomSource rnd, T... options) {
        return options[rnd.nextInt(options.length)];
    }

    private static ItemStack stack(Supplier<Item> holder) {
        Item item = holder.get();
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
