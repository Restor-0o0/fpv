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
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.rules.Explosive;

public class MiniRocketEntity extends net.minecraft.world.entity.projectile.ThrowableItemProjectile {

    private static final int MAX_LIFE = 120;

    public MiniRocketEntity(EntityType<? extends MiniRocketEntity> type, Level level) {
        super(type, level);
    }

    public MiniRocketEntity(Level level, double x, double y, double z) {
        super(ModEntities.MINI_ROCKET.get(), level);
        setPos(x, y, z);
    }

    public static float blastRadius() {
        return Explosive.MINI_ROCKET.param(Explosive.Stat.POWER).f();
    }

    public static float impactDamage() {
        return Explosive.MINI_ROCKET.param(Explosive.Stat.IMPACT_DAMAGE).f();
    }

    public static int blocksPunched() {
        return Explosive.MINI_ROCKET.param(Explosive.Stat.BLOCKS).i();
    }

    @Override
    protected Item getDefaultItem() {
        return org.restor.fpv.item.ModItems.MINI_ROCKET.get();
    }

    @Override
    protected float getGravity() {
        return 0.008f;
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
        float damage = impactDamage();
        if (damage > 0.0f) {
            result.getEntity().hurt(owner instanceof net.minecraft.world.entity.LivingEntity living
                ? damageSources().mobProjectile(this, living)
                : damageSources().explosion(this, owner), damage);
        }
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
        Vec3 heading = getDeltaMovement();
        discard();
        Level lvl = level();
        float radius = blastRadius();
        if (radius > 0.0f) lvl.explode(owner, at.x, at.y, at.z, radius, Level.ExplosionInteraction.NONE);
        if (hit != null) punchThrough(lvl, hit, heading, owner);
    }

    // Walks the flight line from the struck block and stops at the first block too tough to break.
    private static void punchThrough(Level lvl, BlockPos first, Vec3 heading, Entity owner) {
        int budget = blocksPunched();
        if (budget <= 0) return;
        float maxHardness = Explosive.MINI_ROCKET.param(Explosive.Stat.HARDNESS).f();
        Vec3 dir = heading.lengthSqr() > 1.0e-6 ? heading.normalize() : Vec3.ZERO;
        Vec3 start = first.getCenter();
        BlockPos last = null;
        double reach = budget * 2.0 + 1.0;
        for (double t = 0; budget > 0 && t <= reach; t += 0.25) {
            BlockPos pos = BlockPos.containing(start.add(dir.scale(t)));
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = lvl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (!breakable(lvl, pos, state, maxHardness)) return;
            lvl.destroyBlock(pos, true, owner);
            budget--;
            if (dir == Vec3.ZERO) return;
        }
    }

    private static boolean breakable(Level lvl, BlockPos pos, BlockState state, float maxHardness) {
        float hardness = state.getDestroySpeed(lvl, pos);
        if (hardness < 0f || hardness > maxHardness) return false;
        return state.getBlock().getExplosionResistance() < 100f || maxHardness >= 50f;
    }
}
