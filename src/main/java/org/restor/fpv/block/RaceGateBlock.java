package org.restor.fpv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.block.entity.RaceGateBlockEntity;

// Checkpoint gate: a coloured frame the racer flies through. FACING (6-way) is the face it
// was placed on and seeds the frame orientation; fine yaw/pitch angle is set in the screen.
public class RaceGateBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public RaceGateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> org.restor.fpv.client.RaceMarkingClient.onGateClicked(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RaceGateBlockEntity be) {
            be.initOrientationFromFacing(state.getValue(FACING));
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RaceGateBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof RaceGateBlockEntity gate)) return Shapes.block();
        return frameShape(gate);
    }

    private static VoxelShape frameShape(RaceGateBlockEntity gate) {
        Vector3f n = gate.normal();
        double size = gate.getSize();
        double half = size / 2.0;
        double cX = 0.5 + n.x * gate.getOffset();
        double cY = size / 2.0 + n.y * gate.getOffset();
        double cZ = 0.5 + n.z * gate.getOffset();
        double t = 0.25;

        if (Math.abs(n.y) > Math.abs(n.x) && Math.abs(n.y) > Math.abs(n.z)) {
            return Shapes.or(
                frameBox(cX - half, cY - t, cZ - half, cX + half, cY + t, cZ - half + t),
                frameBox(cX - half, cY - t, cZ + half - t, cX + half, cY + t, cZ + half),
                frameBox(cX - half, cY - t, cZ - half, cX - half + t, cY + t, cZ + half),
                frameBox(cX + half - t, cY - t, cZ - half, cX + half, cY + t, cZ + half));
        }
        if (Math.abs(n.x) > Math.abs(n.z)) {
            return Shapes.or(
                frameBox(cX - t, cY - half, cZ - half, cX + t, cY + half, cZ - half + t),
                frameBox(cX - t, cY - half, cZ + half - t, cX + t, cY + half, cZ + half),
                frameBox(cX - t, cY - half, cZ - half, cX + t, cY - half + t, cZ + half),
                frameBox(cX - t, cY + half - t, cZ - half, cX + t, cY + half, cZ + half));
        }
        return Shapes.or(
            frameBox(cX - half, cY - half, cZ - t, cX + half, cY - half + t, cZ + t),
            frameBox(cX - half, cY + half - t, cZ - t, cX + half, cY + half, cZ + t),
            frameBox(cX - half, cY - half, cZ - t, cX - half + t, cY + half, cZ + t),
            frameBox(cX + half - t, cY - half, cZ - t, cX + half, cY + half, cZ + t));
    }

    private static VoxelShape frameBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Block.box(minX * 16.0, minY * 16.0, minZ * 16.0, maxX * 16.0, maxY * 16.0, maxZ * 16.0);
    }
}
