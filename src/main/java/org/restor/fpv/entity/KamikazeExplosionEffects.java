package org.restor.fpv.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KamikazeExplosionEffects {
    private KamikazeExplosionEffects() {}

    public static void spawn(Level level, double x, double y, double z, float power) {
        if (!(level instanceof ServerLevel sl)) return;

        double spread = Math.max(1.5, power * 0.8);
        burst(sl, x, y, z, spread);
        sl.playSound(null, x, y, z, org.restor.fpv.sound.ModSounds.WARHEAD_DETONATE.get(), SoundSource.HOSTILE,
            Math.min(4.0f, 1.4f + power * 0.18f), 0.75f);
        sl.playSound(null, x, y, z, org.restor.fpv.sound.ModSounds.WARHEAD_INCENDIARY.get(), SoundSource.HOSTILE,
            1.8f, 0.55f);
    }

    public static void damageMobs(Level level, Entity source, double x, double y, double z, float power) {
        if (!(level instanceof ServerLevel sl)) return;
        double mult = org.restor.fpv.Config.DEV_EXPLOSION_MOB_DAMAGE_MULT.get();
        if (mult <= 0.0) return;
        Vec3 center = new Vec3(x, y, z);
        double radius = Math.max(4.0, power * 1.6);
        float maxDamage = (float) ((6.0f + power * 7.0f) * mult);
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob != source)) {
            double dist = Math.sqrt(mob.distanceToSqr(center));
            if (dist > radius) continue;
            double falloff = Math.max(0.0, 1.0 - dist / radius);
            float damage = (float) (maxDamage * falloff);
            if (damage > 0.5f) {
                mob.hurt(sl.damageSources().explosion(source, source), damage);
            }
        }
    }

    public static void flingBlocks(Level level, double x, double y, double z, float power) {
        if (!(level instanceof ServerLevel sl)) return;
        double percent = org.restor.fpv.Config.DEV_EXPLOSION_BLOCK_FLING_PERCENT.get();
        if (percent <= 0.0) return;
        Vec3 center = new Vec3(x, y, z);
        int radius = (int) Math.max(2, Math.min(6, Math.ceil(power * 1.6)));
        int maxBlocks = (int) (Math.max(2, Math.min(35, power * power * 4)) * percent / 100.0);
        java.util.ArrayList<BlockPos> candidates = new java.util.ArrayList<>();
        BlockPos origin = BlockPos.containing(x, y, z);

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            double d2 = pos.getCenter().distanceToSqr(center);
            if (d2 > radius * radius) continue;
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.getDestroySpeed(sl, pos) < 0f || sl.getBlockEntity(pos) != null) continue;
            candidates.add(pos.immutable());
        }

        java.util.Collections.shuffle(candidates, new java.util.Random(sl.random.nextLong()));
        int count = Math.min(maxBlocks, candidates.size());
        for (int i = 0; i < count; i++) {
            BlockPos pos = candidates.get(i);
            BlockState state = sl.getBlockState(pos);
            if (state.isAir()) continue;
            Vec3 fromCenter = pos.getCenter().subtract(center);
            if (fromCenter.lengthSqr() < 1.0e-4) fromCenter = new Vec3(sl.random.nextDouble() - 0.5, 0.35, sl.random.nextDouble() - 0.5);
            Vec3 dir = fromCenter.normalize().add(0, 0.75 + sl.random.nextDouble() * 0.55, 0).normalize();
            Vec3 spawn = pos.getCenter().add(dir.scale(0.65 + sl.random.nextDouble() * 0.55)).add(0, 0.35, 0);
            FallingBlockEntity block = FallingBlockEntity.fall(sl, BlockPos.containing(spawn), state);
            block.setPos(spawn.x, spawn.y, spawn.z);
            double powerScale = Math.sqrt(Math.max(1.0, power)) / 4.0;
            double strength = (0.35 + sl.random.nextDouble() * 0.35
                + Math.max(0, radius - Math.sqrt(pos.getCenter().distanceToSqr(center))) * 0.025) * powerScale;
            block.setDeltaMovement(dir.scale(strength));
            block.hurtMarked = true;
        }
    }

    public static java.util.Map<BlockPos, BlockState> captureBreakableBlocks(Level level, double x, double y, double z, float power) {
        java.util.Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();
        if (!(level instanceof ServerLevel sl)) return blocks;
        double percent = org.restor.fpv.Config.DEV_EXPLOSION_BLOCK_BREAK_PERCENT.get();
        if (percent >= 100.0) return blocks;

        int radius = (int) Math.max(3, Math.min(10, Math.ceil(power * 2.0)));
        Vec3 center = new Vec3(x, y, z);
        BlockPos origin = BlockPos.containing(x, y, z);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (pos.getCenter().distanceToSqr(center) > radius * radius) continue;
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(sl, pos) < 0f || sl.getBlockEntity(pos) != null) continue;
            blocks.put(pos.immutable(), state);
        }
        return blocks;
    }

    public static void restoreUnbrokenBlocks(Level level, java.util.Map<BlockPos, BlockState> before, double x, double y, double z) {
        if (!(level instanceof ServerLevel sl) || before.isEmpty()) return;
        double breakPercent = org.restor.fpv.Config.DEV_EXPLOSION_BLOCK_BREAK_PERCENT.get();
        if (breakPercent <= 0.0) breakPercent = 0.0;
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>(before.keySet());
        java.util.Collections.shuffle(positions, new java.util.Random(sl.random.nextLong()));
        int keepBroken = (int) Math.round(positions.size() * Math.min(100.0, breakPercent) / 100.0);
        for (int i = keepBroken; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            if (sl.getBlockState(pos).isAir()) {
                sl.setBlock(pos, before.get(pos), 3);
            }
        }
    }

    public static void removeFloatingBlocks(Level level, double x, double y, double z, float power) {
        if (!(level instanceof ServerLevel sl)) return;
        Vec3 center = new Vec3(x, y, z);
        int radius = (int) Math.max(3, Math.min(10, Math.ceil(power * 2.2)));
        BlockPos origin = BlockPos.containing(x, y, z);
        java.util.ArrayList<BlockPos> floating = new java.util.ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
            if (pos.getCenter().distanceToSqr(center) > radius * radius) continue;
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.getDestroySpeed(sl, pos) < 0f || sl.getBlockEntity(pos) != null) continue;
            if (isUnsupported(sl, pos)) floating.add(pos.immutable());
        }

        floating.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        for (BlockPos pos : floating) {
            BlockState state = sl.getBlockState(pos);
            if (!state.isAir() && isUnsupported(sl, pos)) {
                sl.destroyBlock(pos, true);
            }
        }
    }

    private static boolean isUnsupported(ServerLevel sl, BlockPos pos) {
        if (sl.getBlockState(pos.below()).isFaceSturdy(sl, pos.below(), net.minecraft.core.Direction.UP)) return false;
        for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
            net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST}) {
            BlockPos n = pos.relative(dir);
            if (sl.getBlockState(n).isFaceSturdy(sl, n, dir.getOpposite())) return false;
        }
        return true;
    }

    static void burst(Level level, double x, double y, double z, double spread) {
        if (!(level instanceof ServerLevel sl)) return;
        burst(sl, x, y, z, spread);
    }

    private static void burst(ServerLevel sl, double x, double y, double z, double spread) {
        double ey = y + 0.85;
        double f = Math.max(0.32, Math.min(1.0, spread / 4.0));
        sendForced(sl, ParticleTypes.EXPLOSION_EMITTER, x, ey, z, 4, 0.35, 0.35, 0.35, 0.0);
        sendForced(sl, ParticleTypes.EXPLOSION, x, ey, z, (int) (32 * f), spread * 0.60, spread * 0.45, spread * 0.60, 0.06);
        sendForced(sl, ParticleTypes.LARGE_SMOKE, x, ey, z, (int) (520 * f), spread * 1.55, spread * 1.10, spread * 1.55, 0.045);
        sendForced(sl, ParticleTypes.CLOUD, x, ey + 0.2, z, (int) (260 * f), spread * 1.35, spread * 0.90, spread * 1.35, 0.055);
        sendForced(sl, ParticleTypes.POOF, x, ey, z, (int) (340 * f), spread * 1.65, spread * 1.15, spread * 1.65, 0.20);
        sendForced(sl, ParticleTypes.FLAME, x, ey - 0.35, z, (int) (170 * f), spread * 1.00, spread * 0.70, spread * 1.00, 0.13);
        sendForced(sl, ParticleTypes.LAVA, x, ey - 0.35, z, (int) (80 * f), spread * 0.70, spread * 0.60, spread * 0.70, 0.27);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
            new org.restor.fpv.network.ExplosionParticlesPacket(x, y, z, (float) spread));
    }

    private static <T extends ParticleOptions> void sendForced(ServerLevel sl, T particle,
                                                              double x, double y, double z, int count,
                                                              double dx, double dy, double dz, double speed) {
        for (var player : sl.players()) {
            sl.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    static Vec3 moveCameraBack(Entity entity) {
        double backstep = org.restor.fpv.Config.DEV_EXPLOSION_CAMERA_BACKSTEP.get();
        Vec3 impact = entity.position();
        if (backstep <= 0.0) return impact;

        Vec3 cameraPos;
        if (entity instanceof DroneEntity drone) {
            cameraPos = drone.cameraTrackBack(backstep);
        } else {
            // xo/yo/zo only carry the last step for entities the server still moves itself.
            Vec3 lastStep = impact.subtract(entity.xo, entity.yo, entity.zo);
            Vec3 vel = entity.getDeltaMovement();
            Vec3 dir = lastStep.lengthSqr() > 1.0e-4 ? lastStep.normalize()
                : vel.lengthSqr() > 1.0e-4 ? vel.normalize()
                : entity.getLookAngle();
            cameraPos = impact.subtract(dir.scale(backstep));
        }
        entity.setPos(cameraPos.x, cameraPos.y, cameraPos.z);
        entity.setDeltaMovement(Vec3.ZERO);
        lookAt(entity, impact);
        return impact;
    }

    static void lookAt(Entity entity, Vec3 target) {
        Vec3 d = target.subtract(entity.position());
        double len = d.length();
        if (len < 1.0e-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yRotO = yaw;
        entity.xRotO = pitch;
    }
}
