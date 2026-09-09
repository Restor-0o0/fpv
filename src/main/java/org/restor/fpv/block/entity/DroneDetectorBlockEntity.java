package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.CommonConfig;
import org.restor.fpv.block.DroneDetectorBlock;
import org.restor.fpv.antidrone.RadioInterferenceReceiver;

public class DroneDetectorBlockEntity extends OwnedAntiDroneBlockEntity {
    public DroneDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_DETECTOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneDetectorBlockEntity detector) {
        int interval = CommonConfig.DETECTOR_SCAN_INTERVAL_TICKS.get();
        if (Math.floorMod(level.getGameTime() + pos.asLong(), interval) != 0) return;

        ServerLevel serverLevel = (ServerLevel) level;
        double radius = CommonConfig.DETECTOR_RADIUS.get();
        Vec3 center = Vec3.atCenterOf(pos);
        AABB scanBox = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0);
        boolean detected = !serverLevel.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, scanBox,
            e -> e.isAlive() && e.position().distanceToSqr(center) < radius * radius
                && e instanceof RadioInterferenceReceiver r && detector.canTarget(serverLevel, r)).isEmpty();
        if (state.getValue(DroneDetectorBlock.DETECTED) == detected) return;

        BlockState updated = state.setValue(DroneDetectorBlock.DETECTED, detected);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, updated.getBlock());
        if (detected) {
            level.playSound(null, pos, org.restor.fpv.sound.ModSounds.DETECTOR_ALERT.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.6f);
        }
    }
}
