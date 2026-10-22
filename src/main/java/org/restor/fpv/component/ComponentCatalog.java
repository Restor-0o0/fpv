package org.restor.fpv.component;

import java.util.List;
import java.util.Map;

/**
 * Numbers of every component the mod ships, apart from the items that carry them.
 * ModComponents turns each entry into a registered item; ComponentFit and
 * DroneAssemblyValidator read the same entries to decide what fits and what flies.
 */
public final class ComponentCatalog {
    // Multirotor frames
    public static final ComponentStats FRAME_5INCH = ComponentStats.frame("5-inch Frame", 5, 0.8f, 30);
    public static final ComponentStats FRAME_7INCH = ComponentStats.frame("7-inch Frame", 7, 0.7f, 45);
    public static final ComponentStats FRAME_TINY_WHOOP = ComponentStats.frame("Tiny Whoop Frame", 1.5f, 0.4f, 8);
    public static final ComponentStats FRAME_CINEWHOOP = ComponentStats.frame("Cinewhoop Frame", 3.5f, 0.6f, 40);
    public static final ComponentStats FRAME_INTERCEPTOR = ComponentStats.frame("Interceptor Frame", 5, 0.65f, 34);
    public static final ComponentStats FRAME_HEXA_6INCH = ComponentStats.frame("6-inch Hexa Frame", 6, 0.75f, 70);
    public static final ComponentStats FRAME_OCTO_8INCH = ComponentStats.frame("8-inch Octo Frame", 8, 0.85f, 110);

    // Fixed-wing airframes
    public static final ComponentStats PLANE_FRAME_TRAINER = ComponentStats.frame("Trainer Airframe", 6, 0.7f, 900);
    public static final ComponentStats PLANE_FRAME_DELTA = ComponentStats.frame("Delta Airframe", 7, 0.75f, 1100);
    public static final ComponentStats PLANE_FRAME_GLIDER = ComponentStats.frame("Glider Airframe", 8, 0.55f, 750);
    public static final ComponentStats PLANE_FRAME_STRIKE = ComponentStats.frame("Strike Airframe", 9, 0.7f, 1600);
    public static final ComponentStats PLANE_FRAME_CARGO = ComponentStats.frame("Cargo Airframe", 10, 0.8f, 2400);
    public static final ComponentStats PLANE_FRAME_WING = ComponentStats.frame("Interceptor Wing", 6, 0.6f, 950);
    public static final ComponentStats PLANE_FRAME_SCOUT = ComponentStats.frame("Spotter Airframe", 9, 0.5f, 1400);
    public static final ComponentStats PLANE_FRAME_KAMIKAZE = ComponentStats.frame("Kamikaze Airframe", 7, 0.65f, 1050);
    public static final ComponentStats PLANE_FRAME_CARRIER = ComponentStats.frame("Carrier Airframe", 12, 0.9f, 3400);
    public static final ComponentStats PLANE_FRAME_LOITER = ComponentStats.frame("Loitering Airframe", 11, 0.8f, 2600);

    // Motors
    public static final ComponentStats MOTOR_2207_2450 = ComponentStats.motor("2207 2450KV", 2450, 1.0f, 28, 32);
    public static final ComponentStats MOTOR_2306_1700 = ComponentStats.motor("2306 1700KV", 1700, 0.8f, 28, 34);
    public static final ComponentStats MOTOR_1103_8000 = ComponentStats.motor("1103 8000KV", 8000, 0.3f, 11, 3.5f);
    public static final ComponentStats MOTOR_1404_4500 = ComponentStats.motor("1404 4500KV", 4500, 0.5f, 14, 10);
    public static final ComponentStats MOTOR_2806_1300 = ComponentStats.motor("2806 1300KV", 1300, 1.15f, 28, 42);
    public static final ComponentStats MOTOR_3115_900 = ComponentStats.motor("3115 900KV", 900, 1.35f, 31, 68);
    public static final ComponentStats MOTOR_2814_1100 = ComponentStats.motor("2814 1100KV", 1100, 2.1f, 28, 78);
    public static final ComponentStats MOTOR_3548_900 = ComponentStats.motor("3548 900KV", 900, 3.4f, 35, 165);
    public static final ComponentStats MOTOR_4250_650 = ComponentStats.motor("4250 650KV", 650, 5.0f, 42, 320);

    // Batteries
    public static final ComponentStats BATTERY_1S_300 = ComponentStats.battery("1S 300mAh", 1, 300, 25, 10);
    public static final ComponentStats BATTERY_4S_1300 = ComponentStats.battery("4S 1300mAh", 4, 1300, 100, 180);
    public static final ComponentStats BATTERY_6S_1100 = ComponentStats.battery("6S 1100mAh", 6, 1100, 120, 210);
    public static final ComponentStats BATTERY_4S_650 = ComponentStats.battery("4S 650mAh", 4, 650, 75, 95);
    public static final ComponentStats BATTERY_6S_5000 = ComponentStats.battery("6S 5000mAh", 6, 5000, 100, 700);

    // Propellers
    public static final ComponentStats PROP_5X4X3 = ComponentStats.propeller("5x4x3", 5, 4, 3, 1.0f, 5);
    public static final ComponentStats PROP_5X4X2 = ComponentStats.propeller("5x4x2", 5, 4, 2, 0.85f, 4);
    public static final ComponentStats PROP_3X2X3 = ComponentStats.propeller("3x2x3", 3, 2, 3, 0.9f, 2);
    public static final ComponentStats PROP_65MM = ComponentStats.propeller("65mm", 2.5f, 1.5f, 4, 0.6f, 1);
    public static final ComponentStats PROP_8X6 = ComponentStats.propeller("8x6", 8, 6, 2, 1.0f, 12);
    public static final ComponentStats PROP_10X5 = ComponentStats.propeller("10x5", 10, 5, 2, 1.05f, 20);
    public static final ComponentStats PROP_12X6 = ComponentStats.propeller("12x6", 12, 6, 2, 1.1f, 34);

