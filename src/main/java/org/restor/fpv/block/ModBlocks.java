package org.restor.fpv.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.restor.fpv.Fpv;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Fpv.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Fpv.MODID);

    public static final RegistryObject<Block> WORKBENCH = register("workbench",
        () -> new WorkbenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD)));

    public static final RegistryObject<Block> CONTROL_STATION = register("control_station",
        () -> new ControlStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).sound(SoundType.METAL)));

    public static final RegistryObject<Block> KAMIKAZE_STATION = register("kamikaze_station",
        () -> new KamikazeStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f).sound(SoundType.METAL)));

    public static final RegistryObject<Block> ASSEMBLY_STATION = register("assembly_station",
        () -> new org.restor.fpv.assembly.AssemblyStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f).sound(SoundType.METAL)));

    public static final RegistryObject<Block> RACE_START = register("race_start",
        () -> new RaceStartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5f).sound(SoundType.METAL)));

    public static final RegistryObject<Block> RACE_FINISH = register("race_finish",
        () -> new RaceFinishBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5f).sound(SoundType.METAL)));

    public static final RegistryObject<Block> RACE_GATE = register("race_gate",
        () -> new RaceGateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f).sound(SoundType.METAL)));

    public static final RegistryObject<Block> DRONE_NEST = register("drone_nest",
        () -> new DroneNestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4f).sound(SoundType.METAL)));

    private static <T extends Block> RegistryObject<T> register(String name, Supplier<T> block) {
        var obj = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }
}
