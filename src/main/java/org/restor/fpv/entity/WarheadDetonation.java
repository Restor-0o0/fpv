package org.restor.fpv.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.warhead.WarheadType;

// Runs one warhead's blast: the shared crater/particle pass from KamikazeExplosionEffects,
// plus whatever the warhead family adds on top (fire, submunitions, extra mob damage).
public final class WarheadDetonation {
    private WarheadDetonation() {}

    // The dev slider is a plain multiplier around its 4.0 default.
    private static float scale() {
        return (float) (org.restor.fpv.Config.DEV_EXPLOSION_POWER.get() / 4.0);
    }

    public static void detonate(Level level, Entity source, Vec3 at, WarheadType type) {
        float s = scale();
        float power = type.power() * s;
        float blockPower = type.blockPower() * s;
        blast(level, source, at, power, blockPower, type.mobDamageMul());
        if (!(level instanceof ServerLevel sl)) return;

        if (type.incendiary()) scorch(sl, at, power);
        for (int i = 0; i < type.submunitions(); i++) {
            double a = sl.random.nextDouble() * Math.PI * 2;
            double r = 2.5 + sl.random.nextDouble() * power * 1.6;
            Vec3 sub = at.add(Math.cos(a) * r, sl.random.nextDouble() * 1.5, Math.sin(a) * r);
            blast(sl, source, sub, power * 0.8f, blockPower * 0.8f, type.mobDamageMul());
        }
    }

    public static void blast(Level level, Entity source, Vec3 at, float power,
                             float blockPower, float mobMul) {
        double x = at.x, y = at.y, z = at.z;
        KamikazeExplosionEffects.flingBlocks(level, x, y, z, blockPower);
        var before = KamikazeExplosionEffects.captureBreakableBlocks(level, x, y, z, blockPower);
        level.explode(source, x, y, z, blockPower, Level.ExplosionInteraction.TNT);
        KamikazeExplosionEffects.restoreUnbrokenBlocks(level, before, x, y, z);
        KamikazeExplosionEffects.removeFloatingBlocks(level, x, y, z, blockPower);
        damageMobs(level, source, at, power, mobMul);
        KamikazeExplosionEffects.spawn(level, x, y, z, power);
    }

    private static void damageMobs(Level level, Entity source, Vec3 center, float power, float mul) {
        if (!(level instanceof ServerLevel sl)) return;
        double cfg = org.restor.fpv.Config.DEV_EXPLOSION_MOB_DAMAGE_MULT.get();
        if (cfg <= 0.0 || mul <= 0.0f) return;
        double radius = Math.max(4.0, power * 1.6);
        float maxDamage = (float) ((6.0f + power * 7.0f) * cfg * mul);
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
            center.x + radius, center.y + radius, center.z + radius);
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m != source)) {
            double dist = Math.sqrt(mob.distanceToSqr(center));
            if (dist > radius) continue;
            float damage = (float) (maxDamage * Math.max(0.0, 1.0 - dist / radius));
            if (damage > 0.5f) mob.hurt(sl.damageSources().explosion(source, source), damage);
        }
    }

    private static void scorch(ServerLevel sl, Vec3 at, float power) {
        int r = (int) Math.max(2, Math.min(5, Math.round(power * 0.8f)));
        BlockPos origin = BlockPos.containing(at);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (pos.distSqr(origin) > (double) r * r) continue;
            if (sl.random.nextFloat() > 0.28f) continue;
            BlockState state = sl.getBlockState(pos);
            if (!state.isAir()) continue;
            if (!sl.getBlockState(pos.below()).isFaceSturdy(sl, pos.below(), net.minecraft.core.Direction.UP)) continue;
            sl.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
        }
    }
}
