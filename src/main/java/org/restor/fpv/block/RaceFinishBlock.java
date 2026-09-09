package org.restor.fpv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Optional finish marker for straight race courses. In route-marking mode, right-clicking it
// saves the route with this block as the finish line; otherwise it behaves like a normal block.
public class RaceFinishBlock extends Block {
    public RaceFinishBlock(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            org.restor.fpv.client.RaceMarkingClient.onFinishClicked(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return StationShapes.PEDESTAL;
    }
}
