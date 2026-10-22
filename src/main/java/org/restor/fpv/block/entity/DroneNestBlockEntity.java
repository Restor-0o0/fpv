package org.restor.fpv.block.entity;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.NpcDroneEntity;

import java.util.UUID;

// Spawner logic for the drone nest: while a player is within activation range, launches a
// hostile drone every 8-14 seconds, as long as fewer than MAX_ALIVE of its drones are still
// flying nearby. ~70% kamikaze minis, 30% patrol drones.
public class DroneNestBlockEntity extends BlockEntity {
    private static final double ACTIVATION_RANGE = 40.0;
    private static final double COUNT_RADIUS = 48.0;
    private static final int MAX_ALIVE = 4;
    private static final int MIN_DELAY = 160;   // ticks (8 s)
    private static final int MAX_DELAY = 280;   // ticks (14 s)

    private int cooldown = 60; // short initial delay after placing
    private UUID nestId = UUID.randomUUID();

    public DroneNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_NEST.get(), pos, state);
    }

    /** Action-bar status line: how many of this nest's drones fly, and when the next one launches. */
    public net.minecraft.network.chat.Component status(Level level) {
        int alive = level instanceof ServerLevel sl ? sl.getEntitiesOfClass(NpcDroneEntity.class,
            new AABB(worldPosition).inflate(COUNT_RADIUS),
            drone -> drone.isOwnedByNest(worldPosition, nestId)).size() : 0;
        boolean near = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5, ACTIVATION_RANGE, false) != null;
        return net.minecraft.network.chat.Component.translatable(
            near ? "msg.fpv.nest_status" : "msg.fpv.nest_idle",
            alive, MAX_ALIVE, Math.max(0, cooldown) / 20);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneNestBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        cooldown = MIN_DELAY + level.getRandom().nextInt(MAX_DELAY - MIN_DELAY);

        // Needs a (non-creative-flying-away…) survival/adventure player near to activate.
        if (level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5, ACTIVATION_RANGE, false) == null) return;

        int alive = level.getEntitiesOfClass(NpcDroneEntity.class,
            new AABB(worldPosition).inflate(COUNT_RADIUS),
            drone -> drone.isOwnedByNest(worldPosition, nestId)).size();
        if (alive >= MAX_ALIVE) return;

        spawnDrone(level);
    }

    private void spawnDrone(ServerLevel level) {
        NpcDroneEntity drone = level.getRandom().nextFloat() < 0.7f
            ? ModEntities.HOSTILE_DRONE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT)
            : ModEntities.PATROL_DRONE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (drone == null) return;

        Vec3 launch = findLaunchPoint(level, drone);
        if (launch == null) return;
        drone.setPos(launch.x, launch.y, launch.z);
        drone.setAnchor(worldPosition.above(2));
        drone.setOwnerNest(worldPosition, nestId);
        drone.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), EntitySpawnReason.SPAWNER, null);
        drone.setPersistenceRequired();
        level.addFreshEntity(drone);
        level.playSound(null, worldPosition, org.restor.fpv.sound.ModSounds.NEST_DEPLOY.get(), SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    private Vec3 findLaunchPoint(ServerLevel level, NpcDroneEntity drone) {
        Vec3 origin = Vec3.atCenterOf(worldPosition.above(2));
        for (int i = 0; i < 16; i++) {
            Vec3 candidate = origin.add(
                (level.getRandom().nextDouble() * 2 - 1) * 3.0,
                level.getRandom().nextDouble() * 2.5,
                (level.getRandom().nextDouble() * 2 - 1) * 3.0);
            drone.setPos(candidate.x, candidate.y, candidate.z);
            if (level.noCollision(drone, drone.getBoundingBox())
                && level.clip(new net.minecraft.world.level.ClipContext(origin, candidate,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, drone)).getType()
                    == net.minecraft.world.phys.HitResult.Type.MISS) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cooldown = input.getIntOr("Cooldown", cooldown);
        input.read("NestId", UUIDUtil.CODEC).ifPresent(id -> nestId = id);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Cooldown", cooldown);
        output.store("NestId", UUIDUtil.CODEC, nestId);
    }
}
