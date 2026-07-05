package org.restor.fpv.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// A fast (~50 m/s) FPV wing that detonates like TNT on the first block/entity contact once
// airborne — a fixed-wing kamikaze. The pilot is returned to the launch point on impact.
public class KamikazeWingEntity extends PlaneEntity {
    private int airborneTicks;
    private boolean exploded;

    public KamikazeWingEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 2.5f;          // ~50 m/s cap
        this.maxThrust = 260f;           // reaches the cap quickly
        this.stallSpeed = 4.0f;
        this.liftCoeff = 0.10f;          // holds altitude comfortably at 50 m/s
        this.maxLiftAccel = 9.81f * 1.6f;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (isArmed() && !onGround()) airborneTicks++;
        else if (!isArmed()) airborneTicks = 0;

        // Grace period so arming/takeoff from the ground doesn't detonate instantly.
        if (airborneTicks > 10) {
            if (horizontalCollision || verticalCollision || onGround()) {
                explodeNow();
                return;
            }
            // Ramming a living target also detonates.
            var hits = level().getEntities(this, getBoundingBox().inflate(0.2),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && !hasPassenger(e));
            if (!hits.isEmpty()) explodeNow();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide && (source.is(DamageTypeTags.IS_EXPLOSION)
            || source.is(DamageTypeTags.IS_FIRE))) {
            explodeNow();
            return true;
        }
        return super.hurt(source, amount);
    }

    private void explodeNow() {
        if (exploded) return;
        exploded = true;
        // Return the pilot to their launch point before detonating (their body rides the
        // wing, so exploding first would blast them too).
        ejectPassengers();
        float power = (float) org.restor.fpv.Config.DEV_EXPLOSION_POWER.get().doubleValue();
        level().explode(this, getX(), getY(), getZ(), power, Level.ExplosionInteraction.TNT);
        discard();
    }
}
