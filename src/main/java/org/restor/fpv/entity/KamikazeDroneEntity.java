package org.restor.fpv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.data.DroneData;
import org.restor.fpv.rules.ServerRule;

// Station-controlled drone that detonates like TNT on any block contact once airborne.
public class KamikazeDroneEntity extends RemoteDroneEntity {
    private final org.restor.fpv.warhead.ContactFuze fuze = new org.restor.fpv.warhead.ContactFuze();
    private boolean exploded;
    private int explosionViewTicks;
    private net.minecraft.world.phys.Vec3 explosionLookTarget;
    private net.minecraft.world.phys.Vec3 attackTarget;
    private net.minecraft.world.phys.Vec3 attackDirection;
    private int attackTicks;
    private final ChunkBubble attackChunks = new ChunkBubble();
    private org.restor.fpv.warhead.WarheadType warhead = org.restor.fpv.warhead.WarheadType.FRAG;

    public KamikazeDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level, DroneData.RACING);
    }

    @Override
    public void tick() {
        if (explosionViewTicks > 0) {
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            // Aim before ticking: the tick is what publishes the angles to the client.
            if (explosionLookTarget != null) KamikazeExplosionEffects.lookAt(this, explosionLookTarget);
            super.tick();
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (--explosionViewTicks <= 0) {
                endRemoteControl();
                discard();
            }
            return;
        }
        if (!level().isClientSide && attackTarget != null) {
            xo = getX(); yo = getY(); zo = getZ();
            xRotO = getXRot(); yRotO = getYRot();
            guideAttackRun();
            return;
        }
        super.tick();
        if (level().isClientSide) return;

        if (fuze.tick(this, flightController.isArmed())) explodeNow();
    }

    @Override
    protected void onMovementImpact(net.minecraft.world.phys.Vec3 velocityDelta) {
        if (!level().isClientSide) fuze.reportImpact(velocityDelta.length());
    }

    @Override
    protected void onEntityCollision() {
        // Ramming a living target detonates the warhead once the fuze has armed.
        if (flightController.isArmed() && fuze.isArmed()) {
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

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Warhead")) warhead = org.restor.fpv.warhead.WarheadType.byId(tag.getString("Warhead"));
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Warhead", warhead.id());
    }

    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && org.restor.fpv.warhead.WarheadLoading.isWarhead(held)) {
            if (!canPlayerAccess(player)) return net.minecraft.world.InteractionResult.FAIL;
            if (!level().isClientSide) {
                org.restor.fpv.warhead.WarheadLoading.swap(player, held, warhead,
                    warheadCapacityKg(), this::setWarhead);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    public org.restor.fpv.warhead.WarheadType getWarhead() { return warhead; }

    public void setWarhead(org.restor.fpv.warhead.WarheadType type) {
        if (type != null) this.warhead = type;
    }

    /** Autonomous one-way run: released from a carrier, flies a straight line and detonates. */
    public void beginAttackRun(net.minecraft.world.phys.Vec3 target) {
        attackTarget = target;
        attackTicks = 0;
        attackDirection = target.subtract(position()).normalize();
        setDeltaMovement(attackDirection.scale(1.6));
        setPoseLocked(true);
        aimAlong(attackDirection);
    }

    public boolean isAttackRun() { return attackTarget != null; }

    private void guideAttackRun() {
        attackTicks++;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            attackChunks.update(sl, position(), attackTarget);
        }
        net.minecraft.world.phys.Vec3 toTarget = attackTarget.subtract(position());
        if (toTarget.lengthSqr() < 1.5) {
            explodeNow();
            return;
        }
        // Gentle re-aim keeps the run stable while still tracking a target the carrier
        // picked from far away.
        attackDirection = attackDirection.scale(0.82).add(toTarget.normalize().scale(0.18)).normalize();
        net.minecraft.world.phys.Vec3 vel = attackDirection.scale(1.6);
        var hit = level().clip(new net.minecraft.world.level.ClipContext(position(), position().add(vel),
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            explodeNow();
            return;
        }
        setDeltaMovement(vel);
        aimAlong(vel);
        move(net.minecraft.world.entity.MoverType.SELF, vel);
        if (attackTicks > 3 && (horizontalCollision || verticalCollision || onGround())) explodeNow();
        if (attackTicks > 2400) discard();
    }

    private void aimAlong(net.minecraft.world.phys.Vec3 d) {
        double len = d.length();
        if (len < 1.0e-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) attackChunks.release(sl);
        super.remove(reason);
    }

    private void explodeNow() {
        if (exploded) return;
        exploded = true;
        boolean cinematic = attackTarget == null && org.restor.fpv.Config.DEV_EXPLOSION_CAMERA_BACKSTEP.get() > 0.0;
        // Stop accepting client poses first, otherwise the pilot's next pose packet
        // overwrites the camera backstep before it is ever rendered.
        if (cinematic) setPoseLocked(true);
        net.minecraft.world.phys.Vec3 impact = cinematic ? KamikazeExplosionEffects.moveCameraBack(this) : position();
        if (!cinematic) endRemoteControl();
        WarheadDetonation.detonate(level(), this, impact, warhead);
        if (cinematic) {
            explosionLookTarget = impact;
            explosionViewTicks = Math.max(0, (int) Math.round(ServerRule.BLAST_CAMERA.value() * 20.0));
            if (explosionViewTicks <= 0) {
                endRemoteControl();
                discard();
            }
        } else {
            discard();
        }
    }
}
