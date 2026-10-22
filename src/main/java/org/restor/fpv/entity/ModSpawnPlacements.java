package org.restor.fpv.entity;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.restor.fpv.Fpv;

@EventBusSubscriber(modid = Fpv.MODID)
public final class ModSpawnPlacements {
    private static final boolean NATURAL_SPAWNS = false;

    private ModSpawnPlacements() {}

    @SubscribeEvent
    public static void register(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.HOSTILE_DRONE.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.PATROL_DRONE.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntities.INTERCEPTOR_DRONE.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static <T extends net.minecraft.world.entity.Mob> boolean canSpawn(
            net.minecraft.world.entity.EntityType<T> type,
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.entity.EntitySpawnReason spawnType,
            net.minecraft.core.BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (!NATURAL_SPAWNS && (spawnType == net.minecraft.world.entity.EntitySpawnReason.NATURAL || spawnType == net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION)) return false;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        return pos.getY() >= level.getLevel().getSeaLevel() - 4 && random.nextInt(3) == 0;
    }
}
