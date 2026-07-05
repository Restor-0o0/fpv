package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
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
import net.minecraftforge.network.PacketDistributor;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.network.ModNetworking;
import org.restor.fpv.network.RaceTimerPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Start / finish line of a race course. Holds an ORDERED route of gate positions (built in
// marking mode), times a piloted vehicle over a set number of laps requiring every gate to
// be passed in sequence, and keeps a leaderboard of completed runs (name + time).
public class RaceStartBlockEntity extends BlockEntity {
    public static final int MAX_RESULTS = 15;
    private static final double START_RADIUS = 2.5;
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
    }

    private int laps = 3;
    private final List<Result> results = new ArrayList<>();
    private final List<BlockPos> route = new ArrayList<>();
    private BlockPos finishPos;

    private UUID markingPlayer; // transient: who is currently marking this route
    private final Map<UUID, Progress> runs = new java.util.HashMap<>();

    public RaceStartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACE_START.get(), pos, state);
    }

    public int getLaps() { return laps; }

    public void setLaps(int n) {
        laps = Math.max(1, Math.min(50, n));
        sync();
    }

    public int getRouteSize() { return route.size(); }
    public List<Result> getResults() { return List.copyOf(results); }

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
                BlockPos next = i + 1 < route.size() ? route.get(i + 1) : (finishPos != null ? finishPos : worldPosition);
                gate.setNextRoutePos(next);
            }
        }
        markingPlayer = null;
        runs.clear();
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
        Vec3 startCenter = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
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

            // Next gate in the ordered route (skip any that were removed).
            if (prog != null) {
                while (prog.nextGate < route.size()) {
                    RaceGateBlockEntity g = gateAt(level, route.get(prog.nextGate));
                    if (g == null) { prog.nextGate++; continue; }   // gate deleted → auto-clear
                    if (vp.distanceTo(g.center()) <= g.passRadius()) { prog.nextGate++; continue; }
                    break;
                }
            }

            boolean inStart = vp.distanceTo(startCenter) <= START_RADIUS;
            boolean entering = inStart && (prog == null || !prog.wasInStart);
            if (entering) {
                onCrossStart(level, pilot, prog);
                prog = runs.get(pilot.getUUID());
            }
            if (prog != null) {
                if (finishPos != null) {
                    boolean inFinish = vp.distanceTo(finishCenter) <= START_RADIUS;
                    if (inFinish && !prog.wasInFinish) onCrossFinish(level, pilot, prog);
                    if (!runs.containsKey(pilot.getUUID())) continue;
                    prog.wasInFinish = inFinish;
                }
                prog.wasInStart = inStart;
                pushHud(pilot, prog, false);
            }
        }
    }

    private void onCrossStart(ServerLevel level, ServerPlayer pilot, Progress prog) {
        if (prog == null) {
            startRun(level, pilot);
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
            startRun(level, pilot);
        }
    }

    private void onCrossFinish(ServerLevel level, ServerPlayer pilot, Progress prog) {
        if (prog.nextGate >= route.size()) finishRun(level, pilot, prog, 1);
    }

    private void finishRun(ServerLevel level, ServerPlayer pilot, Progress prog, int resultLaps) {
        int time = (int) (level.getGameTime() - prog.startTime);
        recordResult(new Result(pilot.getName().getString(), time, resultLaps));
        runs.remove(pilot.getUUID());
        pilot.sendSystemMessage(Component.translatable("msg.fpv.race_finish", formatTime(time)));
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pilot),
            new RaceTimerPacket(0, totalLaps(), prog.startTime, route.size(), route.size(), time));
    }

    private void startRun(ServerLevel level, ServerPlayer pilot) {
        Progress p = new Progress();
        p.startTime = level.getGameTime();
        p.lap = 1;
        p.nextGate = 0;
        p.wasInStart = true;
        runs.put(pilot.getUUID(), p);
        pilot.sendSystemMessage(finishPos != null
            ? Component.translatable("msg.fpv.race_start_straight")
            : Component.translatable("msg.fpv.race_start", laps));
        pushHud(pilot, p, true);
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
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> pilot),
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
    public void load(CompoundTag tag) {
        super.load(tag);
        laps = Math.max(1, tag.getInt("Laps"));
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Laps", laps);
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
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
