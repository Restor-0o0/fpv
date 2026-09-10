package org.restor.fpv.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import org.restor.fpv.antidrone.AntiDronePolicy;
import org.restor.fpv.antidrone.RadioInterferenceReceiver;

import java.util.EnumSet;

/**
 * Flying jammer: a walking exclusion zone. It carries no weapon, it just parks near the
 * player and drowns every piloted craft around it in noise. Its own kind is filtered out
 * of the sweep, so a swarm can operate under its own umbrella.
 */
public class JammerDroneEntity extends NpcDroneEntity {
    private static final double ORBIT_DIST = 11.0;
    private static final double JAM_RADIUS = 22.0;
    private static final float JAM_STRENGTH = 0.85f;
    private static final int JAM_INTERVAL = 10;

    public JammerDroneEntity(EntityType<? extends JammerDroneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 9;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 18.0)
            .add(Attributes.MOVEMENT_SPEED, 0.26)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected NpcDroneBuilds.Role buildRole() { return NpcDroneBuilds.Role.JAMMER; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new ShadowGoal());
        goalSelector.addGoal(3, new PatrolGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    getX(), getY() + 0.25, getZ(), 0, 0.02, 0);
            }
            return;
        }
        if (isRadioJammed() || tickCount % JAM_INTERVAL != 0) return;
        sweep();
    }

    private void sweep() {
        Vec3 source = position();
        for (Entity craft : level().getEntitiesOfClass(Entity.class,
            new AABB(blockPosition()).inflate(JAM_RADIUS),
            e -> e.isAlive() && e instanceof RadioInterferenceReceiver && !(e instanceof NpcDroneEntity))) {
            float strength = AntiDronePolicy.falloff(craft.position().distanceTo(source), JAM_RADIUS, JAM_STRENGTH);
            if (strength > 0.0f) {
                ((RadioInterferenceReceiver) craft).applyRadioInterference(getId(), strength, JAM_INTERVAL + 4);
            }
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 6, 0.6, 0.3, 0.6, 0.02);
        }
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.world.damagesource.DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        if (hitByPlayer) {
            spawnAtLocation(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.AMETHYST_SHARD, random.nextInt(2) + 1));
        }
    }

    private class ShadowGoal extends Goal {
        private float orbitAngle;

        ShadowGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() { orbitAngle = random.nextFloat() * (float) (Math.PI * 2); }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);
            orbitAngle += 0.015f;
            setFlightTarget(new Vec3(
                t.getX() + Math.cos(orbitAngle) * ORBIT_DIST,
                t.getEyeY() + 5.0,
                t.getZ() + Math.sin(orbitAngle) * ORBIT_DIST), 0.9);
        }
    }
}
