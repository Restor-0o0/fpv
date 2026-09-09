package org.restor.fpv.entity;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.restor.fpv.Fpv;

@Mod.EventBusSubscriber(modid = Fpv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModSpawnPlacements {
    private ModSpawnPlacements() {}

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SpawnPlacements.register(ModEntities.HOSTILE_DRONE.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::canSpawn);
            SpawnPlacements.register(ModEntities.PATROL_DRONE.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::canSpawn);
            SpawnPlacements.register(ModEntities.INTERCEPTOR_DRONE.get(), SpawnPlacements.Type.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::canSpawn);
        });
    }

    private static <T extends net.minecraft.world.entity.Mob> boolean canSpawn(
            net.minecraft.world.entity.EntityType<T> type,
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.entity.MobSpawnType spawnType,
            net.minecraft.core.BlockPos pos,
            net.minecraft.util.RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        return pos.getY() >= level.getLevel().getSeaLevel() - 4 && random.nextInt(3) == 0;
    }
}
