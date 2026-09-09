package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// Delta racer: very fast and thrusty, but it needs real speed before the small wing bites.
public class DeltaPlaneEntity extends PlaneEntity {
    public DeltaPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 5.5f;
        this.maxThrust = 520f;
        this.stallSpeed = 9.0f;
        this.liftCoeff = 0.09f;
        this.maxLiftAccel = GRAVITY * 1.8f;
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_DELTA.get();
    }
}
