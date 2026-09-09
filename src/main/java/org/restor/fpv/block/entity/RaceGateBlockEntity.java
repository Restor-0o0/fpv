package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// A checkpoint gate: a coloured square frame the drone flies through. Orientation is a
// free yaw/pitch (seeded from the face it's placed on), so gates can be vertical, flat, or
// tilted at any angle. Size and colour are configurable. routeIndex (1-based) is stamped by
// the start block when the course route is marked; 0 = not part of a route.
public class RaceGateBlockEntity extends BlockEntity {
    public static final int[] PALETTE = {
        0xFFFF3030, 0xFFFF9020, 0xFFFFE030, 0xFF40E040,
        0xFF30C0FF, 0xFF4060FF, 0xFFC040FF, 0xFFFFFFFF
    };

    private int size = 6;
    private int color = PALETTE[0];
    private int yaw = 0;     // degrees: frame-normal heading
    private int pitch = 0;   // degrees: frame-normal tilt (+90 = flat facing up)
    private int offset = 0;  // whole blocks along the frame normal, away from the anchor block
    private int routeIndex = 0;
    private BlockPos prevRoutePos;
    private BlockPos nextRoutePos;

    public RaceGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACE_GATE.get(), pos, state);
    }

    public int getSize()  { return size; }
    public int getColor() { return color; }
    public int getYaw()   { return yaw; }
    public int getPitch() { return pitch; }
    public int getOffset() { return offset; }
    public int getRouteIndex() { return routeIndex; }
    public BlockPos getPrevRoutePos() { return prevRoutePos; }
    public BlockPos getNextRoutePos() { return nextRoutePos; }

    public void configureGenerated(int generatedSize, int generatedYaw) {
        size = Math.max(2, Math.min(15, generatedSize));
        yaw = Math.floorMod(generatedYaw, 360);
        pitch = 0;
        offset = 0;
        sync();
    }

    public void setSize(int s) { size = Math.max(2, Math.min(15, s)); sync(); }

    public void cycleColor() {
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == color) { idx = i; break; }
        color = PALETTE[(idx + 1) % PALETTE.length];
        sync();
    }

    public void adjustYaw(int d)   { yaw = Math.floorMod(yaw + d, 360); sync(); }
    public void adjustPitch(int d) { pitch = Math.max(-90, Math.min(90, pitch + d)); sync(); }
    public void flipNormal() { yaw = Math.floorMod(yaw + 180, 360); pitch = -pitch; sync(); }
    public void setOffset(int o) { offset = Math.max(0, Math.min(12, o)); sync(); }

    public void setRouteIndex(int i) { routeIndex = Math.max(0, i); sync(); }
    public void setPrevRoutePos(BlockPos pos) { prevRoutePos = pos; sync(); }
    public void clearPrevRoutePos() { prevRoutePos = null; sync(); }
    public void setNextRoutePos(BlockPos pos) { nextRoutePos = pos; sync(); }
    public void clearNextRoutePos() { nextRoutePos = null; sync(); }

    // Seed orientation from the block face the gate was placed against.
    public void initOrientationFromFacing(Direction face) {
        Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        pitch = Math.round((float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, n.y)))));
        yaw = Math.round((float) Math.toDegrees(Math.atan2(-n.x, n.z)));
        yaw = Math.floorMod(yaw, 360);
        sync();
    }

    // Frame-normal unit vector from yaw/pitch (the axis the racer flies along).
    public Vector3f normal() {
        float y = (float) Math.toRadians(yaw);
        float p = (float) Math.toRadians(pitch);
        float horiz = (float) Math.cos(p);
        return new Vector3f((float) (-Math.sin(y) * horiz), (float) Math.sin(p), (float) (Math.cos(y) * horiz));
    }

    public Vec3 center() {
        Vector3f n = normal();
        return new Vec3(
            worldPosition.getX() + 0.5 + n.x * offset,
            worldPosition.getY() + 1.0 + size / 2.0 + n.y * offset,
            worldPosition.getZ() + 0.5 + n.z * offset);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return renderBoundingBox();
    }

    public AABB renderBoundingBox() {
        Vec3 c = center();
        double frameReach = size * 0.75 + Math.abs(offset) + 2.0;
        AABB box = new AABB(
            c.x - frameReach, c.y - frameReach, c.z - frameReach,
            c.x + frameReach, c.y + frameReach, c.z + frameReach);
        if (prevRoutePos != null) box = box.minmax(new AABB(prevRoutePos).inflate(2.0));
        if (nextRoutePos != null) box = box.minmax(new AABB(nextRoutePos).inflate(2.0));
        return box;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Size"))  size = tag.getInt("Size");
        if (tag.contains("Color")) color = tag.getInt("Color");
        if (tag.contains("Yaw"))   yaw = tag.getInt("Yaw");
        if (tag.contains("Pitch")) pitch = tag.getInt("Pitch");
        if (tag.contains("Offset")) offset = tag.getInt("Offset");
        routeIndex = tag.getInt("RouteIndex");
        prevRoutePos = tag.contains("PrevRoutePos") ? BlockPos.of(tag.getLong("PrevRoutePos")) : null;
        nextRoutePos = tag.contains("NextRoutePos") ? BlockPos.of(tag.getLong("NextRoutePos")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Size", size);
        tag.putInt("Color", color);
        tag.putInt("Yaw", yaw);
        tag.putInt("Pitch", pitch);
        tag.putInt("Offset", offset);
        tag.putInt("RouteIndex", routeIndex);
        if (prevRoutePos != null) tag.putLong("PrevRoutePos", prevRoutePos.asLong());
        if (nextRoutePos != null) tag.putLong("NextRoutePos", nextRoutePos.asLong());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
