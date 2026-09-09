package org.restor.fpv.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.VillageDroneEntity;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worldgen-time drone presence: villages get air defence of their own, and the wilderness
 * gets the occasional hostile nest to fly into.
 */
@Mod.EventBusSubscriber(modid = Fpv.MODID)
public final class WorldSpawns {
    private static final int NEST_CHANCE = 700;
    private static final int VILLAGE_GUARD_CHANCE = 4;
    private static final int POPULATE_PER_TICK = 8;
    private static final int MAX_QUEUED = 4096;

    private static final Map<ResourceKey<Level>, ArrayDeque<ChunkPos>> PENDING = new ConcurrentHashMap<>();

    private WorldSpawns() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        ChunkPos pos = event.getChunk().getPos();
        ArrayDeque<ChunkPos> queue = PENDING.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        synchronized (queue) {
            while (queue.size() >= MAX_QUEUED) queue.removeFirst();
            queue.addLast(pos);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            ArrayDeque<ChunkPos> queue = PENDING.get(level.dimension());
            if (queue == null) continue;
            for (int i = 0; i < POPULATE_PER_TICK; i++) {
                ChunkPos pos;
                synchronized (queue) {
                    pos = queue.pollFirst();
                }
                if (pos == null) break;
                populate(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level.dimension());
    }

    private static void populate(ServerLevel level, ChunkPos chunkPos) {
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) return;
        RandomSource random = level.getRandom();
        BlockPos center = chunkPos.getMiddleBlockPosition(level.getSeaLevel());
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, center);

        if (level.structureManager().getStructureWithPieceAt(center, StructureTags.VILLAGE).isValid()) {
            if (random.nextInt(VILLAGE_GUARD_CHANCE) != 0) return;
            VillageDroneEntity guard = ModEntities.VILLAGE_DRONE.get().create(level);
            if (guard == null) return;
            guard.setPos(surface.getX() + 0.5, surface.getY() + 7.0, surface.getZ() + 0.5);
            guard.setAnchor(surface.above(7));
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(surface),
                MobSpawnType.CHUNK_GENERATION, null, null);
            level.addFreshEntity(guard);
            return;
        }

        if (random.nextInt(NEST_CHANCE) != 0) return;
        if (surface.getY() <= level.getMinBuildHeight() + 1) return;
        if (!level.getBlockState(surface).canBeReplaced()) return;
        if (!level.getBlockState(surface.below()).isSolidRender(level, surface.below())) return;
        level.setBlockAndUpdate(surface, ModBlocks.DRONE_NEST.get().defaultBlockState());
    }
}
