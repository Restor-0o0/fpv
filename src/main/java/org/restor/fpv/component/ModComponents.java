package org.restor.fpv.component;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

public class ModComponents {
    public static final DeferredRegister<Item> COMPONENTS = DeferredRegister.create(Registries.ITEM, Fpv.MODID);

    // Frames
    public static final RegistryObject<Item> FRAME_5INCH = COMPONENTS.register("frame_5inch",
        () -> new FrameItem(ComponentStats.frame("5-inch Frame", 5, 0.8f, 30)));
    public static final RegistryObject<Item> FRAME_7INCH = COMPONENTS.register("frame_7inch",
        () -> new FrameItem(ComponentStats.frame("7-inch Frame", 7, 0.7f, 45)));
    public static final RegistryObject<Item> FRAME_TINY_WHOOP = COMPONENTS.register("frame_tiny_whoop",
        () -> new FrameItem(ComponentStats.frame("Tiny Whoop Frame", 1.5f, 0.4f, 8)));
    public static final RegistryObject<Item> FRAME_CINEWHOOP = COMPONENTS.register("frame_cinewhoop",
        () -> new FrameItem(ComponentStats.frame("Cinewhoop Frame", 3.5f, 0.6f, 40)));
    public static final RegistryObject<Item> FRAME_INTERCEPTOR = COMPONENTS.register("frame_interceptor",
        () -> new FrameItem(ComponentStats.frame("Interceptor Frame", 5, 0.65f, 34)));
    public static final RegistryObject<Item> FRAME_HEXA_6INCH = COMPONENTS.register("frame_hexa_6inch",
        () -> new FrameItem(ComponentStats.frame("6-inch Hexa Frame", 6, 0.75f, 70)));
    public static final RegistryObject<Item> FRAME_OCTO_8INCH = COMPONENTS.register("frame_octo_8inch",
        () -> new FrameItem(ComponentStats.frame("8-inch Octo Frame", 8, 0.85f, 110)));

    // Fixed-wing airframes: same slots as a quad, but they need plane motors and props.
    public static final RegistryObject<Item> PLANE_FRAME_TRAINER = COMPONENTS.register("plane_frame_trainer",
        () -> new PlaneFrameItem(ComponentStats.frame("Trainer Airframe", 6, 0.7f, 900)));
    public static final RegistryObject<Item> PLANE_FRAME_DELTA = COMPONENTS.register("plane_frame_delta",
        () -> new PlaneFrameItem(ComponentStats.frame("Delta Airframe", 7, 0.75f, 1100)));
    public static final RegistryObject<Item> PLANE_FRAME_GLIDER = COMPONENTS.register("plane_frame_glider",
        () -> new PlaneFrameItem(ComponentStats.frame("Glider Airframe", 8, 0.55f, 750)));
    public static final RegistryObject<Item> PLANE_FRAME_STRIKE = COMPONENTS.register("plane_frame_strike",
        () -> new PlaneFrameItem(ComponentStats.frame("Strike Airframe", 9, 0.7f, 1600)));
    public static final RegistryObject<Item> PLANE_FRAME_CARGO = COMPONENTS.register("plane_frame_cargo",
        () -> new PlaneFrameItem(ComponentStats.frame("Cargo Airframe", 10, 0.8f, 2400)));
    public static final RegistryObject<Item> PLANE_FRAME_WING = COMPONENTS.register("plane_frame_wing",
        () -> new PlaneFrameItem(ComponentStats.frame("Interceptor Wing", 6, 0.6f, 950)));
    public static final RegistryObject<Item> PLANE_FRAME_SCOUT = COMPONENTS.register("plane_frame_scout",
        () -> new PlaneFrameItem(ComponentStats.frame("Spotter Airframe", 9, 0.5f, 1400)));
    public static final RegistryObject<Item> PLANE_FRAME_KAMIKAZE = COMPONENTS.register("plane_frame_kamikaze",
        () -> new PlaneFrameItem(ComponentStats.frame("Kamikaze Airframe", 7, 0.65f, 1050)));
    public static final RegistryObject<Item> PLANE_FRAME_CARRIER = COMPONENTS.register("plane_frame_carrier",
        () -> new PlaneFrameItem(ComponentStats.frame("Carrier Airframe", 12, 0.9f, 3400)));
    public static final RegistryObject<Item> PLANE_FRAME_LOITER = COMPONENTS.register("plane_frame_loiter",
        () -> new PlaneFrameItem(ComponentStats.frame("Loitering Airframe", 11, 0.8f, 2600)));

