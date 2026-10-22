package org.restor.fpv.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Unarmed spotter: it never shoots, it paints. Whoever it sees glows, and every armed
 * drone within a couple of chunks gets handed the same target.
 */
public class ScoutDroneEntity extends NpcDroneEntity {
    private static final double STANDOFF_DIST = 15.0;
    private static final double FLEE_DIST_SQR = 64.0;
    private static final double RELAY_RADIUS = 36.0;
    private static final int MARK_INTERVAL = 40;

    public ScoutDroneEntity(EntityType<? extends ScoutDroneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 8.0)
            .add(Attributes.MOVEMENT_SPEED, 0.36)
            .add(Attributes.FOLLOW_RANGE, 48.0)
            .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected NpcDroneBuilds.Role buildRole() { return NpcDroneBuilds.Role.SCOUT; }

    @Override
    protected boolean attacksAircraft() { return true; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SpotGoal());
        goalSelector.addGoal(3, new PatrolGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
                                       boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        if (hitByPlayer && random.nextFloat() < 0.5f) {
            spawnAtLocation(level, new net.minecraft.world.item.ItemStack(
                org.restor.fpv.component.ModComponents.MODULE_TARGETING.get()));
        }
    }

    private class SpotGoal extends Goal {
        private int markTimer;

        SpotGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            Entity t = engagementTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void tick() {
            Entity t = engagementTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);

            Vec3 away = position().subtract(t.position());
            away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
            double keep = distanceToSqr(t) < FLEE_DIST_SQR ? STANDOFF_DIST + 6.0 : STANDOFF_DIST;
            Vec3 post = t.position().add(away.normalize().scale(keep)).add(0, 9.0, 0);
            setFlightTarget(post, distanceToSqr(t) < FLEE_DIST_SQR ? 1.4 : 0.9);

            if (--markTimer > 0 || !hasLineOfSight(t)) return;
            markTimer = MARK_INTERVAL;
            if (t instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_INTERVAL + 20, 0, false, false));
            }
            playSound(org.restor.fpv.sound.ModSounds.DETECTOR_ALERT.get(), 0.6f, 1.8f);
            relay(t);
        }

        private void relay(Entity t) {
            for (NpcDroneEntity other : level().getEntitiesOfClass(NpcDroneEntity.class,
                new AABB(blockPosition()).inflate(RELAY_RADIUS),
                d -> d != ScoutDroneEntity.this && d.isAlive() && d.engagementTarget() == null)) {
                if (t instanceof LivingEntity living) other.setTarget(living);
                else other.setAircraftTarget(t);
            }
        }
    }
}
