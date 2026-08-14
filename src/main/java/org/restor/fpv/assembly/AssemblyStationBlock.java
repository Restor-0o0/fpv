package org.restor.fpv.assembly;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

// Drone assembly station: frame in the center, battery above, camera below,
// motors at the corners with a prop slot next to each motor.
public class AssemblyStationBlock extends BaseEntityBlock {
    private static final MapCodec<AssemblyStationBlock> CODEC = simpleCodec(AssemblyStationBlock::new);

    public AssemblyStationBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                 BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AssemblyStationBlockEntity be) {
            ((ServerPlayer) player).openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof AssemblyStationBlockEntity be) {
            Containers.dropContents(level, pos, be.getItems());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblyStationBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