    // Motors
    public static final RegistryObject<Item> MOTOR_2207_2450 = COMPONENTS.register("motor_2207_2450kv",
        () -> new MotorItem(ComponentStats.motor("2207 2450KV", 2450, 1.0f, 28, 32)));
    public static final RegistryObject<Item> MOTOR_2306_1700 = COMPONENTS.register("motor_2306_1700kv",
        () -> new MotorItem(ComponentStats.motor("2306 1700KV", 1700, 0.8f, 28, 34)));
    public static final RegistryObject<Item> MOTOR_1103_8000 = COMPONENTS.register("motor_1103_8000kv",
        () -> new MotorItem(ComponentStats.motor("1103 8000KV", 8000, 0.3f, 11, 3.5f)));
    public static final RegistryObject<Item> MOTOR_1404_4500 = COMPONENTS.register("motor_1404_4500kv",
        () -> new MotorItem(ComponentStats.motor("1404 4500KV", 4500, 0.5f, 14, 10)));
    public static final RegistryObject<Item> MOTOR_2806_1300 = COMPONENTS.register("motor_2806_1300kv",
        () -> new MotorItem(ComponentStats.motor("2806 1300KV", 1300, 1.15f, 28, 42)));
    public static final RegistryObject<Item> MOTOR_3115_900 = COMPONENTS.register("motor_3115_900kv",
        () -> new MotorItem(ComponentStats.motor("3115 900KV", 900, 1.35f, 31, 68)));

    // Plane motors: slow-turning outrunners, far too heavy for a quad arm.
    public static final RegistryObject<Item> MOTOR_2814_1100 = COMPONENTS.register("motor_2814_1100kv",
        () -> new PlaneMotorItem(ComponentStats.motor("2814 1100KV", 1100, 2.1f, 28, 78)));
    public static final RegistryObject<Item> MOTOR_3548_900 = COMPONENTS.register("motor_3548_900kv",
        () -> new PlaneMotorItem(ComponentStats.motor("3548 900KV", 900, 3.4f, 35, 165)));
    public static final RegistryObject<Item> MOTOR_4250_650 = COMPONENTS.register("motor_4250_650kv",
        () -> new PlaneMotorItem(ComponentStats.motor("4250 650KV", 650, 5.0f, 42, 320)));

    // Batteries
    public static final RegistryObject<Item> BATTERY_1S_300 = COMPONENTS.register("battery_1s_300",
        () -> new BatteryItem(ComponentStats.battery("1S 300mAh", 1, 300, 25, 10)));
    public static final RegistryObject<Item> BATTERY_4S_1300 = COMPONENTS.register("battery_4s_1300",
        () -> new BatteryItem(ComponentStats.battery("4S 1300mAh", 4, 1300, 100, 180)));
    public static final RegistryObject<Item> BATTERY_6S_1100 = COMPONENTS.register("battery_6s_1100",
        () -> new BatteryItem(ComponentStats.battery("6S 1100mAh", 6, 1100, 120, 210)));
    public static final RegistryObject<Item> BATTERY_4S_650 = COMPONENTS.register("battery_4s_650",
        () -> new BatteryItem(ComponentStats.battery("4S 650mAh", 4, 650, 75, 95)));
    public static final RegistryObject<Item> BATTERY_6S_5000 = COMPONENTS.register("battery_6s_5000",
        () -> new BatteryItem(ComponentStats.battery("6S 5000mAh", 6, 5000, 100, 700)));

    // Propellers
    public static final RegistryObject<Item> PROP_5X4X3 = COMPONENTS.register("prop_5x4x3",
        () -> new PropellerItem(ComponentStats.propeller("5x4x3", 5, 4, 3, 1.0f, 5)));
    public static final RegistryObject<Item> PROP_5X4X2 = COMPONENTS.register("prop_5x4x2",
        () -> new PropellerItem(ComponentStats.propeller("5x4x2", 5, 4, 2, 0.85f, 4)));
    public static final RegistryObject<Item> PROP_3X2X3 = COMPONENTS.register("prop_3x2x3",
        () -> new PropellerItem(ComponentStats.propeller("3x2x3", 3, 2, 3, 0.9f, 2)));
    public static final RegistryObject<Item> PROP_65MM = COMPONENTS.register("prop_65mm",
        () -> new PropellerItem(ComponentStats.propeller("65mm", 2.5f, 1.5f, 4, 0.6f, 1)));

