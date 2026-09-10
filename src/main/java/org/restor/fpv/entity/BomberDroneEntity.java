package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * Heavy bomber: a slow eight-motor lifter that climbs well above its target and works
 * through a rack of drop charges. Hard to shoot down, easy to walk away from.
 */
public class BomberDroneEntity extends NpcDroneEntity {
    private static final double BOMB_ALTITUDE = 9.0;
    private static final int BOMB_COOLDOWN = 45;
    private static final int SALVO_SIZE = 3;

    public BomberDroneEntity(EntityType<? extends BomberDroneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 26.0)
            .add(Attributes.MOVEMENT_SPEED, 0.22)
            .add(Attributes.FOLLOW_RANGE, 36.0)
            .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected NpcDroneBuilds.Role buildRole() { return NpcDroneBuilds.Role.BOMBER; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new BombingRunGoal());
        goalSelector.addGoal(3, new PatrolGoal());
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.world.damagesource.DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        if (hitByPlayer) {
            spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.TNT, random.nextInt(2)));
            spawnAtLocation(new ItemStack(org.restor.fpv.item.ModItems.DROP_CHARGE.get(), random.nextInt(3)));
        }
    }

    private class BombingRunGoal extends Goal {
        private int cooldown;
        private int salvo;

        BombingRunGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() { cooldown = 25; }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);

            Vec3 lead = t.position().add(t.getDeltaMovement().scale(10.0));
            setFlightTarget(new Vec3(lead.x, t.getEyeY() + BOMB_ALTITUDE, lead.z), 0.9);

            boolean overhead = t.distanceToSqr(getX(), t.getY(), getZ()) < 6.0;
            boolean high = getY() - t.getY() > 4.0;
            if (--cooldown > 0 || !overhead || !high) return;

            dropCharge();
            if (++salvo < SALVO_SIZE) {
                cooldown = 6;
            } else {
                salvo = 0;
                cooldown = BOMB_COOLDOWN;
            }
        }

        private void dropCharge() {
            DropChargeEntity charge = new DropChargeEntity(level(), getX(), getY() - 0.5, getZ());
            charge.setOwner(BomberDroneEntity.this);
            charge.setDeltaMovement(getDeltaMovement().x * 0.5, -0.2, getDeltaMovement().z * 0.5);
            level().addFreshEntity(charge);
            playSound(org.restor.fpv.sound.ModSounds.WARHEAD_ARM.get(), 0.8f, 0.9f);
        }
    }
}
