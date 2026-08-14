package org.restor.fpv.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.block.entity.RaceGateBlockEntity;

// Checkpoint gate: a coloured frame the racer flies through. FACING (6-way) is the face it
// was placed on and seeds the frame orientation; fine yaw/pitch angle is set in the screen.
public class RaceGateBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final MapCodec<RaceGateBlock> CODEC = simpleCodec(RaceGateBlock::new);

    public RaceGateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            org.restor.fpv.client.RaceMarkingClient.onGateClicked(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face == Direction.UP || face == Direction.DOWN) face = ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, face);
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
        double cY = 1.0 + size / 2.0 + n.y * gate.getOffset();
        double cZ = 0.5 + n.z * gate.getOffset();

        Vector3f ref = Math.abs(n.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(ref).cross(n).normalize();
        Vector3f up = new Vector3f(n).cross(right).normalize();
        Vector3f center = new Vector3f((float) cX, (float) cY, (float) cZ);
        Vector3f[] corners = {
            corner(center, right, up, (float) -half, (float) -half),
            corner(center, right, up, (float)  half, (float) -half),
            corner(center, right, up, (float)  half, (float)  half),
            corner(center, right, up, (float) -half, (float)  half),
        };

        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < 4; i++) shape = Shapes.or(shape, edgeShape(corners[i], corners[(i + 1) % 4]));
        return shape;
    }

    private static Vector3f corner(Vector3f c, Vector3f right, Vector3f up, float u, float v) {
        return new Vector3f(c.x + right.x * u + up.x * v, c.y + right.y * u + up.y * v, c.z + right.z * u + up.z * v);
    }

    private static VoxelShape edgeShape(Vector3f a, Vector3f b) {
        VoxelShape shape = Shapes.empty();
        int steps = Math.max(3, (int) Math.ceil(a.distance(b) * 2.0));
        double radius = 0.32;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            shape = Shapes.or(shape, frameBox(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));
        }
        return shape;
    }

    private static VoxelShape frameBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Block.box(minX * 16.0, minY * 16.0, minZ * 16.0, maxX * 16.0, maxY * 16.0, maxZ * 16.0);
    }
}
