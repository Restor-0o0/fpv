package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.NpcDroneEntity;

// Spawner logic for the drone nest: while a player is within activation range, launches a
// hostile drone every 8-14 seconds, as long as fewer than MAX_ALIVE of its drones are still
// flying nearby. ~70% kamikaze minis, 30% patrol drones.
public class DroneNestBlockEntity extends BlockEntity {
    private static final double ACTIVATION_RANGE = 40.0;
    private static final double COUNT_RADIUS = 24.0;
    private static final int MAX_ALIVE = 4;
    private static final int MIN_DELAY = 160;   // ticks (8 s)
    private static final int MAX_DELAY = 280;   // ticks (14 s)

    private int cooldown = 60; // short initial delay after placing

    public DroneNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_NEST.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneNestBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = MIN_DELAY + level.random.nextInt(MAX_DELAY - MIN_DELAY);

        // Needs a (non-creative-flying-away…) survival/adventure player near to activate.
        if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5, ACTIVATION_RANGE, false) == null) return;

        // Cap the swarm: count this nest's kind of drones still alive around it.
        int alive = level.getEntitiesOfClass(NpcDroneEntity.class,
            new AABB(worldPosition).inflate(COUNT_RADIUS)).size();
        if (alive >= MAX_ALIVE) return;

        spawnDrone(level);
    }

    private void spawnDrone(ServerLevel level) {
        NpcDroneEntity drone = level.random.nextFloat() < 0.7f
            ? ModEntities.HOSTILE_DRONE.get().create(level)
            : ModEntities.PATROL_DRONE.get().create(level);
        if (drone == null) return;

        // Launch point: random air spot a few blocks above/around the nest.
        double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() * 2 - 1) * 2.0;
        double y = worldPosition.getY() + 1.5 + level.random.nextDouble() * 2.0;
        double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() * 2 - 1) * 2.0;
        drone.setPos(x, y, z);
        drone.setAnchor(worldPosition.above(2)); // patrol around the nest, not the launch offset
        drone.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.SPAWNER, null);
        level.addFreshEntity(drone);
        level.playSound(null, worldPosition, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Cooldown")) cooldown = tag.getInt("Cooldown");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Cooldown", cooldown);
    }
}
