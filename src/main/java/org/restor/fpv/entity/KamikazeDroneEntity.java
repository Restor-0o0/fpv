package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;

// Station-controlled drone that detonates like TNT on any block contact once airborne.
public class KamikazeDroneEntity extends RemoteDroneEntity {
    private int airborneTicks;
    private boolean exploded;
    private int explosionViewTicks;
    private net.minecraft.world.phys.Vec3 explosionLookTarget;

    public KamikazeDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.RACING);
    }

    @Override
    public void tick() {
        if (explosionViewTicks > 0) {
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            super.tick();
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (explosionLookTarget != null) KamikazeExplosionEffects.lookAt(this, explosionLookTarget);
            if (--explosionViewTicks <= 0) {
                endRemoteControl();
                discard();
            }
            return;
        }
        super.tick();
        if (level().isClientSide) return;

        if (flightController.isArmed() && !onGround()) {
            airborneTicks++;
        } else if (!flightController.isArmed()) {
            airborneTicks = 0;
        }

        // Grace period so arming/takeoff from the ground doesn't detonate instantly.
        if (airborneTicks > 10 && (horizontalCollision || verticalCollision || onGround())) {
            explodeNow();
        }
    }

    @Override
    protected void onEntityCollision() {
        // Ramming a living target detonates the warhead (same takeoff grace).
        if (flightController.isArmed() && airborneTicks > 10) {
            explodeNow();
        }
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.KAMIKAZE_DRONE.get();
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        // It's a flying warhead: explosions and fire set it off (chain reactions).
        if (!level().isClientSide && (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
            || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE))) {
            explodeNow();
            return true;
        }
        return super.hurt(source, amount);
    }

    private void explodeNow() {
        if (exploded) return;
        exploded = true;
        float power = (float) org.restor.fpv.Config.DEV_EXPLOSION_POWER.get().doubleValue();
        boolean cinematic = org.restor.fpv.Config.DEV_EXPLOSION_CAMERA_BACKSTEP.get() > 0.0;
        net.minecraft.world.phys.Vec3 impact = cinematic ? KamikazeExplosionEffects.moveCameraBack(this) : position();
        if (!cinematic) endRemoteControl();
        KamikazeExplosionEffects.flingBlocks(level(), impact.x, impact.y, impact.z, power);
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> before =
            KamikazeExplosionEffects.captureBreakableBlocks(level(), impact.x, impact.y, impact.z, power);
        level().explode(this, impact.x, impact.y, impact.z, power, Level.ExplosionInteraction.TNT);
        KamikazeExplosionEffects.restoreUnbrokenBlocks(level(), before, impact.x, impact.y, impact.z);
        KamikazeExplosionEffects.removeFloatingBlocks(level(), impact.x, impact.y, impact.z, power);
        KamikazeExplosionEffects.damageMobs(level(), this, impact.x, impact.y, impact.z, power);
        KamikazeExplosionEffects.spawn(level(), impact.x, impact.y, impact.z, power);
        if (cinematic) {
            explosionLookTarget = impact;
            explosionViewTicks = Math.max(0, (int) Math.round(org.restor.fpv.Config.DEV_EXPLOSION_CAMERA_SECONDS.get() * 20.0));
            if (explosionViewTicks <= 0) {
                endRemoteControl();
                discard();
            }
        } else {
            discard();
        }
    }
}
