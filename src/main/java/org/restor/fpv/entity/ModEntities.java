package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Fpv.MODID);

    public static final RegistryObject<EntityType<CinewhoopDroneEntity>> CINEWHOOP = ENTITIES.register("cinewhoop",
        () -> EntityType.Builder.<CinewhoopDroneEntity>of(CinewhoopDroneEntity::new, MobCategory.MISC)
            .sized(0.6f, 0.3f)
            .clientTrackingRange(64)
            .build("cinewhoop"));

    public static final RegistryObject<EntityType<RacingDroneEntity>> RACING = ENTITIES.register("racing_drone",
        () -> EntityType.Builder.<RacingDroneEntity>of(RacingDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(64)
            .build("racing_drone"));

    public static final RegistryObject<EntityType<TinyWhoopDroneEntity>> TINY_WHOOP = ENTITIES.register("tiny_whoop",
        () -> EntityType.Builder.<TinyWhoopDroneEntity>of(TinyWhoopDroneEntity::new, MobCategory.MISC)
            .sized(0.4f, 0.2f)
            .clientTrackingRange(64)
            .build("tiny_whoop"));

    public static final RegistryObject<EntityType<RemoteRacingDroneEntity>> REMOTE_RACING = ENTITIES.register("remote_racing_drone",
        () -> EntityType.Builder.<RemoteRacingDroneEntity>of(RemoteRacingDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(64)
            .build("remote_racing_drone"));

    public static final RegistryObject<EntityType<org.restor.fpv.assembly.ModularDroneEntity>> MODULAR = ENTITIES.register("modular_drone",
        () -> EntityType.Builder.<org.restor.fpv.assembly.ModularDroneEntity>of(org.restor.fpv.assembly.ModularDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(64)
            .build("modular_drone"));

    public static final RegistryObject<EntityType<OperatorDummyEntity>> OPERATOR_DUMMY = ENTITIES.register("operator_dummy",
        () -> EntityType.Builder.<OperatorDummyEntity>of(OperatorDummyEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.3f)
            .clientTrackingRange(10)
            .build("operator_dummy"));

    public static final RegistryObject<EntityType<KamikazeDroneEntity>> KAMIKAZE = ENTITIES.register("kamikaze_drone",
        () -> EntityType.Builder.<KamikazeDroneEntity>of(KamikazeDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(512)
            .updateInterval(1)
            .build("kamikaze_drone"));

    public static final RegistryObject<EntityType<PlaneEntity>> PLANE = ENTITIES.register("plane",
        () -> EntityType.Builder.<PlaneEntity>of(PlaneEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(64)
            .build("plane"));

    public static final RegistryObject<EntityType<org.restor.fpv.assembly.ModularPlaneEntity>> MODULAR_PLANE = ENTITIES.register("modular_plane",
        () -> EntityType.Builder.<org.restor.fpv.assembly.ModularPlaneEntity>of(org.restor.fpv.assembly.ModularPlaneEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(96)
            .build("modular_plane"));

    public static final RegistryObject<EntityType<ScoutPlaneEntity>> PLANE_SCOUT = ENTITIES.register("plane_scout",
        () -> EntityType.Builder.<ScoutPlaneEntity>of(ScoutPlaneEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(64)
            .build("plane_scout"));

    public static final RegistryObject<EntityType<KamikazeWingEntity>> PLANE_KAMIKAZE = ENTITIES.register("plane_kamikaze",
        () -> EntityType.Builder.<KamikazeWingEntity>of(KamikazeWingEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(1024)
            .updateInterval(1)
            .build("plane_kamikaze"));

    public static final RegistryObject<EntityType<DeltaPlaneEntity>> PLANE_DELTA = ENTITIES.register("plane_delta",
        () -> EntityType.Builder.<DeltaPlaneEntity>of(DeltaPlaneEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(64)
            .build("plane_delta"));

    public static final RegistryObject<EntityType<GliderPlaneEntity>> PLANE_GLIDER = ENTITIES.register("plane_glider",
        () -> EntityType.Builder.<GliderPlaneEntity>of(GliderPlaneEntity::new, MobCategory.MISC)
            .sized(1.6f, 0.6f)
            .clientTrackingRange(64)
            .build("plane_glider"));

    public static final RegistryObject<EntityType<CargoPlaneEntity>> PLANE_CARGO = ENTITIES.register("plane_cargo",
        () -> EntityType.Builder.<CargoPlaneEntity>of(CargoPlaneEntity::new, MobCategory.MISC)
            .sized(1.6f, 0.7f)
            .clientTrackingRange(64)
            .build("plane_cargo"));

    public static final RegistryObject<EntityType<CarrierPlaneEntity>> PLANE_CARRIER = ENTITIES.register("plane_carrier",
        () -> EntityType.Builder.<CarrierPlaneEntity>of(CarrierPlaneEntity::new, MobCategory.MISC)
            .sized(2.2f, 0.9f)
            .clientTrackingRange(96)
            .build("plane_carrier"));

    public static final RegistryObject<EntityType<LoiterPlaneEntity>> PLANE_LOITER = ENTITIES.register("plane_loiter",
        () -> EntityType.Builder.<LoiterPlaneEntity>of(LoiterPlaneEntity::new, MobCategory.MISC)
            .sized(1.6f, 0.7f)
            .clientTrackingRange(1024)
            .updateInterval(1)
            .build("plane_loiter"));

    public static final RegistryObject<EntityType<LongRangePlaneEntity>> PLANE_LONGRANGE = ENTITIES.register("plane_longrange",
        () -> EntityType.Builder.<LongRangePlaneEntity>of(LongRangePlaneEntity::new, MobCategory.MISC)
            .sized(2.0f, 0.7f)
            .clientTrackingRange(1024)
            .updateInterval(1)
            .build("plane_longrange"));

    public static final RegistryObject<EntityType<InterceptorPlaneEntity>> PLANE_INTERCEPTOR = ENTITIES.register("plane_interceptor",
        () -> EntityType.Builder.<InterceptorPlaneEntity>of(InterceptorPlaneEntity::new, MobCategory.MISC)
            .sized(1.2f, 0.5f)
            .clientTrackingRange(512)
            .updateInterval(1)
            .build("plane_interceptor"));

    // Hostile NPC drones (FPV_IDEAS §3) — real Mobs with simplified hover AI, not DroneEntity physics.
    public static final RegistryObject<EntityType<HostileDroneEntity>> HOSTILE_DRONE = ENTITIES.register("hostile_drone",
        () -> EntityType.Builder.<HostileDroneEntity>of(HostileDroneEntity::new, MobCategory.MONSTER)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("hostile_drone"));

    public static final RegistryObject<EntityType<PatrolDroneEntity>> PATROL_DRONE = ENTITIES.register("patrol_drone",
        () -> EntityType.Builder.<PatrolDroneEntity>of(PatrolDroneEntity::new, MobCategory.MONSTER)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("patrol_drone"));

    public static final RegistryObject<EntityType<VillageDroneEntity>> VILLAGE_DRONE = ENTITIES.register("village_drone",
        () -> EntityType.Builder.<VillageDroneEntity>of(VillageDroneEntity::new, MobCategory.CREATURE)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("village_drone"));

    public static final RegistryObject<EntityType<DropChargeEntity>> DROP_CHARGE = ENTITIES.register("drop_charge",
        () -> EntityType.Builder.<DropChargeEntity>of(DropChargeEntity::new, MobCategory.MISC)
            .sized(0.3f, 0.3f)
            .clientTrackingRange(128)
            .updateInterval(2)
            .build("drop_charge"));

    public static final RegistryObject<EntityType<MiniRocketEntity>> MINI_ROCKET = ENTITIES.register("mini_rocket",
        () -> EntityType.Builder.<MiniRocketEntity>of(MiniRocketEntity::new, MobCategory.MISC)
            .sized(0.25f, 0.25f)
            .clientTrackingRange(128)
            .updateInterval(1)
            .build("mini_rocket"));

    public static final RegistryObject<EntityType<InterceptorDroneEntity>> INTERCEPTOR_DRONE = ENTITIES.register("interceptor_drone",
        () -> EntityType.Builder.<InterceptorDroneEntity>of(InterceptorDroneEntity::new, MobCategory.MONSTER)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("interceptor_drone"));

    public static final RegistryObject<EntityType<BomberDroneEntity>> BOMBER_DRONE = ENTITIES.register("bomber_drone",
        () -> EntityType.Builder.<BomberDroneEntity>of(BomberDroneEntity::new, MobCategory.MONSTER)
            .sized(1.0f, 0.45f)
            .clientTrackingRange(96)
            .build("bomber_drone"));

    public static final RegistryObject<EntityType<RocketDroneEntity>> ROCKET_DRONE = ENTITIES.register("rocket_drone",
        () -> EntityType.Builder.<RocketDroneEntity>of(RocketDroneEntity::new, MobCategory.MONSTER)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(96)
            .build("rocket_drone"));

    public static final RegistryObject<EntityType<ScoutDroneEntity>> SCOUT_DRONE = ENTITIES.register("scout_drone",
        () -> EntityType.Builder.<ScoutDroneEntity>of(ScoutDroneEntity::new, MobCategory.MONSTER)
            .sized(0.6f, 0.3f)
            .clientTrackingRange(96)
            .build("scout_drone"));

    public static final RegistryObject<EntityType<JammerDroneEntity>> JAMMER_DRONE = ENTITIES.register("jammer_drone",
        () -> EntityType.Builder.<JammerDroneEntity>of(JammerDroneEntity::new, MobCategory.MONSTER)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(96)
            .build("jammer_drone"));
}