    // Video transmitters
    public static final ComponentStats VTX_25MW = ComponentStats.vtx("25mW VTX", 25, 0.5f, 3);
    public static final ComponentStats VTX_200MW = ComponentStats.vtx("200mW VTX", 200, 1.0f, 5);
    public static final ComponentStats VTX_800MW = ComponentStats.vtx("800mW VTX", 800, 1.5f, 7);

    // Cameras
    public static final ComponentStats CAMERA_STANDARD = ComponentStats.camera("Standard Camera", 120, 30, 8);
    public static final ComponentStats CAMERA_WIDE = ComponentStats.camera("Wide FPV Camera", 150, 45, 10);

    // Flight controllers
    public static final ComponentStats FC_F411 = ComponentStats.fc("F411 FC", true, false, 6);
    public static final ComponentStats FC_H7 = ComponentStats.fc("H7 FC", true, true, 8);

    // Payload / utility modules
    public static final ComponentStats MODULE_PAYLOAD_BAY = ComponentStats.module("Payload Bay", 25);
    public static final ComponentStats MODULE_WARHEAD = ComponentStats.module("Warhead", 150);
    public static final ComponentStats MODULE_DART_GUN = ComponentStats.module("Dart Gun", 45);
    public static final ComponentStats MODULE_STORAGE = ComponentStats.module("Cargo Box", 60);
    public static final ComponentStats MODULE_TARGETING = ComponentStats.module("Targeting Pod", 90);
    public static final ComponentStats MODULE_ROCKET_POD = ComponentStats.module("Rocket Pod", 110);

    // Firmware chips
    public static final ComponentStats FIRMWARE_COMPANION = ComponentStats.firmware("Companion Firmware", 4);
    public static final ComponentStats FIRMWARE_SCOUT = ComponentStats.firmware("Scout Firmware", 6);
    public static final ComponentStats FIRMWARE_GUARDIAN = ComponentStats.firmware("Guardian Firmware", 8);
    public static final ComponentStats FIRMWARE_TRADER = ComponentStats.firmware("Trader Firmware", 10);

    public static final List<ComponentStats> FRAMES = List.of(
        FRAME_5INCH, FRAME_7INCH, FRAME_TINY_WHOOP, FRAME_CINEWHOOP, FRAME_INTERCEPTOR,
        FRAME_HEXA_6INCH, FRAME_OCTO_8INCH, PLANE_FRAME_TRAINER, PLANE_FRAME_DELTA, PLANE_FRAME_GLIDER,
        PLANE_FRAME_STRIKE, PLANE_FRAME_CARGO, PLANE_FRAME_WING, PLANE_FRAME_SCOUT,
        PLANE_FRAME_KAMIKAZE, PLANE_FRAME_CARRIER, PLANE_FRAME_LOITER);

    public static final List<ComponentStats> MOTORS = List.of(
        MOTOR_2207_2450, MOTOR_2306_1700, MOTOR_1103_8000, MOTOR_1404_4500, MOTOR_2806_1300,
        MOTOR_3115_900, MOTOR_2814_1100, MOTOR_3548_900, MOTOR_4250_650);

    public static final List<ComponentStats> PROPELLERS = List.of(
        PROP_5X4X3, PROP_5X4X2, PROP_3X2X3, PROP_65MM, PROP_8X6, PROP_10X5, PROP_12X6);

    public static final List<ComponentStats> BATTERIES = List.of(
        BATTERY_1S_300, BATTERY_4S_1300, BATTERY_6S_1100, BATTERY_4S_650, BATTERY_6S_5000);

    public static final List<ComponentStats> CAMERAS = List.of(
        CAMERA_STANDARD, CAMERA_WIDE);

    public static final Map<ComponentStats, ModuleType> MODULE_TYPES = Map.of(
        MODULE_PAYLOAD_BAY, ModuleType.PAYLOAD_BAY,
        MODULE_WARHEAD, ModuleType.WARHEAD,
        MODULE_DART_GUN, ModuleType.DART_GUN,
        MODULE_STORAGE, ModuleType.STORAGE,
        MODULE_TARGETING, ModuleType.TARGETING,
        MODULE_ROCKET_POD, ModuleType.ROCKET_POD);

    public static final Map<ComponentStats, FirmwareType> FIRMWARE_TYPES = Map.of(
        FIRMWARE_COMPANION, FirmwareType.COMPANION,
        FIRMWARE_SCOUT, FirmwareType.SCOUT,
        FIRMWARE_GUARDIAN, FirmwareType.GUARDIAN,
        FIRMWARE_TRADER, FirmwareType.TRADER);

    /** Flight controller a build leaves the assembly station with when none was fitted. */
    public static ComponentStats stockFc(float frameSize) {
        return frameSize >= 6f ? FC_H7 : FC_F411;
    }

    /** Video transmitter a build leaves the assembly station with when none was fitted. */
    public static ComponentStats stockVtx(float frameSize) {
        return frameSize >= 6f ? VTX_800MW : frameSize >= 3f ? VTX_200MW : VTX_25MW;
    }

    private ComponentCatalog() {}
}
