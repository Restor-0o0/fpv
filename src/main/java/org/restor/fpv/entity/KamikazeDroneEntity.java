package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;

// Station-controlled drone that detonates like TNT on any block contact once airborne.
public class KamikazeDroneEntity extends RemoteDroneEntity {
    private int airborneTicks;
    private boolean exploded;

    public KamikazeDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.RACING);
    }

    @Override
    public void tick() {
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
        // Return the pilot to the station BEFORE detonating — their body follows the
        // drone (chunk loading), so exploding first would blast the pilot too.
        endRemoteControl();
        float power = (float) org.restor.fpv.Config.DEV_EXPLOSION_POWER.get().doubleValue();
        level().explode(this, getX(), getY(), getZ(), power, Level.ExplosionInteraction.TNT);
        discard();
    }
}
