package org.restor.fpv.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.VillageDroneEntity;

/**
 * Worldgen-time drone presence: villages get air defence of their own, and the wilderness
 * gets the occasional hostile nest to fly into.
 */
@EventBusSubscriber(modid = Fpv.MODID)
public final class WorldSpawns {
    private static final int NEST_CHANCE = 700;
    private static final int VILLAGE_GUARD_CHANCE = 4;

    private WorldSpawns() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;
        ChunkPos pos = event.getChunk().getPos();
        // The chunk is still being built here, so entities and blocks wait for the next tick.
        level.getServer().execute(() -> populate(level, pos));
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
                MobSpawnType.CHUNK_GENERATION, null);
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