    // Plane propellers: big two-blade folders, high pitch speed.
    public static final RegistryObject<Item> PROP_8X6 = COMPONENTS.register("prop_8x6",
        () -> new PlanePropellerItem(ComponentStats.propeller("8x6", 8, 6, 2, 1.0f, 12)));
    public static final RegistryObject<Item> PROP_10X5 = COMPONENTS.register("prop_10x5",
        () -> new PlanePropellerItem(ComponentStats.propeller("10x5", 10, 5, 2, 1.05f, 20)));
    public static final RegistryObject<Item> PROP_12X6 = COMPONENTS.register("prop_12x6",
        () -> new PlanePropellerItem(ComponentStats.propeller("12x6", 12, 6, 2, 1.1f, 34)));

    // VTX
    public static final RegistryObject<Item> VTX_25MW = COMPONENTS.register("vtx_25mw",
        () -> new ComponentItem(ComponentStats.vtx("25mW VTX", 25, 0.5f, 3)));
    public static final RegistryObject<Item> VTX_200MW = COMPONENTS.register("vtx_200mw",
        () -> new ComponentItem(ComponentStats.vtx("200mW VTX", 200, 1.0f, 5)));
    public static final RegistryObject<Item> VTX_800MW = COMPONENTS.register("vtx_800mw",
        () -> new ComponentItem(ComponentStats.vtx("800mW VTX", 800, 1.5f, 7)));

    // Cameras
    public static final RegistryObject<Item> CAMERA_STANDARD = COMPONENTS.register("camera_standard",
        () -> new CameraItem(ComponentStats.camera("Standard Camera", 120, 30, 8)));
    public static final RegistryObject<Item> CAMERA_WIDE = COMPONENTS.register("camera_wide",
        () -> new CameraItem(ComponentStats.camera("Wide FPV Camera", 150, 45, 10)));

    // Flight Controllers
    public static final RegistryObject<Item> FC_F411 = COMPONENTS.register("fc_f411",
        () -> new ComponentItem(ComponentStats.fc("F411 FC", true, false, 6)));
    public static final RegistryObject<Item> FC_H7 = COMPONENTS.register("fc_h7",
        () -> new ComponentItem(ComponentStats.fc("H7 FC", true, true, 8)));

    // Modules (mount in the frame's module slot, FPV_IDEAS §4-5). Frame compatibility
    // rules live in DroneAssembly.compatibleModule.
    public static final RegistryObject<Item> MODULE_PAYLOAD_BAY = COMPONENTS.register("module_payload_bay",
        () -> new ModuleItem(ComponentStats.module("Payload Bay", 25), ModuleType.PAYLOAD_BAY));
    public static final RegistryObject<Item> MODULE_WARHEAD = COMPONENTS.register("module_warhead",
        () -> new ModuleItem(ComponentStats.module("Warhead", 150), ModuleType.WARHEAD));
    public static final RegistryObject<Item> MODULE_DART_GUN = COMPONENTS.register("module_dart_gun",
        () -> new ModuleItem(ComponentStats.module("Dart Gun", 45), ModuleType.DART_GUN));
    public static final RegistryObject<Item> MODULE_STORAGE = COMPONENTS.register("module_storage",
        () -> new ModuleItem(ComponentStats.module("Cargo Box", 60), ModuleType.STORAGE));
    public static final RegistryObject<Item> MODULE_TARGETING = COMPONENTS.register("module_targeting",
        () -> new ModuleItem(ComponentStats.module("Targeting Pod", 90), ModuleType.TARGETING));
    public static final RegistryObject<Item> MODULE_ROCKET_POD = COMPONENTS.register("module_rocket_pod",
        () -> new ModuleItem(ComponentStats.module("Rocket Pod", 110), ModuleType.ROCKET_POD));

    // Firmware chips (separate slot from the modules above). They teach the FC the
    // autonomous modes the owner cycles by right-clicking the drone.
    public static final RegistryObject<Item> FIRMWARE_COMPANION = COMPONENTS.register("firmware_companion",
        () -> new FirmwareItem(ComponentStats.firmware("Companion Firmware", 4), FirmwareType.COMPANION));
    public static final RegistryObject<Item> FIRMWARE_SCOUT = COMPONENTS.register("firmware_scout",
        () -> new FirmwareItem(ComponentStats.firmware("Scout Firmware", 6), FirmwareType.SCOUT));
    public static final RegistryObject<Item> FIRMWARE_GUARDIAN = COMPONENTS.register("firmware_guardian",
        () -> new FirmwareItem(ComponentStats.firmware("Guardian Firmware", 8), FirmwareType.GUARDIAN));
    public static final RegistryObject<Item> FIRMWARE_TRADER = COMPONENTS.register("firmware_trader",
        () -> new FirmwareItem(ComponentStats.firmware("Trader Firmware", 10), FirmwareType.TRADER));
}
