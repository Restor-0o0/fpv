package org.restor.fpv.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
    private static final EntityDataAccessor<Boolean> DATA_HUNTING =
        SynchedEntityData.defineId(InterceptorDroneEntity.class, EntityDataSerializers.BOOLEAN);
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
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HUNTING, false);
    }

    @Override
    protected NpcDroneBuilds.Role buildRole() { return NpcDroneBuilds.Role.INTERCEPTOR; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(3, new PatrolGoal());
        // No target selector: prey (a non-living DroneEntity) is tracked manually below.
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        // Validate / refresh prey.
        if (prey != null && (!prey.isAlive() || !prey.hasAnyPilot())) {
            prey = null;
            beeped = false;
            setHunting(false);
        }
        if (prey == null && tickCount % SCAN_INTERVAL == 0) {
            prey = findPreyDrone();
            setHunting(prey != null);
        }
        if (prey == null) return;

        if (!beeped) {
            beeped = true;
            playSound(org.restor.fpv.sound.ModSounds.DETECTOR_ALERT.get(), 1.2f, 2.0f);
        }
        // Intercept course: aim ahead of the prey, but only as far ahead as the closing time
        // justifies, or a fast pass sends the interceptor chasing empty air.
        double closing = Math.max(1.0, Math.sqrt(distanceToSqr(prey)) / 0.9);
        var lead = prey.position().add(prey.getDeltaMovement().scale(Math.min(closing, 8.0)));
        setFlightTarget(lead, 1.6);

        if (distanceToSqr(prey) < HIT_DIST_SQR) {
            // The interception IS the warhead going off: the blast is what wrecks the prey,
            // so the ram damage stays small and the unit never survives its own run.
            playSound(org.restor.fpv.sound.ModSounds.CRASH.get(), 1.0f, 0.8f);
            prey.hurt(damageSources().mobAttack(this), 2.0f);
            WarheadDetonation.detonate(level(), this, position(), org.restor.fpv.warhead.WarheadType.FRAG);
            discard();
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
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        // Getting hit drops the lock — it re-acquires on the next scan if the drone is still close.
        boolean result = super.hurtServer(level, source, amount);
        if (result) {
            prey = null;
            beeped = false;
            setHunting(false);
        }
        return result;
    }

    public boolean isHunting() {
        return entityData.get(DATA_HUNTING);
    }

    private void setHunting(boolean hunting) {
        if (!level().isClientSide() && entityData.get(DATA_HUNTING) != hunting) {
            entityData.set(DATA_HUNTING, hunting);
        }
    }
}
