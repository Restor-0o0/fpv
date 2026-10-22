package org.restor.fpv.component;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModComponents {
    public static final DeferredRegister<Item> COMPONENTS = DeferredRegister.create(Registries.ITEM, Fpv.MODID);

    private static final Map<ComponentStats, RegistryObject<Item>> BY_STATS = new IdentityHashMap<>();

    private static RegistryObject<Item> component(String name, ComponentStats stats,
                                                  Function<ComponentStats, Item> factory) {
        RegistryObject<Item> registered = COMPONENTS.register(name, () -> factory.apply(stats));
        BY_STATS.put(stats, registered);
        return registered;
    }

    /** The item carrying these catalog stats, or null if nothing ships with them. */
    public static Supplier<? extends Item> itemFor(ComponentStats stats) {
        return stats == null ? null : BY_STATS.get(stats);
    }

    // Frames
    public static final RegistryObject<Item> FRAME_5INCH = component("frame_5inch",
        ComponentCatalog.FRAME_5INCH, FrameItem::new);
    public static final RegistryObject<Item> FRAME_7INCH = component("frame_7inch",
        ComponentCatalog.FRAME_7INCH, FrameItem::new);
    public static final RegistryObject<Item> FRAME_TINY_WHOOP = component("frame_tiny_whoop",
        ComponentCatalog.FRAME_TINY_WHOOP, FrameItem::new);
    public static final RegistryObject<Item> FRAME_CINEWHOOP = component("frame_cinewhoop",
        ComponentCatalog.FRAME_CINEWHOOP, FrameItem::new);
    public static final RegistryObject<Item> FRAME_INTERCEPTOR = component("frame_interceptor",
        ComponentCatalog.FRAME_INTERCEPTOR, FrameItem::new);
    public static final RegistryObject<Item> FRAME_HEXA_6INCH = component("frame_hexa_6inch",
        ComponentCatalog.FRAME_HEXA_6INCH, FrameItem::new);
    public static final RegistryObject<Item> FRAME_OCTO_8INCH = component("frame_octo_8inch",
        ComponentCatalog.FRAME_OCTO_8INCH, FrameItem::new);

    // Fixed-wing airframes: same slots as a quad, but they need plane motors and props.
    public static final RegistryObject<Item> PLANE_FRAME_TRAINER = component("plane_frame_trainer",
        ComponentCatalog.PLANE_FRAME_TRAINER, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_DELTA = component("plane_frame_delta",
        ComponentCatalog.PLANE_FRAME_DELTA, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_GLIDER = component("plane_frame_glider",
        ComponentCatalog.PLANE_FRAME_GLIDER, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_STRIKE = component("plane_frame_strike",
        ComponentCatalog.PLANE_FRAME_STRIKE, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_CARGO = component("plane_frame_cargo",
        ComponentCatalog.PLANE_FRAME_CARGO, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_WING = component("plane_frame_wing",
        ComponentCatalog.PLANE_FRAME_WING, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_SCOUT = component("plane_frame_scout",
        ComponentCatalog.PLANE_FRAME_SCOUT, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_KAMIKAZE = component("plane_frame_kamikaze",
        ComponentCatalog.PLANE_FRAME_KAMIKAZE, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_CARRIER = component("plane_frame_carrier",
        ComponentCatalog.PLANE_FRAME_CARRIER, PlaneFrameItem::new);
    public static final RegistryObject<Item> PLANE_FRAME_LOITER = component("plane_frame_loiter",
        ComponentCatalog.PLANE_FRAME_LOITER, PlaneFrameItem::new);

    // Motors
    public static final RegistryObject<Item> MOTOR_2207_2450 = component("motor_2207_2450kv",
        ComponentCatalog.MOTOR_2207_2450, MotorItem::new);
    public static final RegistryObject<Item> MOTOR_2306_1700 = component("motor_2306_1700kv",
        ComponentCatalog.MOTOR_2306_1700, MotorItem::new);
    public static final RegistryObject<Item> MOTOR_1103_8000 = component("motor_1103_8000kv",
        ComponentCatalog.MOTOR_1103_8000, MotorItem::new);
    public static final RegistryObject<Item> MOTOR_1404_4500 = component("motor_1404_4500kv",
        ComponentCatalog.MOTOR_1404_4500, MotorItem::new);
    public static final RegistryObject<Item> MOTOR_2806_1300 = component("motor_2806_1300kv",
        ComponentCatalog.MOTOR_2806_1300, MotorItem::new);
    public static final RegistryObject<Item> MOTOR_3115_900 = component("motor_3115_900kv",
        ComponentCatalog.MOTOR_3115_900, MotorItem::new);

    // Plane motors: slow-turning outrunners, far too heavy for a quad arm.
    public static final RegistryObject<Item> MOTOR_2814_1100 = component("motor_2814_1100kv",
        ComponentCatalog.MOTOR_2814_1100, PlaneMotorItem::new);
    public static final RegistryObject<Item> MOTOR_3548_900 = component("motor_3548_900kv",
        ComponentCatalog.MOTOR_3548_900, PlaneMotorItem::new);
    public static final RegistryObject<Item> MOTOR_4250_650 = component("motor_4250_650kv",
        ComponentCatalog.MOTOR_4250_650, PlaneMotorItem::new);

    // Batteries
    public static final RegistryObject<Item> BATTERY_1S_300 = component("battery_1s_300",
        ComponentCatalog.BATTERY_1S_300, BatteryItem::new);
    public static final RegistryObject<Item> BATTERY_4S_1300 = component("battery_4s_1300",
        ComponentCatalog.BATTERY_4S_1300, BatteryItem::new);
    public static final RegistryObject<Item> BATTERY_6S_1100 = component("battery_6s_1100",
        ComponentCatalog.BATTERY_6S_1100, BatteryItem::new);
    public static final RegistryObject<Item> BATTERY_4S_650 = component("battery_4s_650",
        ComponentCatalog.BATTERY_4S_650, BatteryItem::new);
    public static final RegistryObject<Item> BATTERY_6S_5000 = component("battery_6s_5000",
        ComponentCatalog.BATTERY_6S_5000, BatteryItem::new);

    // Propellers
    public static final RegistryObject<Item> PROP_5X4X3 = component("prop_5x4x3",
        ComponentCatalog.PROP_5X4X3, PropellerItem::new);
    public static final RegistryObject<Item> PROP_5X4X2 = component("prop_5x4x2",
        ComponentCatalog.PROP_5X4X2, PropellerItem::new);
    public static final RegistryObject<Item> PROP_3X2X3 = component("prop_3x2x3",
        ComponentCatalog.PROP_3X2X3, PropellerItem::new);
    public static final RegistryObject<Item> PROP_65MM = component("prop_65mm",
        ComponentCatalog.PROP_65MM, PropellerItem::new);

    // Plane propellers: big two-blade folders, high pitch speed.
    public static final RegistryObject<Item> PROP_8X6 = component("prop_8x6",
        ComponentCatalog.PROP_8X6, PlanePropellerItem::new);
    public static final RegistryObject<Item> PROP_10X5 = component("prop_10x5",
        ComponentCatalog.PROP_10X5, PlanePropellerItem::new);
    public static final RegistryObject<Item> PROP_12X6 = component("prop_12x6",
        ComponentCatalog.PROP_12X6, PlanePropellerItem::new);

    // VTX
    public static final RegistryObject<Item> VTX_25MW = component("vtx_25mw",
        ComponentCatalog.VTX_25MW, ComponentItem::new);
    public static final RegistryObject<Item> VTX_200MW = component("vtx_200mw",
        ComponentCatalog.VTX_200MW, ComponentItem::new);
    public static final RegistryObject<Item> VTX_800MW = component("vtx_800mw",
        ComponentCatalog.VTX_800MW, ComponentItem::new);

    // Cameras
    public static final RegistryObject<Item> CAMERA_STANDARD = component("camera_standard",
        ComponentCatalog.CAMERA_STANDARD, CameraItem::new);
    public static final RegistryObject<Item> CAMERA_WIDE = component("camera_wide",
        ComponentCatalog.CAMERA_WIDE, CameraItem::new);

    // Flight Controllers
    public static final RegistryObject<Item> FC_F411 = component("fc_f411",
        ComponentCatalog.FC_F411, ComponentItem::new);
    public static final RegistryObject<Item> FC_H7 = component("fc_h7",
        ComponentCatalog.FC_H7, ComponentItem::new);

    // Modules (mount in the frame's module slot, FPV_IDEAS §4-5). Frame compatibility
    // rules live in DroneAssembly.compatibleModule.
    public static final RegistryObject<Item> MODULE_PAYLOAD_BAY = component("module_payload_bay",
        ComponentCatalog.MODULE_PAYLOAD_BAY, s -> new ModuleItem(s, ComponentCatalog.MODULE_TYPES.get(s)));
    public static final RegistryObject<Item> MODULE_WARHEAD = component("module_warhead",
        ComponentCatalog.MODULE_WARHEAD, s -> new ModuleItem(s, ComponentCatalog.MODULE_TYPES.get(s)));
    public static final RegistryObject<Item> MODULE_DART_GUN = component("module_dart_gun",
        ComponentCatalog.MODULE_DART_GUN, s -> new ModuleItem(s, ComponentCatalog.MODULE_TYPES.get(s)));
    public static final RegistryObject<Item> MODULE_STORAGE = component("module_storage",
        ComponentCatalog.MODULE_STORAGE, s -> new ModuleItem(s, ComponentCatalog.MODULE_TYPES.get(s)));
    public static final RegistryObject<Item> MODULE_TARGETING = component("module_targeting",
        ComponentCatalog.MODULE_TARGETING, s -> new ModuleItem(s, ComponentCatalog.MODULE_TYPES.get(s)));
    public static final RegistryObject<Item> MODULE_ROCKET_POD = component("module_rocket_pod",
        ComponentCatalog.MODULE_ROCKET_POD, s -> new ModuleItem(s, ComponentCatalog.MODULE_TYPES.get(s)));

    // Firmware chips (separate slot from the modules above). They teach the FC the
    // autonomous modes the owner cycles by right-clicking the drone.
    public static final RegistryObject<Item> FIRMWARE_COMPANION = component("firmware_companion",
        ComponentCatalog.FIRMWARE_COMPANION, s -> new FirmwareItem(s, ComponentCatalog.FIRMWARE_TYPES.get(s)));
    public static final RegistryObject<Item> FIRMWARE_SCOUT = component("firmware_scout",
        ComponentCatalog.FIRMWARE_SCOUT, s -> new FirmwareItem(s, ComponentCatalog.FIRMWARE_TYPES.get(s)));
    public static final RegistryObject<Item> FIRMWARE_GUARDIAN = component("firmware_guardian",
        ComponentCatalog.FIRMWARE_GUARDIAN, s -> new FirmwareItem(s, ComponentCatalog.FIRMWARE_TYPES.get(s)));
    public static final RegistryObject<Item> FIRMWARE_TRADER = component("firmware_trader",
        ComponentCatalog.FIRMWARE_TRADER, s -> new FirmwareItem(s, ComponentCatalog.FIRMWARE_TYPES.get(s)));
}
