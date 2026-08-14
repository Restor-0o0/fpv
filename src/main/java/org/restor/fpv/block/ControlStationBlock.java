package org.restor.fpv.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.block.entity.ControlStationBlockEntity;

// Drone control station: keeps a list of drone link codes; right-click to open the
// list, pick a drone and fly it remotely. Sneak exits control back to the station.
public class ControlStationBlock extends BaseEntityBlock {
    private static final MapCodec<ControlStationBlock> CODEC = simpleCodec(ControlStationBlock::new);

    public ControlStationBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            org.restor.fpv.client.screen.ClientScreens.openStation(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ControlStationBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
