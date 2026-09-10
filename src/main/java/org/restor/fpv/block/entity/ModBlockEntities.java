package org.restor.fpv.block.entity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Fpv.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlStationBlockEntity>> CONTROL_STATION = TYPES.register("control_station",
        () -> BlockEntityType.Builder.of(ControlStationBlockEntity::new, ModBlocks.CONTROL_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KamikazeStationBlockEntity>> KAMIKAZE_STATION = TYPES.register("kamikaze_station",
        () -> BlockEntityType.Builder.of(KamikazeStationBlockEntity::new, ModBlocks.KAMIKAZE_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<org.restor.fpv.assembly.AssemblyStationBlockEntity>> ASSEMBLY_STATION = TYPES.register("assembly_station",
        () -> BlockEntityType.Builder.of(org.restor.fpv.assembly.AssemblyStationBlockEntity::new, ModBlocks.ASSEMBLY_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceStartBlockEntity>> RACE_START = TYPES.register("race_start",
        () -> BlockEntityType.Builder.of(RaceStartBlockEntity::new, ModBlocks.RACE_START.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceGateBlockEntity>> RACE_GATE = TYPES.register("race_gate",
        () -> BlockEntityType.Builder.of(RaceGateBlockEntity::new, ModBlocks.RACE_GATE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingStationBlockEntity>> CHARGING_STATION = TYPES.register("charging_station",
        () -> BlockEntityType.Builder.of(ChargingStationBlockEntity::new, ModBlocks.CHARGING_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneNestBlockEntity>> DRONE_NEST = TYPES.register("drone_nest",
        () -> BlockEntityType.Builder.of(DroneNestBlockEntity::new, ModBlocks.DRONE_NEST.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneDetectorBlockEntity>> DRONE_DETECTOR = TYPES.register("drone_detector",
        () -> BlockEntityType.Builder.of(DroneDetectorBlockEntity::new, ModBlocks.DRONE_DETECTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartsPileBlockEntity>> PARTS_PILE = TYPES.register("parts_pile",
        () -> BlockEntityType.Builder.of(PartsPileBlockEntity::new, ModBlocks.PARTS_PILE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuardStationBlockEntity>> GUARD_STATION = TYPES.register("guard_station",
        () -> BlockEntityType.Builder.of(GuardStationBlockEntity::new, ModBlocks.GUARD_STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneJammerBlockEntity>> DRONE_JAMMER = TYPES.register("drone_jammer",
        () -> BlockEntityType.Builder.of(DroneJammerBlockEntity::new, ModBlocks.DRONE_JAMMER.get()).build(null));
}
