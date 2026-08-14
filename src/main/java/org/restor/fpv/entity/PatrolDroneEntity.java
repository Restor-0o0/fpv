package org.restor.fpv.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Patrol Drone (FPV_IDEAS §3): patrols an area and shoots. Keeps a standoff distance from
 * its target and fires darts (arrows) in bursts — tougher than the kamikaze mini but no
 * self-destruct, so it can be fought conventionally.
 */
public class PatrolDroneEntity extends NpcDroneEntity {
    private static final double STANDOFF_DIST = 9.0;   // preferred firing range
    private static final int FIRE_COOLDOWN = 25;       // ticks between shots

    public PatrolDroneEntity(EntityType<? extends PatrolDroneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 14.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new StrafeShootGoal());
        goalSelector.addGoal(3, new PatrolGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // Hold a shooting orbit around the target and fire darts on a cooldown.
    private class StrafeShootGoal extends Goal {
        private int cooldown;
        private float orbitAngle;

        StrafeShootGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            cooldown = 10; // brief windup before the first shot
            orbitAngle = random.nextFloat() * (float) (Math.PI * 2);
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;

            // Slowly circle the target at standoff range, slightly above eye level.
            orbitAngle += 0.02f;
            double wx = t.getX() + Math.cos(orbitAngle) * STANDOFF_DIST;
            double wy = t.getEyeY() + 2.5;
            double wz = t.getZ() + Math.sin(orbitAngle) * STANDOFF_DIST;
            moveControl.setWantedPosition(wx, wy, wz, 1.0);
            getLookControl().setLookAt(t, 30f, 30f);

            if (--cooldown <= 0 && hasLineOfSight(t) && distanceToSqr(t) < 20 * 20) {
                cooldown = FIRE_COOLDOWN;
                shootDart(t);
            }
        }

        private void shootDart(LivingEntity t) {
            Arrow dart = new Arrow(level(), PatrolDroneEntity.this, ItemStack.EMPTY, null);
            dart.setBaseDamage(1.5);
            dart.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            Vec3 to = new Vec3(t.getX() - getX(), t.getEyeY() - 0.4 - dart.getY(), t.getZ() - getZ());
            dart.shoot(to.x, to.y, to.z, 2.2f, 4.0f);
            level().addFreshEntity(dart);
            playSound(SoundEvents.CROSSBOW_SHOOT, 0.8f, 1.5f);
        }
    }
}
