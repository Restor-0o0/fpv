package org.restor.fpv.client;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.component.ModComponents;

import java.util.List;

/**
 * Client-side state for the "dev drone" the player configures in the settings dev tab:
 * one selected component per slot (a single motor + prop type is applied to every frame arm).
 * Builds a {@link DroneAssembly} so all derived flight stats come from the SAME code the real
 * modular drone uses ({@link DroneAssembly#buildDroneData()} / totalMassKg / totalThrustN).
 * Persists only for the session (static) — enough for tuning/testing.
 */
public final class DevDroneBuild {
    private DevDroneBuild() {}

    // Available modules per slot (the ones already registered in ModComponents).
    public static final List<DeferredHolder<Item, Item>> FRAMES = List.of(
        ModComponents.FRAME_TINY_WHOOP, ModComponents.FRAME_CINEWHOOP,
        ModComponents.FRAME_5INCH, ModComponents.FRAME_7INCH,
        ModComponents.FRAME_HEXA_6INCH, ModComponents.FRAME_OCTO_8INCH);
    public static final List<DeferredHolder<Item, Item>> MOTORS = List.of(
        ModComponents.MOTOR_1103_8000, ModComponents.MOTOR_1404_4500,
        ModComponents.MOTOR_2207_2450, ModComponents.MOTOR_2306_1700,
        ModComponents.MOTOR_2806_1300, ModComponents.MOTOR_3115_900);
    public static final List<DeferredHolder<Item, Item>> PROPS = List.of(
        ModComponents.PROP_65MM, ModComponents.PROP_3X2X3,
        ModComponents.PROP_5X4X2, ModComponents.PROP_5X4X3);
    public static final List<DeferredHolder<Item, Item>> BATTERIES = List.of(
        ModComponents.BATTERY_1S_300, ModComponents.BATTERY_4S_650,
        ModComponents.BATTERY_4S_1300, ModComponents.BATTERY_6S_1100);
    public static final List<DeferredHolder<Item, Item>> CAMERAS = List.of(
        ModComponents.CAMERA_STANDARD, ModComponents.CAMERA_WIDE);

    // Slots: 0 frame, 1 motor, 2 prop, 3 battery, 4 camera.
    public static final int SLOTS = 5;
    private static final int[] idx = {2, 2, 3, 2, 0}; // default ≈ a 5" quad

    private static List<DeferredHolder<Item, Item>> list(int slot) {
        return switch (slot) {
            case 0 -> FRAMES;
            case 1 -> MOTORS;
            case 2 -> PROPS;
            case 3 -> BATTERIES;
            default -> CAMERAS;
        };
    }

    public static String slotLabel(int slot) {
        return switch (slot) {
            case 0 -> "Frame";
            case 1 -> "Motor";
            case 2 -> "Prop";
            case 3 -> "Battery";
            default -> "Camera";
        };
    }

    public static Item current(int slot) { return list(slot).get(idx[slot]).get(); }

    public static String currentName(int slot) {
        return new ItemStack(current(slot)).getHoverName().getString();
    }

    public static void cycle(int slot, int dir) {
        int n = list(slot).size();
        idx[slot] = ((idx[slot] + dir) % n + n) % n;
    }

    /** One of the stock drones as a starting template. */
    public static void applyPreset(String name) {
        switch (name) {
            case "tiny"  -> set(0, 0, 0, 0, 0); // tiny whoop / 1103 / 65mm / 1S / std
            case "cine"  -> set(1, 1, 1, 1, 1); // cinewhoop / 1404 / 3x2x3 / 4S650 / wide
            case "racing"-> set(2, 2, 3, 2, 0); // 5" / 2207 / 5x4x3 / 4S1300 / std
            case "seven" -> set(3, 4, 3, 3, 0); // 7" / 2806 / 5x4x3 / 6S / std
        }
    }

    private static void set(int f, int m, int p, int b, int c) {
        idx[0] = f; idx[1] = m; idx[2] = p; idx[3] = b; idx[4] = c;
    }

    /** Build the assembly: chosen motor+prop filled into every frame arm. */
    public static DroneAssembly buildAssembly() {
        DroneAssembly a = new DroneAssembly();
        a.frame = new ItemStack(current(0));
        a.battery = new ItemStack(current(3));
        a.camera = new ItemStack(current(4));
        FrameSpec spec = a.frameSpec();
        int n = spec != null ? spec.motorCount() : 4;
        for (int i = 0; i < n && i < DroneAssembly.MAX_MOTORS; i++) {
            a.motors[i] = new ItemStack(current(1));
            a.props[i] = new ItemStack(current(2));
        }
        return a;
    }
}
