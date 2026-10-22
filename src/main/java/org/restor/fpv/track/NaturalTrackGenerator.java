package org.restor.fpv.track;

import net.minecraft.resources.Identifier;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.TrackGenerationConfig;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.block.RaceGateBlock;
import org.restor.fpv.block.RaceStartBlock;
import org.restor.fpv.block.entity.RaceGateBlockEntity;
import org.restor.fpv.block.entity.RaceStartBlockEntity;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Fpv.MODID)
public final class NaturalTrackGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, PendingRegions> PENDING = new ConcurrentHashMap<>();
    private static final RoutePlanner PLANNER = new RoutePlanner();

    @SubscribeEvent
    public static void addReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Fpv.MODID, "track_profiles"), new TrackProfiles());
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!TrackGenerationConfig.ENABLED.get() || !(event.getLevel() instanceof ServerLevel level)
            || !level.dimension().equals(Level.OVERWORLD)) return;
        ChunkPos chunk = event.getChunk().getPos();
        int spacing = TrackGenerationConfig.SPACING_CHUNKS.get();
        int regionX = Math.floorDiv(chunk.x, spacing);
        int regionZ = Math.floorDiv(chunk.z, spacing);
        long region = ChunkPos.asLong(regionX, regionZ);
        long seed = regionSeed(level.getSeed(), regionX, regionZ);
        if (unitDouble(seed) >= TrackGenerationConfig.REGION_CHANCE.get()) return;
        int targetX = regionX * spacing + Math.floorMod(mix(seed ^ 0x31f2a9b47c6d8e05L), spacing);
        int targetZ = regionZ * spacing + Math.floorMod(mix(seed ^ 0x7c15d3e9a4206bf1L), spacing);
        if (chunk.x == targetX && chunk.z == targetZ) {
            PENDING.computeIfAbsent(level.dimension(), key -> new PendingRegions())
                .offer(region, TrackGenerationConfig.MAX_PENDING_REGIONS.get());
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!TrackGenerationConfig.ENABLED.get()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) processOne(level);
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level.dimension());
    }

    private static void processOne(ServerLevel level) {
        PendingRegions pending = PENDING.get(level.dimension());
        if (pending == null || pending.isEmpty()) return;
        TrackGenerationSavedData data = TrackGenerationSavedData.get(level);
        int scans = Math.min(pending.size(), TrackGenerationConfig.PENDING_SCAN_PER_TICK.get());
        for (int i = 0; i < scans; i++) {
            Long region = pending.poll();
            if (region == null) return;
            if (data.wasAttempted(region)) continue;

            int regionX = ChunkPos.getX(region);
            int regionZ = ChunkPos.getZ(region);
            int spacing = TrackGenerationConfig.SPACING_CHUNKS.get();
            long seed = regionSeed(level.getSeed(), regionX, regionZ);
            int targetChunkX = regionX * spacing + Math.floorMod(mix(seed ^ 0x31f2a9b47c6d8e05L), spacing);
            int targetChunkZ = regionZ * spacing + Math.floorMod(mix(seed ^ 0x7c15d3e9a4206bf1L), spacing);
            TrackProfile profile = TrackProfiles.select(seed);
            if (profile == null) {
                pending.offer(region, TrackGenerationConfig.MAX_PENDING_REGIONS.get());
                continue;
            }
            int chunkReach = (int) Math.ceil((profile.radius() * (1.0 + profile.radialJitter())
                + profile.radius() / 3.0 + profile.clearanceRadius() + 2.0) / 16.0);
            if (!areaLoaded(level, targetChunkX, targetChunkZ, chunkReach)) {
                pending.offer(region, TrackGenerationConfig.MAX_PENDING_REGIONS.get());
                continue;
            }

            if (!data.markAttempted(region)) continue;
            int originX = targetChunkX * 16 + 8;
            int originZ = targetChunkZ * 16 + 8;
            PLANNER.plan(seed, originX, originZ, profile, new LevelTerrain(level),
                    TrackGenerationConfig.PLANNER_TERRAIN_CHECK_BUDGET.get())
                .filter(plan -> place(level, plan, profile))
                .ifPresent(plan -> LOGGER.info("Generated FPV track at {} in {}", plan.start().anchor(), level.dimension().identifier()));
            return;
        }
    }

    private static boolean place(ServerLevel level, RoutePlanner.PlacementPlan plan, TrackProfile profile) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(toBlockPos(plan.start().anchor()));
        for (RoutePlanner.Node gate : plan.gates()) positions.add(toBlockPos(gate.anchor()));
        if (Set.copyOf(positions).size() != positions.size()) return false;
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            BlockState original = level.getBlockState(pos);
            if (!original.canBeReplaced() || !level.getFluidState(pos).isEmpty()) return false;
            originals.put(pos.immutable(), original);
        }

        BlockPos startPos = positions.get(0);
        BlockState startState = ModBlocks.RACE_START.get().defaultBlockState()
            .setValue(RaceStartBlock.FACING, facing(plan.start().yaw()));
        if (!level.setBlock(startPos, startState, 3)) return false;
        for (int i = 0; i < plan.gates().size(); i++) {
            RoutePlanner.Node gate = plan.gates().get(i);
            BlockPos gatePos = positions.get(i + 1);
            BlockState gateState = ModBlocks.RACE_GATE.get().defaultBlockState()
                .setValue(RaceGateBlock.FACING, facing(gate.yaw()));
            if (!level.setBlock(gatePos, gateState, 3)) {
                rollback(level, originals);
                return false;
            }
        }

        for (int i = 0; i < plan.gates().size(); i++) {
            if (!(level.getBlockEntity(positions.get(i + 1)) instanceof RaceGateBlockEntity gate)) {
                rollback(level, originals);
                return false;
            }
            gate.configureGenerated(profile.gateSize(), plan.gates().get(i).yaw());
        }
        if (!(level.getBlockEntity(startPos) instanceof RaceStartBlockEntity start)
            || !start.installGeneratedRoute(positions.subList(1, positions.size()), profile.laps(), profile.gateSize(), plan.start().yaw())) {
            rollback(level, originals);
            return false;
        }
        return true;
    }

    private static void rollback(ServerLevel level, Map<BlockPos, BlockState> originals) {
        originals.forEach((pos, state) -> level.setBlock(pos, state, 3));
    }

    private static boolean areaLoaded(ServerLevel level, int centerX, int centerZ, int reach) {
        for (int x = centerX - reach; x <= centerX + reach; x++) {
            for (int z = centerZ - reach; z <= centerZ + reach; z++) if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }

    private static BlockPos toBlockPos(RoutePlanner.Point point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }

    private static Direction facing(int yaw) {
        int quadrant = Math.floorMod((yaw + 45) / 90, 4);
        return switch (quadrant) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static long regionSeed(long worldSeed, int regionX, int regionZ) {
        return mix(worldSeed ^ (regionX * 0x9e3779b97f4a7c15L) ^ (regionZ * 0xc2b2ae3d27d4eb4fL));
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static double unitDouble(long value) {
        return (mix(value) >>> 11) * 0x1.0p-53;
    }

    private record LevelTerrain(ServerLevel level) implements RoutePlanner.Terrain {
        @Override
        public RoutePlanner.Surface surface(int x, int z) {
            int airY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int groundY = airY - 1;
            if (groundY < level.getMinY() || groundY >= level.getMaxY()) {
                return new RoutePlanner.Surface(groundY, false);
            }
            BlockPos ground = new BlockPos(x, groundY, z);
            BlockState state = level.getBlockState(ground);
            boolean safe = level.getFluidState(ground).isEmpty()
                && !state.getCollisionShape(level, ground).isEmpty()
                && state.isFaceSturdy(level, ground, Direction.UP);
            return new RoutePlanner.Surface(groundY, safe);
        }

        @Override
        public boolean isClear(int x, int y, int z) {
            if (y < level.getMinY() || y > level.getMaxY()) return false;
            BlockPos pos = new BlockPos(x, y, z);
            return level.getFluidState(pos).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        @Override
        public boolean canPlaceAnchor(int x, int y, int z) {
            if (y < level.getMinY() || y > level.getMaxY()) return false;
            BlockPos pos = new BlockPos(x, y, z);
            return level.getFluidState(pos).isEmpty() && level.getBlockState(pos).canBeReplaced();
        }
    }

    private static final class PendingRegions {
        private final ArrayDeque<Long> queue = new ArrayDeque<>();
        private final Set<Long> entries = new HashSet<>();

        synchronized void offer(long region, int limit) {
            if (entries.contains(region)) return;
            while (queue.size() >= limit) entries.remove(queue.removeFirst());
            queue.addLast(region);
            entries.add(region);
        }

        synchronized Long poll() {
            Long region = queue.pollFirst();
            if (region != null) entries.remove(region);
            return region;
        }

        synchronized int size() {
            return queue.size();
        }

        synchronized boolean isEmpty() {
            return queue.isEmpty();
        }
    }

    private NaturalTrackGenerator() {}
}
