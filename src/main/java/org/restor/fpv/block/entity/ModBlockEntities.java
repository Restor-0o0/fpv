package org.restor.fpv.block.entity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Fpv.MODID);

    public static final RegistryObject<BlockEntityType<WorkbenchBlockEntity>> WORKBENCH = TYPES.register("workbench",
        () -> BlockEntityType.Builder.of(WorkbenchBlockEntity::new, ModBlocks.WORKBENCH.get()).build(null));

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

    public static final RegistryObject<BlockEntityType<DroneNestBlockEntity>> DRONE_NEST = TYPES.register("drone_nest",
        () -> BlockEntityType.Builder.of(DroneNestBlockEntity::new, ModBlocks.DRONE_NEST.get()).build(null));
}
