package org.restor.fpv.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Silhouettes for the station blocks. They are no longer full cubes, so each one
// needs an explicit outline / collision box.
public final class StationShapes {
    public static final VoxelShape TABLE = Shapes.or(
        Block.box(0, 12, 0, 16, 16, 16),
        Block.box(1, 0, 1, 4, 12, 4),
        Block.box(12, 0, 1, 15, 12, 4),
        Block.box(1, 0, 12, 4, 12, 15),
        Block.box(12, 0, 12, 15, 12, 15),
        Block.box(1, 3, 1, 15, 5, 15));

    public static final VoxelShape DESK = Shapes.or(
        Block.box(0, 9, 0, 16, 11, 16),
        Block.box(1, 0, 1, 4, 9, 4),
        Block.box(12, 0, 1, 15, 9, 4),
        Block.box(1, 0, 12, 4, 9, 15),
        Block.box(12, 0, 12, 15, 9, 15),
        Block.box(1, 11, 10, 15, 16, 15));

    public static final VoxelShape CRATE = Shapes.or(
        Block.box(0, 0, 0, 16, 2, 16),
        Block.box(1, 2, 1, 15, 13, 15),
        Block.box(1, 13, 10, 15, 16, 15));

    public static final VoxelShape PAD = Shapes.or(
        Block.box(0, 0, 0, 16, 3, 16),
        Block.box(1, 3, 10, 15, 12, 15),
        Block.box(1, 3, 1, 3, 9, 3),
        Block.box(13, 3, 1, 15, 9, 3));

    public static final VoxelShape MAST = Shapes.or(
        Block.box(1, 0, 1, 15, 4, 15),
        Block.box(5, 4, 5, 11, 13, 11),
        Block.box(1, 9, 1, 15, 13, 15));

    public static final VoxelShape PEDESTAL = Shapes.or(
        Block.box(0, 0, 0, 16, 3, 16),
        Block.box(2, 3, 2, 14, 7, 14),
        Block.box(6, 7, 6, 10, 11, 10));

    private StationShapes() {}
}
