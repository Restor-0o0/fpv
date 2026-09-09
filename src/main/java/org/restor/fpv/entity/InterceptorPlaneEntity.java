package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.warhead.WarheadType;

// Anti-drone interceptor: tiny, brutally fast, fragmentation head. Detonates the moment it
// touches anything, so it is flown straight through the target.
public class InterceptorPlaneEntity extends KamikazeWingEntity {
    private static final double CATCH_RADIUS = 2.2;

    public InterceptorPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 4.5f;
        this.maxThrust = 640f;
        this.stallSpeed = 5.0f;
        this.liftCoeff = 0.28f;
        this.maxLiftAccel = GRAVITY * 2.0f;
        this.dryMass = 18f;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive() || !isArmed()) return;
        var prey = level().getEntities(this, getBoundingBox().inflate(CATCH_RADIUS),
            e -> (e instanceof NpcDroneEntity || e instanceof DroneEntity) && e.isAlive() && !hasPassenger(e));
        if (!prey.isEmpty()) hurt(damageSources().explosion(this, this), 100f);
    }

    @Override
    public float getModelScale() { return 0.8f; }

    @Override
    protected WarheadType defaultWarhead() { return WarheadType.FRAG; }

    @Override
    public float warheadCapacityKg() { return 3.0f; }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_INTERCEPTOR.get();
    }
}
