package org.restor.fpv.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Fpv.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(Registries.ITEM, Fpv.MODID);

    public static final RegistryObject<Block> CONTROL_STATION = register("control_station",
        () -> new ControlStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> KAMIKAZE_STATION = register("kamikaze_station",
        () -> new KamikazeStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> ASSEMBLY_STATION = register("assembly_station",
        () -> new org.restor.fpv.assembly.AssemblyStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> RACE_START = register("race_start",
        () -> new RaceStartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> RACE_FINISH = register("race_finish",
        () -> new RaceFinishBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> RACE_GATE = register("race_gate",
        () -> new RaceGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> CHARGING_STATION = register("charging_station",
        () -> new ChargingStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
            .lightLevel(state -> state.getValue(ChargingStationBlock.CHARGING) ? 7 : 0)));

    public static final RegistryObject<Block> DRONE_NEST = register("drone_nest",
        () -> new DroneNestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistryObject<Block> DRONE_DETECTOR = register("drone_detector",
        () -> new DroneDetectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
            .lightLevel(state -> state.getValue(DroneDetectorBlock.DETECTED) ? 8 : 0)));

    public static final RegistryObject<Block> DRONE_JAMMER = register("drone_jammer",
        () -> new DroneJammerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
            .lightLevel(state -> state.getValue(DroneJammerBlock.POWERED) ? 5 : 0)));

    public static final RegistryObject<Block> GUARD_STATION = register("guard_station",
        () -> new GuardStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    // No block item: a pile only exists where a component was set down.
    public static final RegistryObject<PartsPileBlock> PARTS_PILE = BLOCKS.register("parts_pile",
        () -> new PartsPileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.2f).sound(SoundType.METAL).noOcclusion().instabreak()));

    private static <T extends Block> RegistryObject<T> register(String name, Supplier<T> block) {
        var obj = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }
}
