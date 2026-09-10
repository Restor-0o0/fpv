package org.restor.fpv.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Rocket drone: holds a long standoff and walks mini-rockets onto whatever it locked. */
public class RocketDroneEntity extends NpcDroneEntity {
    private static final double STANDOFF_DIST = 16.0;
    private static final int SALVO_COOLDOWN = 90;
    private static final int SALVO_SIZE = 2;

    public RocketDroneEntity(EntityType<? extends RocketDroneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 11;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 16.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected NpcDroneBuilds.Role buildRole() { return NpcDroneBuilds.Role.ROCKET; }

    @Override
    protected boolean attacksAircraft() { return true; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SalvoGoal());
        goalSelector.addGoal(3, new PatrolGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source,
                                       int looting, boolean hitByPlayer) {
        super.dropCustomDeathLoot(source, looting, hitByPlayer);
        if (hitByPlayer) {
            spawnAtLocation(new ItemStack(org.restor.fpv.item.ModItems.MINI_ROCKET.get(), random.nextInt(3) + 1));
        }
    }

    private class SalvoGoal extends Goal {
        private int cooldown;
        private int fired;
        private float orbitAngle;

        SalvoGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            Entity t = engagementTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            cooldown = 20;
            orbitAngle = random.nextFloat() * (float) (Math.PI * 2);
        }

        @Override
        public void tick() {
            Entity t = engagementTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);

            orbitAngle += 0.012f;
            setFlightTarget(new Vec3(
                t.getX() + Math.cos(orbitAngle) * STANDOFF_DIST,
                t.getEyeY() + 6.0,
                t.getZ() + Math.sin(orbitAngle) * STANDOFF_DIST), 1.0);

            if (--cooldown > 0 || !hasLineOfSight(t)) return;
            launch(t);
            if (++fired < SALVO_SIZE) {
                cooldown = 10;
            } else {
                fired = 0;
                cooldown = SALVO_COOLDOWN;
            }
        }

        private void launch(Entity t) {
            MiniRocketEntity rocket = new MiniRocketEntity(level(), getX(), getY() - 0.2, getZ());
            rocket.setOwner(RocketDroneEntity.this);
            Vec3 aim = t.position().add(t.getDeltaMovement().scale(8.0)).add(0, t.getBbHeight() * 0.5, 0);
            Vec3 to = aim.subtract(rocket.position());
            rocket.shoot(to.x, to.y, to.z, 1.6f, 3.0f);
            level().addFreshEntity(rocket);
            playSound(org.restor.fpv.sound.ModSounds.DART.get(), 1.0f, 0.7f);
        }
    }
}
