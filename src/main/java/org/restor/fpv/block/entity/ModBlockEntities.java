package org.restor.fpv.block.entity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fpv.MODID);

    public static final RegistryObject<BlockEntityType<ControlStationBlockEntity>> CONTROL_STATION = TYPES.register("control_station",
        () -> BlockEntityType.Builder.of(ControlStationBlockEntity::new, ModBlocks.CONTROL_STATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<KamikazeStationBlockEntity>> KAMIKAZE_STATION = TYPES.register("kamikaze_station",
        () -> BlockEntityType.Builder.of(KamikazeStationBlockEntity::new, ModBlocks.KAMIKAZE_STATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<org.restor.fpv.assembly.AssemblyStationBlockEntity>> ASSEMBLY_STATION = TYPES.register("assembly_station",
        () -> BlockEntityType.Builder.of(org.restor.fpv.assembly.AssemblyStationBlockEntity::new, ModBlocks.ASSEMBLY_STATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<RaceStartBlockEntity>> RACE_START = TYPES.register("race_start",
        () -> BlockEntityType.Builder.of(RaceStartBlockEntity::new, ModBlocks.RACE_START.get()).build(null));

    public static final RegistryObject<BlockEntityType<RaceGateBlockEntity>> RACE_GATE = TYPES.register("race_gate",
        () -> BlockEntityType.Builder.of(RaceGateBlockEntity::new, ModBlocks.RACE_GATE.get()).build(null));

    public static final RegistryObject<BlockEntityType<ChargingStationBlockEntity>> CHARGING_STATION = TYPES.register("charging_station",
        () -> BlockEntityType.Builder.of(ChargingStationBlockEntity::new, ModBlocks.CHARGING_STATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<DroneNestBlockEntity>> DRONE_NEST = TYPES.register("drone_nest",
        () -> BlockEntityType.Builder.of(DroneNestBlockEntity::new, ModBlocks.DRONE_NEST.get()).build(null));

    public static final RegistryObject<BlockEntityType<DroneDetectorBlockEntity>> DRONE_DETECTOR = TYPES.register("drone_detector",
        () -> BlockEntityType.Builder.of(DroneDetectorBlockEntity::new, ModBlocks.DRONE_DETECTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<PartsPileBlockEntity>> PARTS_PILE = TYPES.register("parts_pile",
        () -> BlockEntityType.Builder.of(PartsPileBlockEntity::new, ModBlocks.PARTS_PILE.get()).build(null));

    public static final RegistryObject<BlockEntityType<GuardStationBlockEntity>> GUARD_STATION = TYPES.register("guard_station",
        () -> BlockEntityType.Builder.of(GuardStationBlockEntity::new, ModBlocks.GUARD_STATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<DroneJammerBlockEntity>> DRONE_JAMMER = TYPES.register("drone_jammer",
        () -> BlockEntityType.Builder.of(DroneJammerBlockEntity::new, ModBlocks.DRONE_JAMMER.get()).build(null));
}
