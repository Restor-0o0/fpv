package org.restor.fpv.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.ModEntities;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Fpv.MODID);

    public static final RegistryObject<Item> CINEWHOOP = ITEMS.register("cinewhoop",
        () -> new DroneItem(ModEntities.CINEWHOOP, false));

    public static final RegistryObject<Item> RACING_DRONE = ITEMS.register("racing_drone",
        () -> new DroneItem(ModEntities.RACING, false));

    public static final RegistryObject<Item> TINY_WHOOP = ITEMS.register("tiny_whoop",
        () -> new DroneItem(ModEntities.TINY_WHOOP, false));

    public static final RegistryObject<Item> ASSEMBLED_DRONE = ITEMS.register("assembled_drone",
        () -> new DroneItem(ModEntities.COMPONENT, true));

    public static final RegistryObject<Item> REMOTE_RACING_DRONE = ITEMS.register("remote_racing_drone",
        () -> new DroneItem(ModEntities.REMOTE_RACING, false));

    public static final RegistryObject<Item> KAMIKAZE_DRONE = ITEMS.register("kamikaze_drone",
        () -> new DroneItem(ModEntities.KAMIKAZE, false));

    public static final RegistryObject<Item> MODULAR_DRONE = ITEMS.register("modular_drone",
        org.restor.fpv.assembly.ModularDroneItem::new);

    // Payload module (FPV_IDEAS §4): sneak-right-click a drone with these to load its payload
    // slot; the pilot drops one with the FIRE_PAYLOAD key. Each loaded charge adds mass.
    public static final RegistryObject<Item> DROP_CHARGE = ITEMS.register("drop_charge",
        () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> PLANE = ITEMS.register("plane",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE));

    public static final RegistryObject<Item> PLANE_SCOUT = ITEMS.register("plane_scout",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_SCOUT));

    public static final RegistryObject<Item> PLANE_KAMIKAZE = ITEMS.register("plane_kamikaze",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_KAMIKAZE));

    // Spawn eggs for the hostile NPC drones (FPV_IDEAS §3) — gunmetal shell, LED-coloured spots.
    public static final RegistryObject<Item> HOSTILE_DRONE_SPAWN_EGG = ITEMS.register("hostile_drone_spawn_egg",
        () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.HOSTILE_DRONE,
            0x2A2A30, 0xE02020, new Item.Properties()));
    public static final RegistryObject<Item> PATROL_DRONE_SPAWN_EGG = ITEMS.register("patrol_drone_spawn_egg",
        () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.PATROL_DRONE,
            0x2A2A30, 0xFF9E1A, new Item.Properties()));
    public static final RegistryObject<Item> INTERCEPTOR_DRONE_SPAWN_EGG = ITEMS.register("interceptor_drone_spawn_egg",
        () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.INTERCEPTOR_DRONE,
            0x2A2A30, 0x2673FF, new Item.Properties()));
}
