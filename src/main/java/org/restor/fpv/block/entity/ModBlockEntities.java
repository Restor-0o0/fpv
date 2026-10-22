package org.restor.fpv.block.entity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fpv.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlStationBlockEntity>> CONTROL_STATION = TYPES.register("control_station",
        () -> new BlockEntityType<>(ControlStationBlockEntity::new, ModBlocks.CONTROL_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KamikazeStationBlockEntity>> KAMIKAZE_STATION = TYPES.register("kamikaze_station",
        () -> new BlockEntityType<>(KamikazeStationBlockEntity::new, ModBlocks.KAMIKAZE_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<org.restor.fpv.assembly.AssemblyStationBlockEntity>> ASSEMBLY_STATION = TYPES.register("assembly_station",
        () -> new BlockEntityType<>(org.restor.fpv.assembly.AssemblyStationBlockEntity::new, ModBlocks.ASSEMBLY_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceStartBlockEntity>> RACE_START = TYPES.register("race_start",
        () -> new BlockEntityType<>(RaceStartBlockEntity::new, ModBlocks.RACE_START.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceGateBlockEntity>> RACE_GATE = TYPES.register("race_gate",
        () -> new BlockEntityType<>(RaceGateBlockEntity::new, ModBlocks.RACE_GATE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingStationBlockEntity>> CHARGING_STATION = TYPES.register("charging_station",
        () -> new BlockEntityType<>(ChargingStationBlockEntity::new, ModBlocks.CHARGING_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneNestBlockEntity>> DRONE_NEST = TYPES.register("drone_nest",
        () -> new BlockEntityType<>(DroneNestBlockEntity::new, ModBlocks.DRONE_NEST.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneDetectorBlockEntity>> DRONE_DETECTOR = TYPES.register("drone_detector",
        () -> new BlockEntityType<>(DroneDetectorBlockEntity::new, ModBlocks.DRONE_DETECTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartsPileBlockEntity>> PARTS_PILE = TYPES.register("parts_pile",
        () -> new BlockEntityType<>(PartsPileBlockEntity::new, ModBlocks.PARTS_PILE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuardStationBlockEntity>> GUARD_STATION = TYPES.register("guard_station",
        () -> new BlockEntityType<>(GuardStationBlockEntity::new, ModBlocks.GUARD_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneJammerBlockEntity>> DRONE_JAMMER = TYPES.register("drone_jammer",
        () -> new BlockEntityType<>(DroneJammerBlockEntity::new, ModBlocks.DRONE_JAMMER.get()));
}
