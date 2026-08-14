package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.network.GhostDataPacket;
import org.restor.fpv.network.RaceTimerPacket;
import org.restor.fpv.race.GhostFrame;
import org.restor.fpv.race.GhostRecording;
import org.restor.fpv.race.GhostSavedData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Start / finish line of a race course. Holds an ORDERED route of gate positions (built in
// marking mode), times a piloted vehicle over a set number of laps requiring every gate to
// be passed in sequence, and keeps a leaderboard of completed runs (name + time).
public class RaceStartBlockEntity extends BlockEntity {
    public static final int MAX_RESULTS = 15;
    public static final int DEFAULT_START_COLOR = 0xFF40E040;
    private static final double START_PLANE_HALF_THICKNESS = 1.25;
    private static final double TRACK_MARGIN = 48.0;

    public record Result(String player, int timeTicks, int laps) {}

    private static final class Progress {
        long startTime;
        int lap;
        int nextGate;
        boolean wasInStart;
        boolean wasInFinish;
        int lastSentLap = -1;
        int lastSentGate = -1;
        // Ghost telemetry recorded for this run (one frame per server tick from startTime).
        int modelType;
        final List<GhostFrame> frames = new ArrayList<>();
    }

    private int laps = 3;
    private int color = DEFAULT_START_COLOR;
    private int size = 6;
    private int yaw = 0;
    private int pitch = 0;
    private int offset = 0;
    private final List<Result> results = new ArrayList<>();
    private final List<BlockPos> route = new ArrayList<>();
    private BlockPos finishPos;

    private UUID markingPlayer; // transient: who is currently marking this route
    private final Map<UUID, Progress> runs = new java.util.HashMap<>();
    private final java.util.Set<UUID> finishedInsideStart = new java.util.HashSet<>();

    public RaceStartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACE_START.get(), pos, state);
    }

    public int getLaps() { return laps; }
    public int getColor() { return color; }
    public int getSize() { return size; }
    public int getYaw() { return yaw; }
    public int getPitch() { return pitch; }
    public int getOffset() { return offset; }
    public BlockPos getFirstRoutePos() { return route.isEmpty() ? null : route.get(0); }

    public void setLaps(int n) {
        laps = Math.max(1, Math.min(50, n));
        sync();
    }

    public void cycleColor() {
        int idx = 0;
        for (int i = 0; i < RaceGateBlockEntity.PALETTE.length; i++) if (RaceGateBlockEntity.PALETTE[i] == color) { idx = i; break; }
        color = RaceGateBlockEntity.PALETTE[(idx + 1) % RaceGateBlockEntity.PALETTE.length];
        sync();
    }

    public void setSize(int s) {
        size = Math.max(2, Math.min(15, s));
        sync();
    }

    public void adjustYaw(int d) { yaw = Math.floorMod(yaw + d, 360); sync(); }
    public void adjustPitch(int d) { pitch = Math.max(-90, Math.min(90, pitch + d)); sync(); }
    public void flipNormal() { yaw = Math.floorMod(yaw + 180, 360); pitch = -pitch; sync(); }
    public void setOffset(int o) { offset = Math.max(0, Math.min(12, o)); sync(); }

    public void initOrientationFromFacing(Direction face) {
        Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        pitch = Math.round((float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, n.y)))));
        yaw = Math.round((float) Math.toDegrees(Math.atan2(-n.x, n.z)));
        yaw = Math.floorMod(yaw, 360);
        sync();
    }

    public Vector3f normal() {
        float y = (float) Math.toRadians(yaw);
        float p = (float) Math.toRadians(pitch);
        float horiz = (float) Math.cos(p);
        return new Vector3f((float) (-Math.sin(y) * horiz), (float) Math.sin(p), (float) (Math.cos(y) * horiz));
    }

    public Vec3 frameCenter() {
        Vector3f n = normal();
        return new Vec3(
            worldPosition.getX() + 0.5 + n.x * offset,
            worldPosition.getY() + size / 2.0 + 1.0 + n.y * offset,
            worldPosition.getZ() + 0.5 + n.z * offset);
    }

    public int getRouteSize() { return route.size(); }
    public List<Result> getResults() { return List.copyOf(results); }

    public AABB renderBoundingBox() {
        Vec3 c = frameCenter();
        double frameReach = size * 0.75 + Math.abs(offset) + 2.0;
        AABB box = new AABB(c.x - frameReach, c.y - frameReach, c.z - frameReach,
            c.x + frameReach, c.y + frameReach, c.z + frameReach);
        if (!route.isEmpty()) box = box.minmax(new AABB(route.get(0)).inflate(2.0));
        return box;
    }

    public void clearResults() {
        results.clear();
        sync();
    }

    // ---------------------------------------------------------------------
    //  Route marking
    // ---------------------------------------------------------------------

    public void beginMarking(ServerPlayer player) {
        clearRouteIndices();
        route.clear();
        finishPos = null;
        markingPlayer = player.getUUID();
        runs.clear();
        finishedInsideStart.clear();
        sync();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_begin"));
    }

    public void addGateToRoute(ServerPlayer player, BlockPos gatePos) {
        if (markingPlayer == null || !markingPlayer.equals(player.getUUID())) return;
        // Ignore a repeat click on the gate that was just added (double-fire / accidental).
        if (!route.isEmpty() && route.get(route.size() - 1).equals(gatePos)) return;
        if (!(level.getBlockEntity(gatePos) instanceof RaceGateBlockEntity gate)) return;
        route.add(gatePos);
        gate.setRouteIndex(route.size());
        gate.setPrevRoutePos(route.size() > 1 ? route.get(route.size() - 2) : worldPosition);
        gate.clearNextRoutePos();
        if (route.size() > 1 && level.getBlockEntity(route.get(route.size() - 2)) instanceof RaceGateBlockEntity prev) {
            prev.setNextRoutePos(gatePos);
        }
        sync();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_add", route.size()));
    }

    public void finishMarking(ServerPlayer player) {
        if (markingPlayer == null || !markingPlayer.equals(player.getUUID())) return;
        markingPlayer = null;
        sync();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_done", route.size()));
    }

    public void finishMarking(ServerPlayer player, List<BlockPos> markedRoute, BlockPos markedFinishPos) {
        clearRouteIndices();
        route.clear();
        route.addAll(markedRoute);
        finishPos = markedFinishPos.equals(worldPosition) ? null : markedFinishPos;
        for (int i = 0; i < route.size(); i++) {
            if (level.getBlockEntity(route.get(i)) instanceof RaceGateBlockEntity gate) {
                gate.setRouteIndex(i + 1);
                gate.setPrevRoutePos(i > 0 ? route.get(i - 1) : worldPosition);
                BlockPos next = i + 1 < route.size() ? route.get(i + 1) : (finishPos != null ? finishPos : worldPosition);
                gate.setNextRoutePos(next);
            }
        }
        markingPlayer = null;
        runs.clear();
        finishedInsideStart.clear();
        sync();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_done", route.size()));
    }

    public boolean isMarkedBy(ServerPlayer player) {
        return markingPlayer != null && markingPlayer.equals(player.getUUID());
    }

    private void clearRouteIndices() {
        if (level == null) return;
        for (BlockPos p : route) {
            if (level.getBlockEntity(p) instanceof RaceGateBlockEntity g) {
                g.setRouteIndex(0);
                g.clearPrevRoutePos();
                g.clearNextRoutePos();
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Server tick — detection, timing, lap/finish logic
    // ---------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, RaceStartBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        Vec3 startCenter = frameCenter();
        Vector3f startNormal = normal().normalize();
        Vec3 finishCenter = finishPos != null
            ? new Vec3(finishPos.getX() + 0.5, finishPos.getY() + 0.5, finishPos.getZ() + 0.5)
            : startCenter;
        AABB box = courseBounds().inflate(TRACK_MARGIN);
        List<Entity> vehicles = level.getEntitiesOfClass(Entity.class, box,
            e -> e instanceof DroneEntity || e instanceof PlaneEntity);

        for (Entity v : vehicles) {
            ServerPlayer pilot = pilotOf(v);
            if (pilot == null) continue;
            Progress prog = runs.get(pilot.getUUID());
            Vec3 vp = v.position();
            AABB vehicleBox = v.getBoundingBox();

            // Next gate in the ordered route (skip any that were removed).
            if (prog != null) {
                while (prog.nextGate < route.size()) {
                    RaceGateBlockEntity g = gateAt(level, route.get(prog.nextGate));
                    if (g == null) { prog.nextGate++; continue; }   // gate deleted → auto-clear
                    if (vp.distanceTo(g.center()) <= g.passRadius()) { prog.nextGate++; continue; }
                    break;
                }
            }

            boolean inStart = intersectsFrame(vehicleBox, startCenter, startNormal, size);
            if (!inStart) finishedInsideStart.remove(pilot.getUUID());
            boolean entering = inStart && !finishedInsideStart.contains(pilot.getUUID()) && (prog == null || !prog.wasInStart);
            if (entering) {
                onCrossStart(level, pilot, prog, v);
                prog = runs.get(pilot.getUUID());
                if (prog == null) continue;
            }
            if (prog != null) {
                if (finishPos != null) {
                    boolean inFinish = vp.distanceTo(finishCenter) <= Math.max(2.5, size * 0.55);
                    if (inFinish && !prog.wasInFinish) onCrossFinish(level, pilot, prog);
                    if (!runs.containsKey(pilot.getUUID())) continue;
                    prog.wasInFinish = inFinish;
                }
                prog.wasInStart = inStart;
                pushHud(pilot, prog, false);
                // Record one telemetry frame per tick while the run is live (see FPV_IDEAS §1-2).
                if (runs.containsKey(pilot.getUUID()) && prog.frames.size() < GhostRecording.MAX_FRAMES) {
                    prog.frames.add(captureFrame(v));
                }
            }
        }
    }

    private void onCrossStart(ServerLevel level, ServerPlayer pilot, Progress prog, Entity vehicle) {
        if (prog == null) {
            startRun(level, pilot, vehicle);
            return;
        }
        if (finishPos != null) return;
        if (prog.nextGate >= route.size()) {
            if (prog.lap >= laps) {
                finishRun(level, pilot, prog, laps);
            } else {
                prog.lap++;
                prog.nextGate = 0;
                pilot.sendSystemMessage(Component.translatable("msg.fpv.race_lap", prog.lap, laps));
                pushHud(pilot, prog, true);
            }
        } else {
            // Crossed the line without clearing the whole route in order — fresh (re)start.
            startRun(level, pilot, vehicle);
        }
    }

    private void onCrossFinish(ServerLevel level, ServerPlayer pilot, Progress prog) {
        if (prog.nextGate >= route.size()) finishRun(level, pilot, prog, 1);
    }

    private void finishRun(ServerLevel level, ServerPlayer pilot, Progress prog, int resultLaps) {
        int time = (int) (level.getGameTime() - prog.startTime);
        recordResult(new Result(pilot.getName().getString(), time, resultLaps));
        saveGhostIfRecord(level, pilot, prog, time);
        runs.remove(pilot.getUUID());
        finishedInsideStart.add(pilot.getUUID());
        pilot.sendSystemMessage(Component.translatable("msg.fpv.race_finish", formatTime(time)));
        PacketDistributor.sendToPlayer(pilot,
            new RaceTimerPacket(0, totalLaps(), prog.startTime, route.size(), route.size(), time));
    }

    private void startRun(ServerLevel level, ServerPlayer pilot, Entity vehicle) {
        Progress p = new Progress();
        p.startTime = level.getGameTime();
        p.lap = 1;
        p.nextGate = 0;
        p.wasInStart = true;
        p.modelType = modelTypeOf(vehicle);
        finishedInsideStart.remove(pilot.getUUID());
        runs.put(pilot.getUUID(), p);
        pilot.sendSystemMessage(finishPos != null
            ? Component.translatable("msg.fpv.race_start_straight")
            : Component.translatable("msg.fpv.race_start", laps));
        pushHud(pilot, p, true);
        sendGhosts(level, pilot, p.startTime);
    }

    // ---------------------------------------------------------------------
    //  Ghost recording (FPV_IDEAS §1-2)
    // ---------------------------------------------------------------------

    // Stable identity of this course: dimension + start + finish + ordered gates + laps.
    // Moving a gate or changing the lap count makes a new course, dropping old ghosts.
    private String courseKey(ServerLevel level) {
        StringBuilder sb = new StringBuilder(level.dimension().location().toString());
        sb.append('@').append(worldPosition.asLong());
        sb.append('#').append(finishPos != null ? finishPos.asLong() : 0L);
        for (BlockPos p : route) sb.append('/').append(p.asLong());
        sb.append('*').append(totalLaps());
        return sb.toString();
    }

    private static int modelTypeOf(Entity v) {
        if (v instanceof org.restor.fpv.entity.TinyWhoopDroneEntity) return 1;
        if (v instanceof org.restor.fpv.entity.CinewhoopDroneEntity) return 2;
        if (v instanceof PlaneEntity) return 3;
        return 0;
    }

    private static GhostFrame captureFrame(Entity v) {
        float roll = 0f, throttle = 0f;
        if (v instanceof DroneEntity d) { roll = d.getSyncedRoll(); throttle = d.getVisualThrottle(); }
        else if (v instanceof PlaneEntity p) { roll = p.getSyncedRoll(); throttle = p.getVisualThrottle(); }
        return new GhostFrame(v.getX(), v.getY(), v.getZ(), v.getYRot(), v.getXRot(), roll, throttle);
    }

    private void saveGhostIfRecord(ServerLevel level, ServerPlayer pilot, Progress prog, int time) {
        if (prog.frames.isEmpty()) return;
        GhostRecording rec = GhostRecording.of(pilot.getName().getString(), pilot.getUUID(),
            time, prog.modelType, prog.frames);
        int r = GhostSavedData.get(level).submit(courseKey(level), pilot.getUUID(), rec);
        if ((r & 2) != 0) {
            pilot.sendSystemMessage(Component.translatable("msg.fpv.race_record_overall"));
        } else if ((r & 1) != 0) {
            pilot.sendSystemMessage(Component.translatable("msg.fpv.race_record_personal"));
        }
    }

    // Sends the personal-best and overall-best ghosts for this course to the starting pilot.
    // Always sent (even when empty) so a fresh run clears any ghosts from a previous course.
    private void sendGhosts(ServerLevel level, ServerPlayer pilot, long startTime) {
        String key = courseKey(level);
        GhostSavedData data = GhostSavedData.get(level);
        GhostRecording personal = data.personalBest(key, pilot.getUUID());
        GhostRecording overall = data.overallBest(key);
        boolean same = personal != null && personal == overall; // player owns the track record
        PacketDistributor.sendToPlayer(pilot,
            new GhostDataPacket(startTime, same, personal, same ? null : overall));
    }

    private AABB courseBounds() {
        int minX = worldPosition.getX();
        int minY = worldPosition.getY();
        int minZ = worldPosition.getZ();
        int maxX = minX + 1;
        int maxY = minY + 1;
        int maxZ = minZ + 1;
        for (BlockPos p : route) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX() + 1);
            maxY = Math.max(maxY, p.getY() + 1);
            maxZ = Math.max(maxZ, p.getZ() + 1);
        }
        if (finishPos != null) {
            minX = Math.min(minX, finishPos.getX());
            minY = Math.min(minY, finishPos.getY());
            minZ = Math.min(minZ, finishPos.getZ());
            maxX = Math.max(maxX, finishPos.getX() + 1);
            maxY = Math.max(maxY, finishPos.getY() + 1);
            maxZ = Math.max(maxZ, finishPos.getZ() + 1);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private int totalLaps() { return finishPos != null ? 1 : laps; }

    private static boolean intersectsFrame(AABB box, Vec3 center, Vector3f normal, int size) {
        Vector3f n = new Vector3f(normal).normalize();
        Vector3f ref = Math.abs(n.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(ref).cross(n).normalize();
        Vector3f up = new Vector3f(n).cross(right).normalize();
        double boxCenterX = (box.minX + box.maxX) * 0.5;
        double boxCenterY = (box.minY + box.maxY) * 0.5;
        double boxCenterZ = (box.minZ + box.maxZ) * 0.5;
        double ex = (box.maxX - box.minX) * 0.5;
        double ey = (box.maxY - box.minY) * 0.5;
        double ez = (box.maxZ - box.minZ) * 0.5;
        double dx = boxCenterX - center.x;
        double dy = boxCenterY - center.y;
        double dz = boxCenterZ - center.z;
        double along = dx * n.x + dy * n.y + dz * n.z;
        double x = dx * right.x + dy * right.y + dz * right.z;
        double y = dx * up.x + dy * up.y + dz * up.z;
        double alongExtent = projectedExtent(ex, ey, ez, n);
        double xExtent = projectedExtent(ex, ey, ez, right);
        double yExtent = projectedExtent(ex, ey, ez, up);
        double half = size / 2.0;
        return Math.abs(along) <= START_PLANE_HALF_THICKNESS + alongExtent
            && Math.abs(x) <= half + xExtent
            && Math.abs(y) <= half + yExtent;
    }

    private static double projectedExtent(double ex, double ey, double ez, Vector3f axis) {
        return ex * Math.abs(axis.x) + ey * Math.abs(axis.y) + ez * Math.abs(axis.z);
    }

    private void pushHud(ServerPlayer pilot, Progress prog, boolean force) {
        if (!force && prog.lastSentLap == prog.lap && prog.lastSentGate == prog.nextGate) return;
        prog.lastSentLap = prog.lap;
        prog.lastSentGate = prog.nextGate;
        PacketDistributor.sendToPlayer(pilot,
            new RaceTimerPacket(prog.lap, totalLaps(), prog.startTime, prog.nextGate, route.size(), -1));
    }

    private void recordResult(Result r) {
        results.add(r);
        results.sort((a, b) -> Integer.compare(a.timeTicks(), b.timeTicks()));
        while (results.size() > MAX_RESULTS) results.remove(results.size() - 1);
        sync();
    }

    private static RaceGateBlockEntity gateAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RaceGateBlockEntity g ? g : null;
    }

    private static ServerPlayer pilotOf(Entity v) {
        if (v instanceof DroneEntity d) return d.getRacePilot();
        if (v instanceof PlaneEntity p && p.getControllingPassenger() instanceof ServerPlayer sp) return sp;
        return null;
    }

    public static String formatTime(int ticks) {
        int ms = ticks * 50;
        int minutes = ms / 60000;
        int seconds = (ms / 1000) % 60;
        int millis = ms % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }

    // ---------------------------------------------------------------------
    //  Persistence / sync
    // ---------------------------------------------------------------------

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        laps = Math.max(1, tag.getInt("Laps"));
        if (tag.contains("Color")) color = tag.getInt("Color");
        if (tag.contains("Size")) size = Math.max(2, Math.min(15, tag.getInt("Size")));
        if (tag.contains("Yaw")) yaw = tag.getInt("Yaw");
        if (tag.contains("Pitch")) pitch = tag.getInt("Pitch");
        if (tag.contains("Offset")) offset = tag.getInt("Offset");
        results.clear();
        ListTag list = tag.getList("Results", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            results.add(new Result(c.getString("Player"), c.getInt("Time"), c.getInt("Laps")));
        }
        route.clear();
        ListTag r = tag.getList("Route", Tag.TAG_LONG);
        for (int i = 0; i < r.size(); i++) route.add(BlockPos.of(((LongTag) r.get(i)).getAsLong()));
        finishPos = tag.contains("FinishPos") ? BlockPos.of(tag.getLong("FinishPos")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Laps", laps);
        tag.putInt("Color", color);
        tag.putInt("Size", size);
        tag.putInt("Yaw", yaw);
        tag.putInt("Pitch", pitch);
        tag.putInt("Offset", offset);
        ListTag list = new ListTag();
        for (Result r : results) {
            CompoundTag c = new CompoundTag();
            c.putString("Player", r.player());
            c.putInt("Time", r.timeTicks());
            c.putInt("Laps", r.laps());
            list.add(c);
        }
        tag.put("Results", list);
        ListTag r = new ListTag();
        for (BlockPos p : route) r.add(LongTag.valueOf(p.asLong()));
        tag.put("Route", r);
        if (finishPos != null) tag.putLong("FinishPos", finishPos.asLong());
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
