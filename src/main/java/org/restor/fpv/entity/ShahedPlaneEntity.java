package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.warhead.WarheadType;

// Moped-wing loitering munition: slow, patient and hard to turn, but it carries a real
// warhead and holds a straight line forever.
public class ShahedPlaneEntity extends KamikazeWingEntity {
    public ShahedPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 1.7f;
        this.maxThrust = 300f;
        this.stallSpeed = 3.4f;
        this.liftCoeff = 1.1f;
        this.maxLiftAccel = GRAVITY * 1.3f;
        this.dryMass = 42f;
    }

    @Override
    protected WarheadType defaultWarhead() { return WarheadType.HEAVY; }

    @Override
    public float warheadCapacityKg() { return 16.0f; }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_SHAHED.get();
    }
}
