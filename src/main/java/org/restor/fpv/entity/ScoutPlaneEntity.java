package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// A slow, stable trainer wing: cruises comfortably around 30 m/s and holds altitude there
// (low stall speed + strong lift), capped at ~32 m/s so it stays easy to fly.
public class ScoutPlaneEntity extends PlaneEntity {
    private long nextStrikeGameTime;

    public ScoutPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 2.0f;              // ~40 m/s cap
        this.maxThrust = 220f;               // ~7.3 m/s² — enough to leave the ground quickly
        this.stallSpeed = 3.0f;
        this.liftCoeff = 1.2f;               // lift balances gravity by ~6.5 m/s, so it takes off almost at once
        this.maxLiftAccel = GRAVITY * 1.6f;
    }

    public boolean tryStartStrike() {
        long now = level().getGameTime();
        if (now < nextStrikeGameTime) return false;
        nextStrikeGameTime = now + 200;
        return true;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_SCOUT.get();
    }
}
