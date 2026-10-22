package org.restor.fpv.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.item.SpawnEggItem;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.ModEntities;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Fpv.MODID);

    public static final DeferredHolder<Item, Item> CINEWHOOP = ITEMS.registerItem("cinewhoop",
        p -> new DroneItem(p, ModEntities.CINEWHOOP, false));

    public static final DeferredHolder<Item, Item> RACING_DRONE = ITEMS.registerItem("racing_drone",
        p -> new DroneItem(p, ModEntities.RACING, false));

    public static final DeferredHolder<Item, Item> TINY_WHOOP = ITEMS.registerItem("tiny_whoop",
        p -> new DroneItem(p, ModEntities.TINY_WHOOP, false));

    public static final DeferredHolder<Item, Item> REMOTE_RACING_DRONE = ITEMS.registerItem("remote_racing_drone",
        p -> new DroneItem(p, ModEntities.REMOTE_RACING, false));

    public static final DeferredHolder<Item, Item> KAMIKAZE_DRONE = ITEMS.registerItem("kamikaze_drone",
        p -> new DroneItem(p, ModEntities.KAMIKAZE, false));

    public static final DeferredHolder<Item, Item> MODULAR_DRONE = ITEMS.registerItem("modular_drone",
        org.restor.fpv.assembly.ModularDroneItem::new);

    // Payload module (FPV_IDEAS §4): sneak-right-click a drone with these to load its payload
    // slot; the pilot drops one with the FIRE_PAYLOAD key. Each loaded charge adds mass.
    public static final DeferredHolder<Item, Item> DROP_CHARGE = ITEMS.registerItem("drop_charge",
        DropChargeItem::new);

    // Ammunition for the rocket pod module: sneak-right-click a drone to load its rails.
    public static final DeferredHolder<Item, Item> MINI_ROCKET = ITEMS.registerItem("mini_rocket",
        MiniRocketItem::new);

    public static final DeferredHolder<Item, Item> MODULAR_PLANE = ITEMS.registerItem("modular_plane",
        org.restor.fpv.assembly.ModularPlaneItem::new);

    public static final DeferredHolder<Item, Item> DRONE_CARD = ITEMS.registerItem("drone_card",
        org.restor.fpv.assembly.DroneCardItem::new);

    public static final DeferredHolder<Item, Item> PLANE = ITEMS.registerItem("plane",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE));

    public static final DeferredHolder<Item, Item> PLANE_SCOUT = ITEMS.registerItem("plane_scout",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_SCOUT));

    public static final DeferredHolder<Item, Item> PLANE_KAMIKAZE = ITEMS.registerItem("plane_kamikaze",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_KAMIKAZE));

    public static final DeferredHolder<Item, Item> PLANE_DELTA = ITEMS.registerItem("plane_delta",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_DELTA));

    public static final DeferredHolder<Item, Item> PLANE_GLIDER = ITEMS.registerItem("plane_glider",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_GLIDER));

    public static final DeferredHolder<Item, Item> PLANE_CARGO = ITEMS.registerItem("plane_cargo",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_CARGO));

    public static final DeferredHolder<Item, Item> PLANE_CARRIER = ITEMS.registerItem("plane_carrier",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_CARRIER));

    public static final DeferredHolder<Item, Item> PLANE_LOITER = ITEMS.registerItem("plane_loiter",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_LOITER));

    public static final DeferredHolder<Item, Item> PLANE_LONGRANGE = ITEMS.registerItem("plane_longrange",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_LONGRANGE));

    public static final DeferredHolder<Item, Item> PLANE_INTERCEPTOR = ITEMS.registerItem("plane_interceptor",
        p -> new PlaneItem(p, org.restor.fpv.entity.ModEntities.PLANE_INTERCEPTOR));

    // Warheads (BC): loaded into strike drones and carrier planes; each family trades
    // blast power against the mass the airframe has to haul.
    public static final java.util.Map<org.restor.fpv.warhead.WarheadType, DeferredHolder<Item, Item>> WARHEADS =
        new java.util.EnumMap<>(org.restor.fpv.warhead.WarheadType.class);
    static {
        for (org.restor.fpv.warhead.WarheadType type : org.restor.fpv.warhead.WarheadType.values()) {
            if (!type.loadable()) continue;
            WARHEADS.put(type, ITEMS.registerItem(type.itemName(),
                p -> new org.restor.fpv.warhead.WarheadItem(p, type)));
        }
    }

    // Radio gear: an aircraft only flies while its bound transmitter is in hand, and the
    // FPV feed only reaches the pilot's eyes through the goggles.
    public static final DeferredHolder<Item, Item> RC_TRANSMITTER = ITEMS.registerItem("rc_transmitter",
        p -> new RcTransmitterItem(p, false));

    public static final DeferredHolder<Item, Item> RC_TRANSMITTER_LR = ITEMS.registerItem("rc_transmitter_lr",
        p -> new RcTransmitterItem(p, true));

    public static final DeferredHolder<Item, Item> FPV_GOGGLES = ITEMS.registerItem("fpv_goggles",
        FpvGogglesItem::new);

    // Spawn eggs for the hostile NPC drones (FPV_IDEAS §3) — gunmetal shell, LED-coloured spots.
    public static final DeferredHolder<Item, Item> HOSTILE_DRONE_SPAWN_EGG = ITEMS.registerItem("hostile_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.HOSTILE_DRONE.get())));
    public static final DeferredHolder<Item, Item> PATROL_DRONE_SPAWN_EGG = ITEMS.registerItem("patrol_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.PATROL_DRONE.get())));
    public static final DeferredHolder<Item, Item> INTERCEPTOR_DRONE_SPAWN_EGG = ITEMS.registerItem("interceptor_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.INTERCEPTOR_DRONE.get())));
    public static final DeferredHolder<Item, Item> BOMBER_DRONE_SPAWN_EGG = ITEMS.registerItem("bomber_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.BOMBER_DRONE.get())));
    public static final DeferredHolder<Item, Item> ROCKET_DRONE_SPAWN_EGG = ITEMS.registerItem("rocket_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.ROCKET_DRONE.get())));
    public static final DeferredHolder<Item, Item> SCOUT_DRONE_SPAWN_EGG = ITEMS.registerItem("scout_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.SCOUT_DRONE.get())));
    public static final DeferredHolder<Item, Item> JAMMER_DRONE_SPAWN_EGG = ITEMS.registerItem("jammer_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.JAMMER_DRONE.get())));
    public static final DeferredHolder<Item, Item> VILLAGE_DRONE_SPAWN_EGG = ITEMS.registerItem("village_drone_spawn_egg",
        p -> new SpawnEggItem(p.spawnEgg(ModEntities.VILLAGE_DRONE.get())));
}
