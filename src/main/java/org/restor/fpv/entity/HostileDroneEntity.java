package org.restor.fpv.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * Kamikaze Mini Drone (FPV_IDEAS §3): patrols around its spawn anchor; when a player comes
 * into range it beeps, rams them and detonates on contact. Shooting it down first prevents
 * the blast (it just pops and drops scrap).
 */
public class HostileDroneEntity extends NpcDroneEntity {
    private static final float EXPLOSION_POWER = 1.8f;
    private static final double RAM_TRIGGER_DIST_SQR = 2.4;

    private boolean exploded;

    public HostileDroneEntity(EntityType<? extends HostileDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new RamGoal());
        goalSelector.addGoal(3, new PatrolGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        // Contact detonation while chasing.
        if (!level().isClientSide && getTarget() != null && getTarget().isAlive()
            && distanceToSqr(getTarget()) < RAM_TRIGGER_DIST_SQR) {
            explode();
        }
    }

    @Override
    protected NpcDroneBuilds.Role buildRole() { return NpcDroneBuilds.Role.KAMIKAZE; }

    private void explode() {
        if (exploded || level().isClientSide) return;
        exploded = true;
        // MOB interaction: block damage respects the mobGriefing gamerule (creeper rules).
        level().explode(this, getX(), getY(), getZ(), EXPLOSION_POWER, Level.ExplosionInteraction.MOB);
        discard();
    }

    // Chain reaction: explosions and fire set the warhead off.
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide && (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
            || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE))) {
            explode();
            return true;
        }
        return super.hurt(source, amount);
    }

    // Chase the target's eyes at ram speed; the contact check in tick() detonates.
    private class RamGoal extends Goal {
        private boolean beeped;

        RamGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() { beeped = false; }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            if (!beeped) {
                beeped = true;
                // Aggro warning so the player gets a beat to react or shoot it down.
                playSound(org.restor.fpv.sound.ModSounds.DETECTOR_ALERT.get(), 1.2f, 1.6f);
            }
            setFlightTarget(new net.minecraft.world.phys.Vec3(t.getX(), t.getEyeY(), t.getZ()), 1.5);
        }
    }
}
