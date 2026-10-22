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
import org.restor.fpv.race.RacePlaneCrossing;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Start / finish line of a race course. Holds an ORDERED route of gate positions (built in
// marking mode), times a piloted vehicle over a set number of laps requiring every gate to
// be passed in sequence, and keeps a leaderboard of completed runs (name + time).
public class RaceStartBlockEntity extends BlockEntity {
    public static final int MAX_RESULTS = 15;
    public static final int DEFAULT_START_COLOR = 0xFF40E040;
    private static final double TRACK_MARGIN = 48.0;

    public record Result(String player, UUID playerId, int timeTicks, int laps, String courseKey) {}

    private static final class Progress {
        long startTime;
        int lap;
        int nextGate;
        UUID vehicleId;
        Vec3 previousPosition;
        long previousPositionTick;
        String courseKey;
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
    private String activeCourseKey;
    private boolean courseDirty;

    private UUID markingPlayer; // transient: who is currently marking this route
    private final Map<UUID, Progress> runs = new java.util.HashMap<>();
    private final Map<UUID, Vec3> observedVehiclePositions = new java.util.HashMap<>();
    private final Map<UUID, Long> observedVehicleTicks = new java.util.HashMap<>();

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
    public BlockPos getFinishPos() { return finishPos; }

    // The finish hoop is lifted off its block the same way the start frame is, so the plane
    // the timer watches is exactly the one drawn in the world.
    public Vec3 finishCenter() {
        return finishPos != null ? Vec3.atCenterOf(finishPos).add(0.0, size / 2.0 + 1.0, 0.0) : frameCenter();
    }

    public Vec3 finishNormal() {
        if (finishPos == null) return normalVec();
        Vec3 back = finishCenter().subtract(routeCenterAt(route.isEmpty() ? null : route.get(route.size() - 1)));
        return back.lengthSqr() < 1.0E-6 ? normalVec() : back.normalize();
    }

    private Vec3 normalVec() {
        Vector3f n = normal();
        return new Vec3(n.x, n.y, n.z);
    }

    private Vec3 routeCenterAt(BlockPos pos) {
        if (pos == null) return frameCenter();
        if (level != null && level.getBlockEntity(pos) instanceof RaceGateBlockEntity gate) return gate.center();
        return Vec3.atCenterOf(pos);
    }

    public void setLaps(int n) {
        int value = Math.max(1, Math.min(50, n));
        if (laps == value) return;
        laps = value;
        invalidateCourse();
        sync();
    }

    public void cycleColor() {
        int idx = 0;
        for (int i = 0; i < RaceGateBlockEntity.PALETTE.length; i++) if (RaceGateBlockEntity.PALETTE[i] == color) { idx = i; break; }
        color = RaceGateBlockEntity.PALETTE[(idx + 1) % RaceGateBlockEntity.PALETTE.length];
        sync();
    }

    public void setSize(int s) {
        int value = Math.max(2, Math.min(15, s));
        if (size == value) return;
        size = value;
        invalidateCourse();
        sync();
    }

    public void adjustYaw(int d) { int old = yaw; yaw = Math.floorMod(yaw + d, 360); if (yaw != old) invalidateCourse(); sync(); }
    public void adjustPitch(int d) { int old = pitch; pitch = Math.max(-90, Math.min(90, pitch + d)); if (pitch != old) invalidateCourse(); sync(); }
    public void flipNormal() { yaw = Math.floorMod(yaw + 180, 360); pitch = -pitch; invalidateCourse(); sync(); }
    public void setOffset(int o) { int old = offset; offset = Math.max(0, Math.min(12, o)); if (offset != old) invalidateCourse(); sync(); }

    public void initOrientationFromFacing(Direction face) {
        Vector3f n = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        pitch = Math.round((float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, n.y)))));
        yaw = Math.round((float) Math.toDegrees(Math.atan2(-n.x, n.z)));
        yaw = Math.floorMod(yaw, 360);
        invalidateCourse();
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
    public List<Result> getResults() {
        if (courseDirty) return List.of();
        if (activeCourseKey == null) return List.copyOf(results);
        return results.stream().filter(r -> Objects.equals(activeCourseKey, r.courseKey())).toList();
    }

    public AABB getRenderBoundingBox() {
        return renderBoundingBox();
    }

    public AABB renderBoundingBox() {
        Vec3 c = frameCenter();
        double frameReach = size * 0.75 + Math.abs(offset) + 2.0;
        AABB box = new AABB(c.x - frameReach, c.y - frameReach, c.z - frameReach,
            c.x + frameReach, c.y + frameReach, c.z + frameReach);
        if (!route.isEmpty()) box = box.minmax(new AABB(route.get(0)).inflate(2.0));
        if (finishPos != null) box = box.minmax(new AABB(finishPos).inflate(size * 0.75 + 2.0));
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
        invalidateCourse();
        markingPlayer = player.getUUID();
        runs.clear();
        observedVehiclePositions.clear();
        observedVehicleTicks.clear();
        sync();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_begin"));
    }

    public void addGateToRoute(ServerPlayer player, BlockPos gatePos) {
        if (markingPlayer == null || !markingPlayer.equals(player.getUUID())) return;
        // Ignore a repeat click on the gate that was just added (double-fire / accidental).
        if (!route.isEmpty() && route.get(route.size() - 1).equals(gatePos)) return;
        if (!(level.getBlockEntity(gatePos) instanceof RaceGateBlockEntity gate)) return;
        route.add(gatePos);
        invalidateCourse();
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
        invalidateCourse();
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
        observedVehiclePositions.clear();
        observedVehicleTicks.clear();
        sync();
        player.sendSystemMessage(Component.translatable("msg.fpv.race_mark_done", route.size()));
    }

    public boolean isMarkedBy(ServerPlayer player) {
        return markingPlayer != null && markingPlayer.equals(player.getUUID());
    }

    public boolean installGeneratedRoute(List<BlockPos> generatedRoute, int generatedLaps,
                                         int generatedSize, int generatedYaw) {
        if (!(level instanceof ServerLevel) || generatedRoute.isEmpty()
            || generatedRoute.stream().distinct().count() != generatedRoute.size()) {
            return false;
        }
        List<RaceGateBlockEntity> gates = new ArrayList<>(generatedRoute.size());
        for (BlockPos pos : generatedRoute) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RaceGateBlockEntity gate)) return false;
            gates.add(gate);
        }

        clearRouteIndices();
        route.clear();
        route.addAll(generatedRoute);
        finishPos = null;
        laps = Math.max(1, Math.min(50, generatedLaps));
        size = Math.max(2, Math.min(15, generatedSize));
        yaw = Math.floorMod(generatedYaw, 360);
        pitch = 0;
        offset = 0;
        markingPlayer = null;
        invalidateCourse();
        for (int i = 0; i < gates.size(); i++) {
            RaceGateBlockEntity gate = gates.get(i);
            gate.setRouteIndex(i + 1);
            gate.setPrevRoutePos(i == 0 ? worldPosition : generatedRoute.get(i - 1));
            gate.setNextRoutePos(i + 1 < gates.size() ? generatedRoute.get(i + 1) : worldPosition);
        }
        sync();
        return true;
    }

    private void clearRouteIndices() {
        if (level == null) return;
        for (BlockPos p : route) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof RaceGateBlockEntity g) {
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
        boolean courseReady = refreshCourseIdentity(level);
        cleanupRuns(level);
        Vec3 startCenter = frameCenter();
        Vector3f startNormal = normal().normalize();
        Vec3 finishCenter = finishCenter();
        Vec3 finishNormal = finishPos != null ? finishNormal() : Vec3.ZERO;
        AABB box = courseBounds().inflate(TRACK_MARGIN);
        Map<UUID, Entity> vehicles = new LinkedHashMap<>();
        for (Entity vehicle : level.getEntitiesOfClass(Entity.class, box,
            e -> e instanceof DroneEntity || e instanceof PlaneEntity)) {
            vehicles.put(vehicle.getUUID(), vehicle);
        }
        for (Progress progress : runs.values()) {
            Entity vehicle = level.getEntity(progress.vehicleId);
            if (vehicle != null) vehicles.put(vehicle.getUUID(), vehicle);
        }

        for (Entity v : vehicles.values()) {
            ServerPlayer pilot = pilotOf(v);
            if (pilot == null) continue;
            Progress prog = runs.get(pilot.getUUID());
            Vec3 current = v.position();
            Vec3 previous = observedVehicleTicks.getOrDefault(v.getUUID(), Long.MIN_VALUE) == level.getGameTime() - 1
                ? observedVehiclePositions.get(v.getUUID()) : null;
            if (prog != null) {
                if (!v.getUUID().equals(prog.vehicleId)) {
                    abortRun(pilot, prog, false);
                    prog = null;
                } else {
                    previous = prog.previousPositionTick == level.getGameTime() - 1 ? prog.previousPosition : null;
                }
            }
            if (previous == null) {
                observedVehiclePositions.put(v.getUUID(), current);
                observedVehicleTicks.put(v.getUUID(), level.getGameTime());
                if (prog != null) {
                    prog.previousPosition = current;
                    prog.previousPositionTick = level.getGameTime();
                }
                continue;
            }

            if (prog != null && hasMissingLoadedGate(level)) {
                abortRun(pilot, prog, true);
                observedVehiclePositions.put(v.getUUID(), current);
                observedVehicleTicks.put(v.getUUID(), level.getGameTime());
                continue;
            }

            List<Vec3> path = flownPath(v, previous, current);
            double startCrossing = crossing(path, startCenter, startNormal, size / 2.0);
            boolean startedNow = false;
            if (prog == null && courseReady && !route.isEmpty() && !Double.isNaN(startCrossing)
                && !hasMissingLoadedGate(level)) {
                startRun(level, pilot, v, previous);
                prog = runs.get(pilot.getUUID());
                startedNow = true;
            }

            if (prog != null) {
                if (finishPos != null) {
                    double finishCrossing = crossing(path, finishCenter, finishNormal, size / 2.0);
                    processStraightSegment(level, pilot, prog, v, path,
                        startedNow ? startCrossing : -1.0, finishCrossing);
                } else if (startedNow) {
                    advanceGates(level, pilot, prog, path, startCrossing, 1.0);
                } else {
                    processLapSegment(level, pilot, prog, v, path, startCrossing);
                }
                prog = runs.get(pilot.getUUID());
                if (prog != null) {
                    prog.previousPosition = current;
                    prog.previousPositionTick = level.getGameTime();
                    pushHud(pilot, prog, false);
                }
                if (prog != null && prog.frames.size() < GhostRecording.MAX_FRAMES) {
                    prog.frames.add(captureFrame(v));
                }
            }
            observedVehiclePositions.put(v.getUUID(), current);
            observedVehicleTicks.put(v.getUUID(), level.getGameTime());
        }
        observedVehiclePositions.keySet().retainAll(vehicles.keySet());
        observedVehicleTicks.keySet().retainAll(vehicles.keySet());
    }

    private void processLapSegment(ServerLevel level, ServerPlayer pilot, Progress prog, Entity vehicle,
                                   List<Vec3> path, double startCrossing) {
        double beforeStart = Double.isNaN(startCrossing) ? 1.0 : startCrossing;
        if (!advanceGates(level, pilot, prog, path, -1.0, beforeStart)) return;
        if (Double.isNaN(startCrossing)) return;
        onCrossStart(level, pilot, prog, vehicle, path.get(0));
        Progress currentProgress = runs.get(pilot.getUUID());
        if (currentProgress != null) {
            advanceGates(level, pilot, currentProgress, path, startCrossing, 1.0);
        }
    }

    private void processStraightSegment(ServerLevel level, ServerPlayer pilot, Progress prog, Entity vehicle,
                                        List<Vec3> path, double segmentStart, double finishCrossing) {
        if (Double.isNaN(finishCrossing) || finishCrossing <= segmentStart) {
            advanceGates(level, pilot, prog, path, segmentStart, 1.0);
            return;
        }
        if (!advanceGates(level, pilot, prog, path, segmentStart, finishCrossing)) return;
        onCrossFinish(level, pilot, prog, vehicle);
        Progress currentProgress = runs.get(pilot.getUUID());
        if (currentProgress != null) {
            advanceGates(level, pilot, currentProgress, path, finishCrossing, 1.0);
        }
    }

    private boolean advanceGates(ServerLevel level, ServerPlayer pilot, Progress prog, List<Vec3> path,
                                 double after, double through) {
        double cursor = after;
        while (prog.nextGate < route.size()) {
            BlockPos gatePos = route.get(prog.nextGate);
            if (!level.isLoaded(gatePos)) return true;
            RaceGateBlockEntity gate = gateAt(level, gatePos);
            if (gate == null) {
                abortRun(pilot, prog, true);
                return false;
            }
            double hit = gateCrossing(path, gate.center(), gate.normal(), gate.getSize() / 2.0);
            if (Double.isNaN(hit) || hit <= cursor + 1.0e-9 || hit > through + 1.0e-9) break;
            prog.nextGate++;
            cursor = hit;
        }
        return runs.get(pilot.getUUID()) == prog;
    }

    private boolean hasMissingLoadedGate(ServerLevel level) {
        for (BlockPos gatePos : route) {
            if (level.isLoaded(gatePos) && gateAt(level, gatePos) == null) return true;
        }
        return false;
    }

    private void abortRun(ServerPlayer pilot, Progress prog, boolean missingGate) {
        if (runs.remove(pilot.getUUID(), prog)) {
            if (missingGate) pilot.sendSystemMessage(Component.translatable("msg.fpv.race_invalid_gate"));
            PacketDistributor.sendToPlayer(pilot,
                new RaceTimerPacket(0, totalLaps(), prog.startTime, prog.nextGate, route.size(), -1));
        }
    }

    private static double crossing(List<Vec3> path, Vec3 center, Vector3f normal, double halfAperture) {
        return crossing(path, center, new Vec3(normal.x, normal.y, normal.z), halfAperture);
    }

    // A checkpoint hoop counts whichever way the racer went through it — only the start and
    // finish planes care which side you came from.
    private static double gateCrossing(List<Vec3> path, Vec3 center, Vector3f normal, double halfAperture) {
        double forward = crossing(path, center, normal, halfAperture);
        double backward = crossing(path, center, new Vector3f(normal).negate(), halfAperture);
        if (Double.isNaN(forward)) return backward;
        if (Double.isNaN(backward)) return forward;
        return Math.min(forward, backward);
    }

    // Returns where along the whole path the plane was crossed, normalised to [0,1] so the
    // ordering the gate/lap logic relies on still holds across multiple segments.
    private static double crossing(List<Vec3> path, Vec3 center, Vec3 normal, double halfAperture) {
        int segments = path.size() - 1;
        if (segments < 1) return Double.NaN;
        for (int i = 0; i < segments; i++) {
            Vec3 from = path.get(i);
            Vec3 to = path.get(i + 1);
            double t = RacePlaneCrossing.crossingParameter(
                from.x, from.y, from.z, to.x, to.y, to.z,
                center.x, center.y, center.z, normal.x, normal.y, normal.z, halfAperture);
            if (!Double.isNaN(t)) return (i + t) / segments;
        }
        return Double.NaN;
    }

    // A client-simulated drone reports 20 poses per server tick; walking them all is what
    // keeps every gate counted when the server tick is slow.
    private static List<Vec3> flownPath(Entity vehicle, Vec3 previous, Vec3 current) {
        List<Vec3> path = new ArrayList<>();
        path.add(previous);
        if (vehicle instanceof DroneEntity drone) {
            for (Vec3 point : drone.lastTickPath()) {
                if (!point.equals(path.get(path.size() - 1))) path.add(point);
            }
        }
        if (!current.equals(path.get(path.size() - 1))) path.add(current);
        return path;
    }

    private void onCrossStart(ServerLevel level, ServerPlayer pilot, Progress prog, Entity vehicle, Vec3 previousPosition) {
        if (finishPos != null) return;
        if (prog.nextGate >= route.size()) {
            if (prog.lap >= laps) {
                finishRun(level, pilot, prog, laps, vehicle);
            } else {
                prog.lap++;
                prog.nextGate = 0;
                pilot.sendSystemMessage(Component.translatable("msg.fpv.race_lap", prog.lap, laps));
                pushHud(pilot, prog, true);
            }
        } else {
            startRun(level, pilot, vehicle, previousPosition);
        }
    }

    private void onCrossFinish(ServerLevel level, ServerPlayer pilot, Progress prog, Entity vehicle) {
        if (prog.nextGate >= route.size()) finishRun(level, pilot, prog, 1, vehicle);
    }

    private void finishRun(ServerLevel level, ServerPlayer pilot, Progress prog, int resultLaps, Entity vehicle) {
        int time = (int) (level.getGameTime() - prog.startTime);
        appendFinalFrame(prog, vehicle);
        recordResult(new Result(pilot.getName().getString(), pilot.getUUID(), time, resultLaps, prog.courseKey));
        saveGhostIfRecord(level, pilot, prog, time);
        runs.remove(pilot.getUUID());
        pilot.sendSystemMessage(Component.translatable("msg.fpv.race_finish", formatTime(time)));
        PacketDistributor.sendToPlayer(pilot,
            new RaceTimerPacket(0, totalLaps(), prog.startTime, route.size(), route.size(), time));
    }

    private void startRun(ServerLevel level, ServerPlayer pilot, Entity vehicle, Vec3 previousPosition) {
        Progress p = new Progress();
        p.startTime = level.getGameTime();
        p.lap = 1;
        p.nextGate = 0;
        p.vehicleId = vehicle.getUUID();
        p.previousPosition = previousPosition;
        p.previousPositionTick = level.getGameTime() - 1;
        p.modelType = modelTypeOf(vehicle);
        p.courseKey = activeCourseKey;
        runs.put(pilot.getUUID(), p);
        pilot.sendSystemMessage(finishPos != null
            ? Component.translatable("msg.fpv.race_start_straight")
            : Component.translatable("msg.fpv.race_start", laps));
        pushHud(pilot, p, true);
        sendGhosts(level, pilot, p.startTime);
    }

    private void appendFinalFrame(Progress progress, Entity vehicle) {
        GhostFrame frame = captureFrame(vehicle);
        if (progress.frames.size() < GhostRecording.MAX_FRAMES) {
            progress.frames.add(frame);
        } else if (!progress.frames.isEmpty()) {
            progress.frames.set(progress.frames.size() - 1, frame);
        }
    }

    private void invalidateCourse() {
        courseDirty = true;
        if (!(level instanceof ServerLevel serverLevel)) {
            runs.clear();
            return;
        }
        for (Map.Entry<UUID, Progress> entry : List.copyOf(runs.entrySet())) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) abortRun(player, entry.getValue(), false);
            else runs.remove(entry.getKey(), entry.getValue());
        }
        observedVehiclePositions.clear();
        observedVehicleTicks.clear();
    }

    private boolean refreshCourseIdentity(ServerLevel level) {
        String key = courseKey(level);
        if (key == null) return false;
        if (activeCourseKey == null) {
            activeCourseKey = key;
            for (int i = 0; i < results.size(); i++) {
                Result result = results.get(i);
                if (result.courseKey() == null) {
                    results.set(i, new Result(result.player(), result.playerId(), result.timeTicks(), result.laps(), key));
                }
            }
            courseDirty = false;
            sync();
        } else if (!activeCourseKey.equals(key)) {
            invalidateCourse();
            activeCourseKey = key;
            results.clear();
            courseDirty = false;
            sync();
        }
        courseDirty = false;
        return true;
    }

    private void cleanupRuns(ServerLevel level) {
        long now = level.getGameTime();
        for (Map.Entry<UUID, Progress> entry : List.copyOf(runs.entrySet())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            Entity vehicle = level.getEntity(entry.getValue().vehicleId);
            ServerPlayer currentPilot = vehicle != null ? pilotOf(vehicle) : null;
            boolean valid = player != null && !player.isRemoved() && player.isAlive()
                && vehicle != null && !vehicle.isRemoved() && vehicle.isAlive()
                && currentPilot != null && currentPilot.getUUID().equals(player.getUUID())
                && now - entry.getValue().startTime <= GhostRecording.MAX_FRAMES;
            if (valid && Objects.equals(entry.getValue().courseKey, activeCourseKey)) continue;
            if (player != null) abortRun(player, entry.getValue(), false);
            else runs.remove(entry.getKey(), entry.getValue());
        }
    }

    private String courseKey(ServerLevel level) {
        StringBuilder sb = new StringBuilder(level.dimension().location().toString());
        appendGeometry(sb, worldPosition, size, yaw, pitch, offset);
        sb.append("|laps=").append(totalLaps());
        for (BlockPos p : route) {
            RaceGateBlockEntity gate = gateAt(level, p);
            if (gate == null) return null;
            appendGeometry(sb, p, gate.getSize(), gate.getYaw(), gate.getPitch(), gate.getOffset());
        }
        sb.append("|finish=").append(finishPos != null ? finishPos.asLong() : "start");
        if (finishPos != null) {
            Vec3 finishNormal = finishNormal();
            sb.append(':').append(Double.doubleToLongBits(finishNormal.x))
                .append(':').append(Double.doubleToLongBits(finishNormal.y))
                .append(':').append(Double.doubleToLongBits(finishNormal.z));
        }
        return sb.toString();
    }

    private static void appendGeometry(StringBuilder sb, BlockPos pos, int size, int yaw, int pitch, int offset) {
        sb.append('|').append(pos.asLong()).append(':').append(size).append(':').append(yaw)
            .append(':').append(pitch).append(':').append(offset);
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
        int r = GhostSavedData.get(level).submit(prog.courseKey, pilot.getUUID(), rec);
        if ((r & 2) != 0) {
            pilot.sendSystemMessage(Component.translatable("msg.fpv.race_record_overall"));
        } else if ((r & 1) != 0) {
            pilot.sendSystemMessage(Component.translatable("msg.fpv.race_record_personal"));
        }
    }

    private void sendGhosts(ServerLevel level, ServerPlayer pilot, long startTime) {
        String key = activeCourseKey;
        GhostSavedData data = GhostSavedData.get(level);
        GhostRecording personal = data.personalBest(key, pilot.getUUID());
        GhostRecording overall = data.overallBest(key);
        boolean same = personal != null && personal.sameRecording(overall);
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

    private void pushHud(ServerPlayer pilot, Progress prog, boolean force) {
        if (!force && prog.lastSentLap == prog.lap && prog.lastSentGate == prog.nextGate) return;
        prog.lastSentLap = prog.lap;
        prog.lastSentGate = prog.nextGate;
        PacketDistributor.sendToPlayer(pilot,
            new RaceTimerPacket(prog.lap, totalLaps(), prog.startTime, prog.nextGate, route.size(), -1));
    }

    private void recordResult(Result r) {
        Result previous = results.stream()
            .filter(existing -> Objects.equals(existing.courseKey(), r.courseKey()) && samePlayer(existing, r))
            .findFirst().orElse(null);
        if (previous != null && previous.timeTicks() <= r.timeTicks()) return;
        if (previous != null) results.remove(previous);
        results.add(r);
        results.sort((a, b) -> Integer.compare(a.timeTicks(), b.timeTicks()));
        while (results.size() > MAX_RESULTS) results.remove(results.size() - 1);
        sync();
    }

    private static boolean samePlayer(Result a, Result b) {
        if (a.playerId() != null && b.playerId() != null) return a.playerId().equals(b.playerId());
        return a.player().equals(b.player());
    }

    private static RaceGateBlockEntity gateAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
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
            UUID playerId = c.hasUUID("PlayerId") ? c.getUUID("PlayerId") : null;
            String key = c.contains("CourseKey") ? c.getString("CourseKey") : null;
            results.add(new Result(c.getString("Player"), playerId, c.getInt("Time"), c.getInt("Laps"), key));
        }
        route.clear();
        ListTag r = tag.getList("Route", Tag.TAG_LONG);
        for (int i = 0; i < r.size(); i++) route.add(BlockPos.of(((LongTag) r.get(i)).getAsLong()));
        finishPos = tag.contains("FinishPos") ? BlockPos.of(tag.getLong("FinishPos")) : null;
        activeCourseKey = tag.contains("CourseKey") ? tag.getString("CourseKey") : null;
        courseDirty = tag.getBoolean("CourseDirty");
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
            if (r.playerId() != null) c.putUUID("PlayerId", r.playerId());
            c.putInt("Time", r.timeTicks());
            c.putInt("Laps", r.laps());
            if (r.courseKey() != null) c.putString("CourseKey", r.courseKey());
            list.add(c);
        }
        tag.put("Results", list);
        ListTag r = new ListTag();
        for (BlockPos p : route) r.add(LongTag.valueOf(p.asLong()));
        tag.put("Route", r);
        if (finishPos != null) tag.putLong("FinishPos", finishPos.asLong());
        if (activeCourseKey != null) tag.putString("CourseKey", activeCourseKey);
        tag.putBoolean("CourseDirty", courseDirty);
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
