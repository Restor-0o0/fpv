package org.restor.fpv.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.warhead.WarheadType;

public class DropChargeEntity extends net.minecraft.world.entity.projectile.ThrowableItemProjectile {

    private static final int MAX_LIFE = 400;
    // Ticks of free fall before the fuze is live. A charge that hits something inside that
    // window is a dud and lands as an item, so a low release can no longer kill the drone.
    private static final int ARM_DELAY = 8;
    private static final double AIR_DRAG = 0.996;

    private boolean whistled;

    public DropChargeEntity(EntityType<? extends DropChargeEntity> type, Level level) {
        super(type, level);
    }

    public DropChargeEntity(Level level, double x, double y, double z) {
        super(ModEntities.DROP_CHARGE.get(), level);
        setPos(x, y, z);
    }

    @Override
    protected Item getDefaultItem() {
        return org.restor.fpv.item.ModItems.DROP_CHARGE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof Pilotable);
    }

    public boolean isArmed() {
        return tickCount >= ARM_DELAY;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(getDeltaMovement().multiply(AIR_DRAG, 1.0, AIR_DRAG));
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.01, 0);
            return;
        }
        // The whistle starts once the fall is steep enough to be heard from the ground.
        if (!whistled && getDeltaMovement().y < -0.55) {
            whistled = true;
            level().playSound(null, this, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.4f, 0.5f);
        }
        if (isArmed() && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (tickCount > MAX_LIFE) detonate();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        if (!isArmed()) {
            dud();
            return;
        }
        detonate();
    }

    // Too low a release: the charge survives intact and can be picked back up.
    private void dud() {
        if (!isAlive()) return;
        playSound(SoundEvents.ITEM_FRAME_BREAK, 0.6f, 1.2f);
        spawnAtLocation(new ItemStack(org.restor.fpv.item.ModItems.DROP_CHARGE.get()));
        discard();
    }

    private void detonate() {
        if (!isAlive()) return;
        Vec3 at = groundedPoint();
        discard();
        WarheadDetonation.detonate(level(), getOwner() != null ? getOwner() : this, at, WarheadType.DROP);
    }

    // Detonating a hair above the surface it struck, rather than inside it, keeps the blast
    // from being swallowed by the block it happened to clip.
    private Vec3 groundedPoint() {
        Vec3 at = position();
        HitResult ground = level().clip(new ClipContext(at, at.add(0, -1.2, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (ground.getType() != HitResult.Type.MISS) return ground.getLocation().add(0, 0.1, 0);
        return at;
    }
}
