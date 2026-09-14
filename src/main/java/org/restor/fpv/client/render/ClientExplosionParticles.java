package org.restor.fpv.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;

public final class ClientExplosionParticles {
    private ClientExplosionParticles() {}

    public static void spawn(double x, double y, double z, float spread) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        java.util.Random random = new java.util.Random(Double.doubleToLongBits(x) ^ Double.doubleToLongBits(z));
        double ey = y + 0.85;
        int smoke = 360;
        int cloud = 220;
        int flame = 130;

        for (int i = 0; i < smoke; i++) {
            add(mc, ParticleTypes.LARGE_SMOKE, x, ey, z, spread * 1.55, spread * 1.10, spread * 1.55, 0.045, random);
        }
        for (int i = 0; i < cloud; i++) {
            add(mc, ParticleTypes.CLOUD, x, ey + 0.2, z, spread * 1.35, spread * 0.90, spread * 1.35, 0.055, random);
        }
        for (int i = 0; i < flame; i++) {
            add(mc, ParticleTypes.FLAME, x, ey - 0.35, z, spread, spread * 0.70, spread, 0.13, random);
        }
        for (int i = 0; i < 80; i++) {
            add(mc, ParticleTypes.POOF, x, ey, z, spread * 1.65, spread * 1.15, spread * 1.65, 0.18, random);
        }
    }

    private static void add(Minecraft mc, net.minecraft.core.particles.ParticleOptions particle,
                            double x, double y, double z, double dx, double dy, double dz,
                            double speed, java.util.Random random) {
        double ox = (random.nextDouble() * 2.0 - 1.0) * dx;
        double oy = (random.nextDouble() * 2.0 - 1.0) * dy;
        double oz = (random.nextDouble() * 2.0 - 1.0) * dz;
        double vx = (random.nextDouble() * 2.0 - 1.0) * speed;
        double vy = random.nextDouble() * speed;
        double vz = (random.nextDouble() * 2.0 - 1.0) * speed;
        mc.level.addParticle(particle, x + ox, y + oy, z + oz, vx, vy, vz);
    }
}
