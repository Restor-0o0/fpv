package org.restor.fpv.component;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.Fpv;

public class ModComponents {
    public static final DeferredRegister<Item> COMPONENTS = DeferredRegister.create(BuiltInRegistries.ITEM, Fpv.MODID);

    // Frames
    public static final DeferredHolder<Item, Item> FRAME_5INCH = COMPONENTS.register("frame_5inch",
        () -> new FrameItem(ComponentStats.frame("5-inch Frame", 5, 0.8f, 30)));
    public static final DeferredHolder<Item, Item> FRAME_7INCH = COMPONENTS.register("frame_7inch",
        () -> new FrameItem(ComponentStats.frame("7-inch Frame", 7, 0.7f, 45)));
    public static final DeferredHolder<Item, Item> FRAME_TINY_WHOOP = COMPONENTS.register("frame_tiny_whoop",
        () -> new FrameItem(ComponentStats.frame("Tiny Whoop Frame", 1.5f, 0.4f, 8)));
    public static final DeferredHolder<Item, Item> FRAME_CINEWHOOP = COMPONENTS.register("frame_cinewhoop",
        () -> new FrameItem(ComponentStats.frame("Cinewhoop Frame", 3.5f, 0.6f, 40)));
    public static final DeferredHolder<Item, Item> FRAME_HEXA_6INCH = COMPONENTS.register("frame_hexa_6inch",
        () -> new FrameItem(ComponentStats.frame("6-inch Hexa Frame", 6, 0.75f, 70)));
    public static final DeferredHolder<Item, Item> FRAME_OCTO_8INCH = COMPONENTS.register("frame_octo_8inch",
        () -> new FrameItem(ComponentStats.frame("8-inch Octo Frame", 8, 0.85f, 110)));

    // Motors
    public static final DeferredHolder<Item, Item> MOTOR_2207_2450 = COMPONENTS.register("motor_2207_2450kv",
        () -> new MotorItem(ComponentStats.motor("2207 2450KV", 2450, 1.0f, 28, 32)));
    public static final DeferredHolder<Item, Item> MOTOR_2306_1700 = COMPONENTS.register("motor_2306_1700kv",
        () -> new MotorItem(ComponentStats.motor("2306 1700KV", 1700, 0.8f, 28, 34)));
    public static final DeferredHolder<Item, Item> MOTOR_1103_8000 = COMPONENTS.register("motor_1103_8000kv",
        () -> new MotorItem(ComponentStats.motor("1103 8000KV", 8000, 0.3f, 11, 5)));
    public static final DeferredHolder<Item, Item> MOTOR_1404_4500 = COMPONENTS.register("motor_1404_4500kv",
        () -> new MotorItem(ComponentStats.motor("1404 4500KV", 4500, 0.5f, 14, 10)));
    public static final DeferredHolder<Item, Item> MOTOR_2806_1300 = COMPONENTS.register("motor_2806_1300kv",
        () -> new MotorItem(ComponentStats.motor("2806 1300KV", 1300, 1.15f, 28, 42)));
    public static final DeferredHolder<Item, Item> MOTOR_3115_900 = COMPONENTS.register("motor_3115_900kv",
        () -> new MotorItem(ComponentStats.motor("3115 900KV", 900, 1.35f, 31, 68)));

    // Batteries
    public static final DeferredHolder<Item, Item> BATTERY_1S_300 = COMPONENTS.register("battery_1s_300",
        () -> new BatteryItem(ComponentStats.battery("1S 300mAh", 1, 300, 25, 10)));
    public static final DeferredHolder<Item, Item> BATTERY_4S_1300 = COMPONENTS.register("battery_4s_1300",
        () -> new BatteryItem(ComponentStats.battery("4S 1300mAh", 4, 1300, 100, 180)));
    public static final DeferredHolder<Item, Item> BATTERY_6S_1100 = COMPONENTS.register("battery_6s_1100",
        () -> new BatteryItem(ComponentStats.battery("6S 1100mAh", 6, 1100, 120, 210)));
    public static final DeferredHolder<Item, Item> BATTERY_4S_650 = COMPONENTS.register("battery_4s_650",
        () -> new BatteryItem(ComponentStats.battery("4S 650mAh", 4, 650, 75, 95)));

