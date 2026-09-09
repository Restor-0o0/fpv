package org.restor.fpv.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.warhead.WarheadType;

public class DropChargeEntity extends net.minecraft.world.entity.projectile.ThrowableItemProjectile {

    private static final int MAX_LIFE = 400;

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
    protected float getGravity() {
        return 0.06f;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof Pilotable);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > MAX_LIFE) detonate();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) detonate();
    }

    private void detonate() {
        if (!isAlive()) return;
        Vec3 at = position();
        discard();
        WarheadDetonation.detonate(level(), getOwner() != null ? getOwner() : this, at, WarheadType.DROP);
    }
}
