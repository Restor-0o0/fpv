package org.restor.fpv.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Base for all AI drones (FPV_IDEAS §3 "NpcDroneEntity"): a Mob flying via a stable
 * hover / fly-to-target move control (Vex-style direct flight, no gravity) instead of
 * DroneEntity's ACRO physics — predictable behaviour, cheap to simulate.
 *
 * Provides: hover move control, patrol-around-anchor wandering, hover bob, no fall
 * damage, scrap-electronics death loot. Subclasses add their combat behaviour.
 */
public abstract class NpcDroneEntity extends Monster
    implements org.restor.fpv.antidrone.RadioInterferenceReceiver {
    protected static final double PATROL_RADIUS = 8.0;
    private static final double MAX_ANCHOR_DISTANCE = 40.0;
    private static final int PATH_CHECK_INTERVAL = 6;
    private static final int STUCK_CHECK_INTERVAL = 10;
    private static final int STUCK_TIMEOUT = 60;
    private static final double AIRCRAFT_SCAN_RADIUS = 32.0;
    private static final int AIRCRAFT_SCAN_INTERVAL = 10;

    private static final net.minecraft.network.syncher.EntityDataAccessor<CompoundTag> DATA_ASSEMBLY =
        net.minecraft.network.syncher.SynchedEntityData.defineId(NpcDroneEntity.class,
            net.minecraft.network.syncher.EntityDataSerializers.COMPOUND_TAG);

    private org.restor.fpv.assembly.DroneAssembly assembly;
    protected BlockPos anchor;
    private BlockPos ownerNestPos;
    private UUID ownerNestId;
    private Vec3 requestedTarget;
    private Vec3 resolvedTarget;
    private Vec3 plannedTarget;
    private Vec3 lastProgressPosition;
    private double requestedSpeed;
    private int nextPathCheck;
    private int stuckTicks;
    private int recoveryUntil;
    private Entity aircraftTarget;
    private int nextAircraftScan;

    private static final float JAM_LINK_LOSS = 0.25f;
    private final org.restor.fpv.antidrone.RadioJamState jamState = new org.restor.fpv.antidrone.RadioJamState();

    // Machines don't care about the difficulty setting, and a nest's swarm must not
    // evaporate the moment the player backs off 32 blocks.
    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ASSEMBLY, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ASSEMBLY.equals(key)) {
            assembly = null;
            refreshDimensions();
        }
    }

    public org.restor.fpv.assembly.DroneAssembly getAssembly() {
        if (assembly == null) assembly = org.restor.fpv.assembly.DroneAssembly.load(entityData.get(DATA_ASSEMBLY));
        return assembly;
    }

    public void setAssembly(org.restor.fpv.assembly.DroneAssembly a) {
        assembly = a;
        entityData.set(DATA_ASSEMBLY, a.save());
        refreshDimensions();
    }

    @Override
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        org.restor.fpv.assembly.FrameSpec spec = getAssembly().frameSpec();
        if (spec == null) return super.getDefaultDimensions(pose);
        org.restor.fpv.assembly.DroneVisual v = org.restor.fpv.assembly.DroneVisual.of(spec);
        return net.minecraft.world.entity.EntityDimensions.scalable(v.hitboxWidth(), v.hitboxHeight());
    }

    public boolean hasModule(org.restor.fpv.component.ModuleType type) {
        return getAssembly().moduleType() == type;
    }

    protected abstract NpcDroneBuilds.Role buildRole();

    /** A player aircraft is a plain Entity, so the vanilla target selector never sees it. */
    public static boolean isPlayerAircraft(Entity e) {
        return e instanceof DroneEntity || e instanceof PlaneEntity;
    }

    protected boolean attacksAircraft() { return false; }

    public void setAircraftTarget(Entity e) { aircraftTarget = e; }

    public Entity getAircraftTarget() {
        if (aircraftTarget != null && !aircraftTarget.isAlive()) aircraftTarget = null;
        return aircraftTarget;
    }

    protected Entity engagementTarget() {
        LivingEntity living = getTarget();
        if (living != null && living.isAlive()) return living;
        return getAircraftTarget();
    }

    private Entity nearestAircraft() {
        AABB box = new AABB(blockPosition()).inflate(AIRCRAFT_SCAN_RADIUS);
        Entity best = nearestOf(level().getEntitiesOfClass(DroneEntity.class, box, Entity::isAlive), null);
        return nearestOf(level().getEntitiesOfClass(PlaneEntity.class, box, Entity::isAlive), best);
    }

    private Entity nearestOf(List<? extends Entity> candidates, Entity best) {
        double bestSqr = best == null ? Double.MAX_VALUE : distanceToSqr(best);
        for (Entity e : candidates) {
            double sqr = distanceToSqr(e);
            if (sqr < bestSqr) {
                bestSqr = sqr;
                best = e;
            }
        }
        return best;
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData data) {
        if (entityData.get(DATA_ASSEMBLY).isEmpty()) setAssembly(NpcDroneBuilds.roll(random, buildRole()));
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    /** Pin the patrol center (e.g. a drone nest) instead of the first-tick position. */
    public void setAnchor(BlockPos pos) { this.anchor = pos; }

    public void setOwnerNest(BlockPos pos, UUID id) {
        ownerNestPos = pos.immutable();
        ownerNestId = id;
    }

    public boolean isOwnedByNest(BlockPos pos, UUID id) {
        return ownerNestId != null && ownerNestId.equals(id) && pos.equals(ownerNestPos);
    }

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
    public void applyRadioInterference(long sourceId, float strength, int durationTicks) {
        if (level().isClientSide) return;
        jamState.apply(sourceId, strength, durationTicks, level().getGameTime());
    }

    public boolean isRadioJammed() {
        return jamState.strength() > JAM_LINK_LOSS;
    }

    // Jammed: the flight controller loses its link, drops whatever it was chasing and sinks.
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (jamState.refresh(level().getGameTime()) <= JAM_LINK_LOSS) return;
        setTarget(null);
        aircraftTarget = null;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.82, 1.0, 0.82).add(0.0, -0.035, 0.0));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (isRadioJammed()) return;
        if (anchor == null) anchor = blockPosition();
        if (entityData.get(DATA_ASSEMBLY).isEmpty()) setAssembly(NpcDroneBuilds.roll(random, buildRole()));
        if (attacksAircraft() && tickCount >= nextAircraftScan) {
            nextAircraftScan = tickCount + AIRCRAFT_SCAN_INTERVAL;
            aircraftTarget = nearestAircraft();
        }
        tickNavigationSafety();
        // Hover bob so it never reads as frozen mid-air.
        if (getDeltaMovement().lengthSqr() < 0.003) {
            setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount * 0.11) * 0.002, 0));
        }
    }

    // Shot down: drop scrap electronics (copper/redstone per the §6 material logic).
    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
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
    @Override protected SoundEvent getHurtSound(DamageSource source) { return org.restor.fpv.sound.ModSounds.CRASH.get(); }
    @Override protected SoundEvent getDeathSound() { return org.restor.fpv.sound.ModSounds.CRASH.get(); }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Assembly")) setAssembly(org.restor.fpv.assembly.DroneAssembly.load(tag.getCompound("Assembly")));
        if (tag.contains("Anchor")) anchor = BlockPos.of(tag.getLong("Anchor"));
        if (tag.contains("OwnerNestPos")) ownerNestPos = BlockPos.of(tag.getLong("OwnerNestPos"));
        if (tag.hasUUID("OwnerNestId")) ownerNestId = tag.getUUID("OwnerNestId");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Assembly", entityData.get(DATA_ASSEMBLY));
        if (anchor != null) tag.putLong("Anchor", anchor.asLong());
        if (ownerNestPos != null) tag.putLong("OwnerNestPos", ownerNestPos.asLong());
        if (ownerNestId != null) tag.putUUID("OwnerNestId", ownerNestId);
    }

    protected void setFlightTarget(Vec3 target, double speed) {
        if (level().isClientSide || anchor == null) return;
        requestedTarget = clampToAnchor(target);
        requestedSpeed = speed;
        if (tickCount < recoveryUntil) return;
        if (tickCount >= nextPathCheck || resolvedTarget == null || plannedTarget == null) {
            resolvedTarget = findReachableTarget(requestedTarget);
            plannedTarget = requestedTarget;
            nextPathCheck = tickCount + PATH_CHECK_INTERVAL;
        }
        if (resolvedTarget != null) {
            moveControl.setWantedPosition(resolvedTarget.x, resolvedTarget.y, resolvedTarget.z, speed);
        }
    }

    protected Vec3 findFreePatrolPoint() {
        BlockPos center = anchor != null ? anchor : blockPosition();
        for (int i = 0; i < 12; i++) {
            Vec3 candidate = new Vec3(
                center.getX() + 0.5 + (random.nextDouble() * 2 - 1) * PATROL_RADIUS,
                center.getY() + 1.5 + (random.nextDouble() * 2 - 1) * 2.5,
                center.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * PATROL_RADIUS);
            if (isPathClear(position(), candidate)) return candidate;
        }
        return null;
    }

    private Vec3 clampToAnchor(Vec3 target) {
        Vec3 center = Vec3.atCenterOf(anchor);
        Vec3 offset = target.subtract(center);
        if (offset.lengthSqr() <= MAX_ANCHOR_DISTANCE * MAX_ANCHOR_DISTANCE) return target;
        return center.add(offset.normalize().scale(MAX_ANCHOR_DISTANCE));
    }

    private Vec3 findReachableTarget(Vec3 target) {
        Vec3 from = position();
        if (isPathClear(from, target)) return target;

        Vec3 direction = target.subtract(from);
        if (direction.lengthSqr() < 1.0e-6) return null;
        Vec3 forward = direction.normalize().scale(Math.min(4.0, direction.length()));
        Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize().scale(2.5);
        Vec3[] offsets = {
            forward.add(0, 2.5, 0), forward.add(side), forward.subtract(side),
            forward.add(side).add(0, 2.0, 0), forward.subtract(side).add(0, 2.0, 0),
            new Vec3(0, 3.0, 0)
        };
        for (Vec3 offset : offsets) {
            Vec3 candidate = clampToAnchor(from.add(offset));
            if (isPathClear(from, candidate)) return candidate;
        }
        return null;
    }

    private boolean isPathClear(Vec3 from, Vec3 to) {
        if (level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) return false;

        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, Mth.ceil(delta.length() / 0.5));
        AABB box = getBoundingBox();
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.add(delta.scale((double) i / steps));
            AABB moved = box.move(point.subtract(position()));
            if (!level().noCollision(this, moved)) return false;
        }
        return true;
    }

    private void tickNavigationSafety() {
        if (requestedTarget == null || tickCount % STUCK_CHECK_INTERVAL != 0) return;
        if (lastProgressPosition == null || position().distanceToSqr(lastProgressPosition) >= 0.09
            || position().distanceToSqr(requestedTarget) < 1.0) {
            stuckTicks = 0;
        } else {
            stuckTicks += STUCK_CHECK_INTERVAL;
        }
        lastProgressPosition = position();
        if (stuckTicks < STUCK_TIMEOUT) return;

        stuckTicks = 0;
        resolvedTarget = findRecoveryPoint();
        plannedTarget = resolvedTarget;
        nextPathCheck = tickCount + PATH_CHECK_INTERVAL;
        if (resolvedTarget != null) {
            recoveryUntil = tickCount + 20;
            moveControl.setWantedPosition(resolvedTarget.x, resolvedTarget.y, resolvedTarget.z,
                Math.max(0.8, requestedSpeed));
        }
    }

    private Vec3 findRecoveryPoint() {
        for (int i = 0; i < 10; i++) {
            Vec3 candidate = clampToAnchor(position().add(
                (random.nextDouble() * 2 - 1) * 3.0,
                1.0 + random.nextDouble() * 3.0,
                (random.nextDouble() * 2 - 1) * 3.0));
            if (isPathClear(position(), candidate)) return candidate;
        }
        return null;
    }

    private void invalidateFlightPath() {
        resolvedTarget = null;
        nextPathCheck = tickCount + 1;
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
            Vec3 nextVelocity = drone.getDeltaMovement().add(to.scale(speedModifier * 0.06 / dist));
            double maximumSpeed = 0.25 + speedModifier * 0.25;
            if (nextVelocity.lengthSqr() > maximumSpeed * maximumSpeed) {
                nextVelocity = nextVelocity.normalize().scale(maximumSpeed);
            }
            if (drone.anchor != null) {
                Vec3 radial = drone.position().subtract(Vec3.atCenterOf(drone.anchor));
                if (radial.lengthSqr() >= MAX_ANCHOR_DISTANCE * MAX_ANCHOR_DISTANCE) {
                    Vec3 outward = radial.normalize();
                    double outwardSpeed = nextVelocity.dot(outward);
                    if (outwardSpeed > 0) nextVelocity = nextVelocity.subtract(outward.scale(outwardSpeed));
                }
            }
            if (!drone.level().isClientSide
                && !drone.level().noCollision(drone, drone.getBoundingBox().move(nextVelocity))) {
                operation = Operation.WAIT;
                drone.setDeltaMovement(drone.getDeltaMovement().scale(0.2));
                drone.invalidateFlightPath();
                return;
            }
            drone.setDeltaMovement(nextVelocity);
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
            Vec3 target = findFreePatrolPoint();
            if (target != null) setFlightTarget(target, 0.7);
        }
    }
}
