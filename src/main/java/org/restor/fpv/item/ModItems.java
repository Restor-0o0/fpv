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

    public static final RegistryObject<Item> PLANE = ITEMS.register("plane",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE));

    public static final RegistryObject<Item> PLANE_SCOUT = ITEMS.register("plane_scout",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_SCOUT));

    public static final RegistryObject<Item> PLANE_KAMIKAZE = ITEMS.register("plane_kamikaze",
        () -> new PlaneItem(org.restor.fpv.entity.ModEntities.PLANE_KAMIKAZE));
}
