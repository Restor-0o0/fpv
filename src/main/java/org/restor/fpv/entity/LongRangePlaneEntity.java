package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.warhead.WarheadType;

// Long-range pusher strike aircraft: big straight wing, high cruise, thermobaric load.
public class LongRangePlaneEntity extends KamikazeWingEntity {
    public LongRangePlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 3.0f;
        this.maxThrust = 420f;
        this.stallSpeed = 4.5f;
        this.liftCoeff = 0.55f;
        this.maxLiftAccel = GRAVITY * 1.5f;
        this.dryMass = 50f;
    }

    @Override
    public float getModelScale() { return 1.35f; }

    @Override
    protected WarheadType defaultWarhead() { return WarheadType.THERMOBARIC; }

    @Override
    public float warheadCapacityKg() { return 8.0f; }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_LONGRANGE.get();
    }
}
