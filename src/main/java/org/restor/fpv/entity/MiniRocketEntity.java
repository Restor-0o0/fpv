package org.restor.fpv.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class MiniRocketEntity extends net.minecraft.world.entity.projectile.ThrowableItemProjectile {

    private static final int MAX_LIFE = 120;
    private static final float BLAST_RADIUS = 1.4f;
    private static final float IMPACT_DAMAGE = 5.0f;
    // Blocks tougher than this shrug the warhead off — obsidian, ancient debris, bedrock.
    private static final float MAX_BREAK_HARDNESS = 6.0f;

    public MiniRocketEntity(EntityType<? extends MiniRocketEntity> type, Level level) {
        super(type, level);
    }

    public MiniRocketEntity(Level level, double x, double y, double z) {
        super(ModEntities.MINI_ROCKET.get(), level);
        setPos(x, y, z);
    }

    @Override
    protected Item getDefaultItem() {
        return org.restor.fpv.item.ModItems.MINI_ROCKET.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.008;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof Pilotable);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 back = getDeltaMovement().scale(-0.35);
            level().addParticle(ParticleTypes.SMOKE, getX() + back.x, getY() + back.y, getZ() + back.z, 0, 0, 0);
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > MAX_LIFE) {
            detonate(position(), null);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity owner = getOwner();
        result.getEntity().hurt(owner instanceof net.minecraft.world.entity.LivingEntity living
            ? damageSources().mobProjectile(this, living)
            : damageSources().explosion(this, owner), IMPACT_DAMAGE);
        detonate(result.getLocation(), null);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide) return;
        detonate(result.getLocation(), result.getBlockPos());
    }

    private void detonate(Vec3 at, BlockPos hit) {
        if (!isAlive()) return;
        Entity owner = getOwner() != null ? getOwner() : this;
        discard();
        Level lvl = level();
        lvl.explode(owner, at.x, at.y, at.z, BLAST_RADIUS, Level.ExplosionInteraction.NONE);
        if (hit != null) breakSingleBlock(lvl, hit, owner);
    }

    private static void breakSingleBlock(Level lvl, BlockPos pos, Entity owner) {
        BlockState state = lvl.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return;
        float hardness = state.getDestroySpeed(lvl, pos);
        if (hardness < 0f || hardness > MAX_BREAK_HARDNESS) return;
        if (state.getBlock().getExplosionResistance() >= 100f) return;
        lvl.destroyBlock(pos, true, owner);
    }
}
