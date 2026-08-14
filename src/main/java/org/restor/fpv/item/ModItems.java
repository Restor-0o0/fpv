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

    public static final DeferredHolder<Item, Item> ASSEMBLED_DRONE = ITEMS.register("assembled_drone",
        () -> new DroneItem(ModEntities.COMPONENT, true));

    public static final DeferredHolder<Item, Item> REMOTE_RACING_DRONE = ITEMS.register("remote_racing_drone",
        () -> new DroneItem(ModEntities.REMOTE_RACING, false));

    public static final DeferredHolder<Item, Item> KAMIKAZE_DRONE = ITEMS.register("kamikaze_drone",
        () -> new DroneItem(ModEntities.KAMIKAZE, false));

    public static final DeferredHolder<Item, Item> MODULAR_DRONE = ITEMS.register("modular_drone",
        org.restor.fpv.assembly.ModularDroneItem::new);

    // Payload module (FPV_IDEAS §4): sneak-right-click a drone with these to load its payload
    // slot; the pilot drops one with the FIRE_PAYLOAD key. Each loaded charge adds mass.
    public static final DeferredHolder<Item, Item> DROP_CHARGE = ITEMS.register("drop_charge",
        () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, Item> PLANE = ITEMS.register("plane",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE));

    public static final DeferredHolder<Item, Item> PLANE_SCOUT = ITEMS.register("plane_scout",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_SCOUT));

    public static final DeferredHolder<Item, Item> PLANE_KAMIKAZE = ITEMS.register("plane_kamikaze",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_KAMIKAZE));

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
}
