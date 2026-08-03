package org.restor.fpv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.fpv.block.entity.DroneNestBlockEntity;
import org.restor.fpv.block.entity.ModBlockEntities;

// Drone nest / spawner (FPV_IDEAS §3): periodically launches hostile NPC drones while a
// player is near, capped by how many of its drones are still alive around it. Break the
// nest to stop the swarm.
public class DroneNestBlock extends BaseEntityBlock {
    public DroneNestBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DroneNestBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.DRONE_NEST.get(), DroneNestBlockEntity::serverTick);
    }
}
