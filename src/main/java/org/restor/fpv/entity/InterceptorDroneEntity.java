package org.restor.fpv.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Interceptor Drone (FPV_IDEAS §3): the anti-FPV unit. Ignores players on foot — it hunts
 * PILOTED player drones: locks onto the nearest DroneEntity with an active pilot, chases it
 * and wrecks it on contact (any real hit breaks a drone into its item). Since vehicle drones
 * aren't LivingEntities, targeting bypasses the vanilla goal system and runs in tick().
 */
public class InterceptorDroneEntity extends NpcDroneEntity {
    private static final double SCAN_RADIUS = 32.0;
    private static final double HIT_DIST_SQR = 2.0;
    private static final int SCAN_INTERVAL = 10;

    private DroneEntity prey;
    private boolean beeped;

    public InterceptorDroneEntity(EntityType<? extends InterceptorDroneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 12.0)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(3, new PatrolGoal());
        // No target selector: prey (a non-living DroneEntity) is tracked manually below.
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // Validate / refresh prey.
        if (prey != null && (!prey.isAlive() || !prey.hasAnyPilot())) {
            prey = null;
            beeped = false;
        }
        if (prey == null && tickCount % SCAN_INTERVAL == 0) {
            prey = findPreyDrone();
        }
        if (prey == null) return;

        if (!beeped) {
            beeped = true;
            playSound(SoundEvents.BEE_LOOP_AGGRESSIVE, 1.2f, 2.0f);
        }
        // Intercept course: aim slightly ahead of the drone's velocity.
        var lead = prey.position().add(prey.getDeltaMovement().scale(6));
        moveControl.setWantedPosition(lead.x, lead.y, lead.z, 1.6);

        if (distanceToSqr(prey) < HIT_DIST_SQR) {
            // Any real damage breaks a drone (its hurt() drops the item and discards).
            prey.hurt(damageSources().mobAttack(this), 5.0f);
            playSound(SoundEvents.ITEM_BREAK, 1.0f, 0.8f);
            prey = null;
            beeped = false;
        }
    }

    private DroneEntity findPreyDrone() {
        List<DroneEntity> drones = level().getEntitiesOfClass(DroneEntity.class,
            new AABB(blockPosition()).inflate(SCAN_RADIUS),
            d -> d.isAlive() && d.hasAnyPilot());
        DroneEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (DroneEntity d : drones) {
            double dist = distanceToSqr(d);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // Getting hit drops the lock — it re-acquires on the next scan if the drone is still close.
        boolean result = super.hurt(source, amount);
        if (result) {
            prey = null;
            beeped = false;
        }
        return result;
    }

    /** Whether this interceptor is actively chasing a drone (renderer shows a hot LED). */
    public boolean isHunting() {
        return prey != null;
    }
}
