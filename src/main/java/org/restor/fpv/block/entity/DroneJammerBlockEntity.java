package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.CommonConfig;
import org.restor.fpv.antidrone.AntiDronePolicy;
import org.restor.fpv.block.DroneJammerBlock;
import org.restor.fpv.antidrone.RadioInterferenceReceiver;
import org.restor.fpv.component.RadioChannel;

public class DroneJammerBlockEntity extends OwnedAntiDroneBlockEntity {
    private int channel = RadioChannel.SWEEP;

    public DroneJammerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_JAMMER.get(), pos, state);
    }

    public int channel() {
        return channel;
    }

    public int cycleChannel() {
        channel = RadioChannel.nextWithSweep(channel);
        setChanged();
        return channel;
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.contains("Channel") ? tag.getInt("Channel") : RadioChannel.SWEEP;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Channel", channel);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneJammerBlockEntity jammer) {
        if (!state.getValue(DroneJammerBlock.POWERED)) return;
        int interval = CommonConfig.JAMMER_SCAN_INTERVAL_TICKS.get();
        if (Math.floorMod(level.getGameTime() + pos.asLong(), interval) != 0) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 source = Vec3.atCenterOf(pos);
        double radius = CommonConfig.JAMMER_RADIUS.get();
        float maximumStrength = CommonConfig.JAMMER_MAXIMUM_STRENGTH.get().floatValue();
        AABB scanBox = AABB.ofSize(source, radius * 2.0, radius * 2.0, radius * 2.0);
        for (net.minecraft.world.entity.Entity craft : serverLevel.getEntitiesOfClass(
            net.minecraft.world.entity.Entity.class, scanBox,
            e -> e.isAlive() && e instanceof RadioInterferenceReceiver r
                && RadioChannel.reaches(jammer.channel, r.radioChannel())
                && jammer.canTarget(serverLevel, r))) {
            float strength = AntiDronePolicy.falloff(craft.position().distanceTo(source), radius, maximumStrength);
            if (strength > 0.0f) {
                ((RadioInterferenceReceiver) craft).applyRadioInterference(pos.asLong(), strength, interval + 2);
            }
        }
    }
}
