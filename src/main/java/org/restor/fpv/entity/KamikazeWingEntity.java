package org.restor.fpv.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.restor.fpv.warhead.WarheadType;

// A fast (~50 m/s) FPV wing that detonates like TNT on the first block/entity contact once
// airborne — a fixed-wing kamikaze. The pilot is returned to the launch point on impact.
public class KamikazeWingEntity extends PlaneEntity {
    private final org.restor.fpv.warhead.ContactFuze fuze = new org.restor.fpv.warhead.ContactFuze();
    private boolean exploded;
    private int explosionViewTicks;
    private net.minecraft.world.phys.Vec3 explosionLookTarget;
    private net.minecraft.world.phys.Vec3 attackTarget;
    private net.minecraft.world.phys.Vec3 attackDirection;
    private int attackTicks;
    private final java.util.Set<Long> attackForcedChunks = new java.util.HashSet<>();
    private WarheadType warhead = defaultWarhead();

    public KamikazeWingEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.maxSpeedBt = 2.5f;          // ~50 m/s cap
        this.maxThrust = 260f;           // reaches the cap quickly
        this.stallSpeed = 4.0f;
        this.liftCoeff = 0.10f;          // holds altitude comfortably at 50 m/s
        this.maxLiftAccel = 9.81f * 1.6f;
    }

    @Override
    public void tick() {
        if (explosionViewTicks > 0) {
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            super.tick();
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            if (explosionLookTarget != null) KamikazeExplosionEffects.lookAt(this, explosionLookTarget);
            if (--explosionViewTicks <= 0) {
                ejectPassengers();
                discard();
            }
            return;
        }
        if (!level().isClientSide && attackTarget != null) {
            xo = getX(); yo = getY(); zo = getZ();
            xRotO = getXRot(); yRotO = getYRot(); rollO = getRoll();
            guideAttackRun();
            return;
        }
        super.tick();
        if (level().isClientSide) return;

        if (fuze.tick(this, isArmed())) {
            explodeNow();
            return;
        }
        if (fuze.isArmed()) {
            // Ramming a living target also detonates.
            double radius = warhead.proximity() ? org.restor.fpv.warhead.ProximityFuze.TRIGGER_RADIUS : 0.2;
            var hits = level().getEntities(this, getBoundingBox().inflate(radius),
                e -> e instanceof net.minecraft.world.entity.LivingEntity && !hasPassenger(e));
            if (!hits.isEmpty()) explodeNow();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide && (source.is(DamageTypeTags.IS_EXPLOSION)
            || source.is(DamageTypeTags.IS_FIRE))) {
            explodeNow();
            return true;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected net.minecraft.world.item.Item getDropItem() {
        return org.restor.fpv.item.ModItems.PLANE_KAMIKAZE.get();
    }

    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (org.restor.fpv.warhead.WarheadLoading.isWarhead(held)) {
            if (!level().isClientSide) {
                org.restor.fpv.warhead.WarheadLoading.swap(player, held, warhead,
                    warheadCapacityKg(), this::setWarhead);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    protected WarheadType defaultWarhead() { return WarheadType.HE; }

    /** How much warhead this airframe can lift, in kg. */
    public float warheadCapacityKg() { return 4.0f; }

    public WarheadType getWarhead() { return warhead; }

    public void setWarhead(WarheadType type) { this.warhead = type == null ? defaultWarhead() : type; }

    @Override
    protected float payloadMass() { return warhead.mass(); }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Warhead")) warhead = WarheadType.byId(tag.getString("Warhead"));
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Warhead", warhead.id());
    }

    public void beginAttackRun(net.minecraft.world.phys.Vec3 target, net.minecraft.world.phys.Vec3 spawn) {
        attackTarget = target;
        attackTicks = 0;
        setPos(spawn.x, spawn.y, spawn.z);
        attackDirection = target.subtract(spawn).normalize();
        setDeltaMovement(attackDirection.scale(4.0));
        aimAlong(attackDirection);
        updateAttackForcedChunks(spawn, target);
    }

    public boolean isAttackRun() { return attackTarget != null; }

    private void guideAttackRun() {
        attackTicks++;
        updateAttackForcedChunks(position(), attackTarget);
        net.minecraft.world.phys.Vec3 toTarget = attackTarget.subtract(position());
        if (toTarget.lengthSqr() < 2.25) {
            KamikazeExplosionEffects.burst(level(), getX(), getY(), getZ(), 5.0);
            explodeNow();
            return;
        }
        // Keep the missile on a clean, non-jittery straight approach. Recomputing a strong
        // guidance turn every tick made the rendered wing wobble; the scout strike spawns on
        // the correct attack line, so a fixed ray to the selected block is the stable path.
        if (attackDirection == null || attackDirection.lengthSqr() < 1.0e-4) {
            attackDirection = toTarget.normalize();
        }
        net.minecraft.world.phys.Vec3 newVel = attackDirection.normalize().scale(4.0);
        net.minecraft.world.phys.Vec3 start = position();
        net.minecraft.world.phys.Vec3 end = start.add(newVel);
        var hit = level().clip(new net.minecraft.world.level.ClipContext(start, end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            KamikazeExplosionEffects.burst(level(), getX(), getY(), getZ(), 5.0);
            explodeNow();
            return;
        }
        setDeltaMovement(newVel);
        aimAlong(newVel);
        move(net.minecraft.world.entity.MoverType.SELF, newVel);
        if (attackTicks > 3 && (horizontalCollision || verticalCollision || onGround())) {
            KamikazeExplosionEffects.burst(level(), getX(), getY(), getZ(), 5.0);
            explodeNow();
        }
    }

    private void aimAlong(net.minecraft.world.phys.Vec3 d) {
        double len = d.length();
        if (len < 1.0e-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
        syncVisualAttitude(yaw, pitch, 0f);
        yRotO = yaw;
        xRotO = pitch;
    }

    private void updateAttackForcedChunks(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        java.util.Set<Long> want = new java.util.HashSet<>();
        addChunkBubble(want, a, 2);
        addChunkBubble(want, b, 2);
        net.minecraft.world.phys.Vec3 mid = a.add(b).scale(0.5);
        addChunkBubble(want, mid, 1);

        if (want.equals(attackForcedChunks)) return;
        for (long c : attackForcedChunks) {
            if (!want.contains(c)) {
                sl.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            }
        }
        for (long c : want) {
            if (!attackForcedChunks.contains(c)) {
                sl.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), true);
            }
        }
        attackForcedChunks.clear();
        attackForcedChunks.addAll(want);
    }

    private static void addChunkBubble(java.util.Set<Long> out, net.minecraft.world.phys.Vec3 pos, int radius) {
        int cx = ((int) Math.floor(pos.x)) >> 4;
        int cz = ((int) Math.floor(pos.z)) >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
    }

    private void clearAttackForcedChunks() {
        if (attackForcedChunks.isEmpty()) return;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (long c : attackForcedChunks) {
                sl.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
            }
        }
        attackForcedChunks.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) clearAttackForcedChunks();
        super.remove(reason);
    }

    private void explodeNow() {
        if (exploded) return;
        exploded = true;
        boolean cinematic = attackTarget == null && org.restor.fpv.Config.DEV_EXPLOSION_CAMERA_BACKSTEP.get() > 0.0;
        net.minecraft.world.phys.Vec3 impact = cinematic ? KamikazeExplosionEffects.moveCameraBack(this) : position();
        if (!cinematic) ejectPassengers();
        WarheadDetonation.detonate(level(), this, impact, warhead);
        if (cinematic) {
            explosionLookTarget = impact;
            explosionViewTicks = Math.max(0, (int) Math.round(org.restor.fpv.Config.DEV_EXPLOSION_CAMERA_SECONDS.get() * 20.0));
            if (explosionViewTicks <= 0) {
                ejectPassengers();
                discard();
            }
        } else {
            discard();
        }
    }
}
