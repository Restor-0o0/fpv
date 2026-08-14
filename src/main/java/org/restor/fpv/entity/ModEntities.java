package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.Fpv;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Fpv.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CinewhoopDroneEntity>> CINEWHOOP = ENTITIES.register("cinewhoop",
        () -> EntityType.Builder.<CinewhoopDroneEntity>of(CinewhoopDroneEntity::new, MobCategory.MISC)
            .sized(0.6f, 0.3f)
            .clientTrackingRange(64)
            .build("cinewhoop"));

    public static final DeferredHolder<EntityType<?>, EntityType<RacingDroneEntity>> RACING = ENTITIES.register("racing_drone",
        () -> EntityType.Builder.<RacingDroneEntity>of(RacingDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(64)
            .build("racing_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<TinyWhoopDroneEntity>> TINY_WHOOP = ENTITIES.register("tiny_whoop",
        () -> EntityType.Builder.<TinyWhoopDroneEntity>of(TinyWhoopDroneEntity::new, MobCategory.MISC)
            .sized(0.4f, 0.2f)
            .clientTrackingRange(64)
            .build("tiny_whoop"));

    public static final DeferredHolder<EntityType<?>, EntityType<ComponentDroneEntity>> COMPONENT = ENTITIES.register("component_drone",
        () -> EntityType.Builder.<ComponentDroneEntity>of(ComponentDroneEntity::new, MobCategory.MISC)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("component_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<RemoteRacingDroneEntity>> REMOTE_RACING = ENTITIES.register("remote_racing_drone",
        () -> EntityType.Builder.<RemoteRacingDroneEntity>of(RemoteRacingDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(64)
            .build("remote_racing_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<org.restor.fpv.assembly.ModularDroneEntity>> MODULAR = ENTITIES.register("modular_drone",
        () -> EntityType.Builder.<org.restor.fpv.assembly.ModularDroneEntity>of(org.restor.fpv.assembly.ModularDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(64)
            .build("modular_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<OperatorDummyEntity>> OPERATOR_DUMMY = ENTITIES.register("operator_dummy",
        () -> EntityType.Builder.<OperatorDummyEntity>of(OperatorDummyEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.3f)
            .clientTrackingRange(10)
            .build("operator_dummy"));

    public static final DeferredHolder<EntityType<?>, EntityType<KamikazeDroneEntity>> KAMIKAZE = ENTITIES.register("kamikaze_drone",
        () -> EntityType.Builder.<KamikazeDroneEntity>of(KamikazeDroneEntity::new, MobCategory.MISC)
            .sized(0.8f, 0.4f)
            .clientTrackingRange(512)
            .updateInterval(1)
            .build("kamikaze_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<PlaneEntity>> PLANE = ENTITIES.register("plane",
        () -> EntityType.Builder.<PlaneEntity>of(PlaneEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(64)
            .build("plane"));

    public static final DeferredHolder<EntityType<?>, EntityType<ScoutPlaneEntity>> PLANE_SCOUT = ENTITIES.register("plane_scout",
        () -> EntityType.Builder.<ScoutPlaneEntity>of(ScoutPlaneEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(64)
            .build("plane_scout"));

    public static final DeferredHolder<EntityType<?>, EntityType<KamikazeWingEntity>> PLANE_KAMIKAZE = ENTITIES.register("plane_kamikaze",
        () -> EntityType.Builder.<KamikazeWingEntity>of(KamikazeWingEntity::new, MobCategory.MISC)
            .sized(1.4f, 0.6f)
            .clientTrackingRange(1024)
            .updateInterval(1)
            .build("plane_kamikaze"));

    // Hostile NPC drones (FPV_IDEAS §3) — real Mobs with simplified hover AI, not DroneEntity physics.
    public static final DeferredHolder<EntityType<?>, EntityType<HostileDroneEntity>> HOSTILE_DRONE = ENTITIES.register("hostile_drone",
        () -> EntityType.Builder.<HostileDroneEntity>of(HostileDroneEntity::new, MobCategory.MONSTER)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("hostile_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<PatrolDroneEntity>> PATROL_DRONE = ENTITIES.register("patrol_drone",
        () -> EntityType.Builder.<PatrolDroneEntity>of(PatrolDroneEntity::new, MobCategory.MONSTER)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("patrol_drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<InterceptorDroneEntity>> INTERCEPTOR_DRONE = ENTITIES.register("interceptor_drone",
        () -> EntityType.Builder.<InterceptorDroneEntity>of(InterceptorDroneEntity::new, MobCategory.MONSTER)
            .sized(0.7f, 0.35f)
            .clientTrackingRange(64)
            .build("interceptor_drone"));
}
