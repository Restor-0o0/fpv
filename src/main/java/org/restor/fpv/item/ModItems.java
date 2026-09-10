package org.restor.fpv.item;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.ModEntities;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, Fpv.MODID);

    public static final DeferredHolder<Item, Item> CINEWHOOP = ITEMS.register("cinewhoop",
        () -> new DroneItem(ModEntities.CINEWHOOP, false));

    public static final DeferredHolder<Item, Item> RACING_DRONE = ITEMS.register("racing_drone",
        () -> new DroneItem(ModEntities.RACING, false));

    public static final DeferredHolder<Item, Item> TINY_WHOOP = ITEMS.register("tiny_whoop",
        () -> new DroneItem(ModEntities.TINY_WHOOP, false));

    public static final DeferredHolder<Item, Item> REMOTE_RACING_DRONE = ITEMS.register("remote_racing_drone",
        () -> new DroneItem(ModEntities.REMOTE_RACING, false));

    public static final DeferredHolder<Item, Item> KAMIKAZE_DRONE = ITEMS.register("kamikaze_drone",
        () -> new DroneItem(ModEntities.KAMIKAZE, false));

    public static final DeferredHolder<Item, Item> MODULAR_DRONE = ITEMS.register("modular_drone",
        org.restor.fpv.assembly.ModularDroneItem::new);

    // Payload module (FPV_IDEAS §4): sneak-right-click a drone with these to load its payload
    // slot; the pilot drops one with the FIRE_PAYLOAD key. Each loaded charge adds mass.
    public static final DeferredHolder<Item, Item> DROP_CHARGE = ITEMS.register("drop_charge",
        DropChargeItem::new);

    // Ammunition for the rocket pod module: sneak-right-click a drone to load its rails.
    public static final DeferredHolder<Item, Item> MINI_ROCKET = ITEMS.register("mini_rocket",
        MiniRocketItem::new);

    public static final DeferredHolder<Item, Item> MODULAR_PLANE = ITEMS.register("modular_plane",
        () -> new org.restor.fpv.assembly.ModularPlaneItem());

    public static final DeferredHolder<Item, Item> DRONE_CARD = ITEMS.register("drone_card",
        org.restor.fpv.assembly.DroneCardItem::new);

    public static final DeferredHolder<Item, Item> PLANE = ITEMS.register("plane",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE));

    public static final DeferredHolder<Item, Item> PLANE_SCOUT = ITEMS.register("plane_scout",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_SCOUT));

    public static final DeferredHolder<Item, Item> PLANE_KAMIKAZE = ITEMS.register("plane_kamikaze",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_KAMIKAZE));

    public static final DeferredHolder<Item, Item> PLANE_DELTA = ITEMS.register("plane_delta",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_DELTA));

    public static final DeferredHolder<Item, Item> PLANE_GLIDER = ITEMS.register("plane_glider",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_GLIDER));

    public static final DeferredHolder<Item, Item> PLANE_CARGO = ITEMS.register("plane_cargo",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_CARGO));

    public static final DeferredHolder<Item, Item> PLANE_CARRIER = ITEMS.register("plane_carrier",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_CARRIER));

    public static final DeferredHolder<Item, Item> PLANE_SHAHED = ITEMS.register("plane_shahed",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_SHAHED));

    public static final DeferredHolder<Item, Item> PLANE_LONGRANGE = ITEMS.register("plane_longrange",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_LONGRANGE));

    public static final DeferredHolder<Item, Item> PLANE_INTERCEPTOR = ITEMS.register("plane_interceptor",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_INTERCEPTOR));

    // Warheads (BC): loaded into strike drones and carrier planes; each family trades
    // blast power against the mass the airframe has to haul.
    public static final java.util.Map<org.restor.fpv.warhead.WarheadType, DeferredHolder<Item, Item>> WARHEADS =
        new java.util.EnumMap<>(org.restor.fpv.warhead.WarheadType.class);
    static {
        for (org.restor.fpv.warhead.WarheadType type : org.restor.fpv.warhead.WarheadType.values()) {
            if (!type.loadable()) continue;
            WARHEADS.put(type, ITEMS.register(type.itemName(),
                () -> new org.restor.fpv.warhead.WarheadItem(type)));
        }
    }

    // Radio gear: an aircraft only flies while its bound transmitter is in hand, and the
    // FPV feed only reaches the pilot's eyes through the goggles.
    public static final DeferredHolder<Item, Item> RC_TRANSMITTER = ITEMS.register("rc_transmitter",
        () -> new RcTransmitterItem(false));

    public static final DeferredHolder<Item, Item> RC_TRANSMITTER_LR = ITEMS.register("rc_transmitter_lr",
        () -> new RcTransmitterItem(true));

    public static final DeferredHolder<Item, Item> FPV_GOGGLES = ITEMS.register("fpv_goggles",
        FpvGogglesItem::new);

    // Spawn eggs for the hostile NPC drones (FPV_IDEAS §3) — gunmetal shell, LED-coloured spots.
    public static final DeferredHolder<Item, Item> HOSTILE_DRONE_SPAWN_EGG = ITEMS.register("hostile_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.HOSTILE_DRONE,
            0x2A2A30, 0xE02020, new Item.Properties()));
    public static final DeferredHolder<Item, Item> PATROL_DRONE_SPAWN_EGG = ITEMS.register("patrol_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.PATROL_DRONE,
            0x2A2A30, 0xFF9E1A, new Item.Properties()));
    public static final DeferredHolder<Item, Item> INTERCEPTOR_DRONE_SPAWN_EGG = ITEMS.register("interceptor_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.INTERCEPTOR_DRONE,
            0x2A2A30, 0x2673FF, new Item.Properties()));
    public static final DeferredHolder<Item, Item> BOMBER_DRONE_SPAWN_EGG = ITEMS.register("bomber_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.BOMBER_DRONE,
            0x2A2A30, 0x8C5A1E, new Item.Properties()));
    public static final DeferredHolder<Item, Item> ROCKET_DRONE_SPAWN_EGG = ITEMS.register("rocket_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.ROCKET_DRONE,
            0x2A2A30, 0xE0561A, new Item.Properties()));
    public static final DeferredHolder<Item, Item> SCOUT_DRONE_SPAWN_EGG = ITEMS.register("scout_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.SCOUT_DRONE,
            0x2A2A30, 0x7BE05A, new Item.Properties()));
    public static final DeferredHolder<Item, Item> JAMMER_DRONE_SPAWN_EGG = ITEMS.register("jammer_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.JAMMER_DRONE,
            0x2A2A30, 0xB05AE0, new Item.Properties()));
    public static final DeferredHolder<Item, Item> VILLAGE_DRONE_SPAWN_EGG = ITEMS.register("village_drone_spawn_egg",
        () -> new DeferredSpawnEggItem(ModEntities.VILLAGE_DRONE,
            0x3A4A5A, 0x5AE0D0, new Item.Properties()));
}
