package org.restor.fpv.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Base for all AI drones (FPV_IDEAS §3 "NpcDroneEntity"): a Mob flying via a stable
 * hover / fly-to-target move control (Vex-style direct flight, no gravity) instead of
 * DroneEntity's ACRO physics — predictable behaviour, cheap to simulate.
 *
 * Provides: hover move control, patrol-around-anchor wandering, hover bob, no fall
 * damage, scrap-electronics death loot. Subclasses add their combat behaviour.
 */
public abstract class NpcDroneEntity extends Monster {
    protected static final double PATROL_RADIUS = 8.0;

    protected BlockPos anchor; // patrol center — set at spawn, persisted

    /** Pin the patrol center (e.g. a drone nest) instead of the first-tick position. */
    public void setAnchor(BlockPos pos) { this.anchor = pos; }

    protected NpcDroneEntity(EntityType<? extends NpcDroneEntity> type, Level level) {
        super(type, level);
        this.moveControl = new HoverMoveControl(this);
        this.xpReward = 5;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 10.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.FOLLOW_RANGE, 28.0)
            .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (anchor == null) anchor = blockPosition();
        // Hover bob so it never reads as frozen mid-air.
        if (!level().isClientSide && getDeltaMovement().lengthSqr() < 0.003) {
            setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount * 0.11) * 0.002, 0));
        }
    }

    // Shot down: drop scrap electronics (copper/redstone per the §6 material logic).
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer) {
        super.dropCustomDeathLoot(source, looting, hitByPlayer);
        if (hitByPlayer) {
            spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE,
                random.nextInt(2) + 1));
            if (random.nextFloat() < 0.3f) {
                spawnAtLocation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT));
            }
        }
    }

    // A quad doesn't take fall damage while its props run — and a dead one is already loot.
    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) { return false; }

    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ITEM_BREAK; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ITEM_BREAK; }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Anchor")) anchor = BlockPos.of(tag.getLong("Anchor"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchor != null) tag.putLong("Anchor", anchor.asLong());
    }

    // ------------------------------------------------------------------
    //  Movement: direct fly-to-point (Vex-style), no pathfinding needed.
    // ------------------------------------------------------------------

    protected static class HoverMoveControl extends MoveControl {
        private final NpcDroneEntity drone;

        HoverMoveControl(NpcDroneEntity drone) {
            super(drone);
            this.drone = drone;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) return;
            Vec3 to = new Vec3(wantedX - drone.getX(), wantedY - drone.getY(), wantedZ - drone.getZ());
            double dist = to.length();
            if (dist < drone.getBoundingBox().getSize()) {
                operation = Operation.WAIT;
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.6));
                return;
            }
            // Accelerate toward the point; speedModifier ~0.7 patrol, ~1.5 ram.
            drone.setDeltaMovement(drone.getDeltaMovement()
                .add(to.scale(speedModifier * 0.06 / dist)));
            // Face travel direction (yaw only — the renderer keeps it level like a real quad).
            Vec3 vel = drone.getDeltaMovement();
            if (vel.horizontalDistanceSqr() > 1.0e-6) {
                float yaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
                drone.setYRot(Mth.approachDegrees(drone.getYRot(), yaw, 20f));
                drone.yBodyRot = drone.getYRot();
            }
        }
    }

    // Wander around the anchor: pick a nearby point in the air, fly there, idle, repeat.
    protected class PatrolGoal extends Goal {
        public PatrolGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            return getTarget() == null
                && (!moveControl.hasWanted() || random.nextInt(reducedTickDelay(40)) == 0);
        }

        @Override
        public boolean canContinueToUse() { return false; } // re-pick each time

        @Override
        public void start() {
            BlockPos c = anchor != null ? anchor : blockPosition();
            double x = c.getX() + 0.5 + (random.nextDouble() * 2 - 1) * PATROL_RADIUS;
            double y = c.getY() + 1.5 + (random.nextDouble() * 2 - 1) * 2.5;
            double z = c.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * PATROL_RADIUS;
            moveControl.setWantedPosition(x, y, z, 0.7);
        }
    }
}
