package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// A slow, stable trainer wing: cruises comfortably around 30 m/s and holds altitude there
// (low stall speed + strong lift), capped at ~32 m/s so it stays easy to fly.
public class ScoutPlaneEntity extends PlaneEntity {
    public ScoutPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 1.6f;              // ~32 m/s cap (reaches the ~30 target)
        this.maxThrust = 180f;               // enough punch to reach the cap
        this.stallSpeed = 3.5f;              // lift comes in at low speed
        this.liftCoeff = 0.22f;              // balances gravity by ~15 m/s → never falls at 30
        this.maxLiftAccel = GRAVITY_REF * 1.5f;
    }

    // GRAVITY is private in the base; expose the same value locally for tuning.
    private static final float GRAVITY_REF = 9.81f;

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_SCOUT.get();
    }
}