    // Propellers
    public static final DeferredHolder<Item, Item> PROP_5X4X3 = COMPONENTS.register("prop_5x4x3",
        () -> new PropellerItem(ComponentStats.propeller("5x4x3", 5, 4, 3, 1.0f, 5)));
    public static final DeferredHolder<Item, Item> PROP_5X4X2 = COMPONENTS.register("prop_5x4x2",
        () -> new PropellerItem(ComponentStats.propeller("5x4x2", 5, 4, 2, 0.85f, 4)));
    public static final DeferredHolder<Item, Item> PROP_3X2X3 = COMPONENTS.register("prop_3x2x3",
        () -> new PropellerItem(ComponentStats.propeller("3x2x3", 3, 2, 3, 0.9f, 2)));
    public static final DeferredHolder<Item, Item> PROP_65MM = COMPONENTS.register("prop_65mm",
        () -> new PropellerItem(ComponentStats.propeller("65mm", 2.5f, 1.5f, 4, 0.6f, 1)));

    // VTX
    public static final DeferredHolder<Item, Item> VTX_25MW = COMPONENTS.register("vtx_25mw",
        () -> new ComponentItem(ComponentStats.vtx("25mW VTX", 25, 0.5f, 3)));
    public static final DeferredHolder<Item, Item> VTX_200MW = COMPONENTS.register("vtx_200mw",
        () -> new ComponentItem(ComponentStats.vtx("200mW VTX", 200, 1.0f, 5)));
    public static final DeferredHolder<Item, Item> VTX_800MW = COMPONENTS.register("vtx_800mw",
        () -> new ComponentItem(ComponentStats.vtx("800mW VTX", 800, 1.5f, 7)));

    // Cameras
    public static final DeferredHolder<Item, Item> CAMERA_STANDARD = COMPONENTS.register("camera_standard",
        () -> new CameraItem(ComponentStats.camera("Standard Camera", 120, 30, 8)));
    public static final DeferredHolder<Item, Item> CAMERA_WIDE = COMPONENTS.register("camera_wide",
        () -> new CameraItem(ComponentStats.camera("Wide FPV Camera", 150, 45, 10)));

    // Flight Controllers
    public static final DeferredHolder<Item, Item> FC_F411 = COMPONENTS.register("fc_f411",
        () -> new ComponentItem(ComponentStats.fc("F411 FC", true, false, 6)));
    public static final DeferredHolder<Item, Item> FC_H7 = COMPONENTS.register("fc_h7",
        () -> new ComponentItem(ComponentStats.fc("H7 FC", true, true, 8)));

    // Modules (mount in the frame's module slot, FPV_IDEAS §4-5). Frame compatibility
    // rules live in DroneAssembly.compatibleModule.
    public static final DeferredHolder<Item, Item> MODULE_PAYLOAD_BAY = COMPONENTS.register("module_payload_bay",
        () -> new ModuleItem(ComponentStats.module("Payload Bay", 25), ModuleType.PAYLOAD_BAY));
    public static final DeferredHolder<Item, Item> MODULE_WARHEAD = COMPONENTS.register("module_warhead",
        () -> new ModuleItem(ComponentStats.module("Warhead", 150), ModuleType.WARHEAD));
    public static final DeferredHolder<Item, Item> MODULE_DART_GUN = COMPONENTS.register("module_dart_gun",
        () -> new ModuleItem(ComponentStats.module("Dart Gun", 45), ModuleType.DART_GUN));
    public static final DeferredHolder<Item, Item> MODULE_STORAGE = COMPONENTS.register("module_storage",
        () -> new ModuleItem(ComponentStats.module("Cargo Box", 60), ModuleType.STORAGE));
}
