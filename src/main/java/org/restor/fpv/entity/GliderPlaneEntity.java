package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Trainer glider: a big slow wing that floats on almost no thrust, forgiving to fly.
public class GliderPlaneEntity extends PlaneEntity {
    public GliderPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 1.5f;
        this.maxThrust = 110f;
        this.stallSpeed = 2.0f;
        this.liftCoeff = 2.0f;
        this.maxLiftAccel = GRAVITY * 1.35f;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_GLIDER.get();
    }
}
