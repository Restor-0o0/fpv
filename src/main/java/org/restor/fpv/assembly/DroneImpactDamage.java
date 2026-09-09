package org.restor.fpv.assembly;

public final class DroneImpactDamage {
    private static final double MIN_DAMAGE_ENERGY_J = 0.75;
    private static final double STRONG_IMPACT_ENERGY_J = 6.0;

    public record Allocation(int motorIndex, int propDamage, int motorDamage, int frameDamage) {
        public boolean hasDamage() {
            return propDamage > 0 || motorDamage > 0 || frameDamage > 0;
        }
    }

    public static Allocation allocate(float massKg, double deltaVelocityX, double deltaVelocityY,
                                      double deltaVelocityZ, float[] mountX, float[] mountZ) {
        if (massKg <= 0 || mountX.length == 0 || mountX.length != mountZ.length) {
            return new Allocation(-1, 0, 0, 0);
        }
        double speedSq = deltaVelocityX * deltaVelocityX
            + deltaVelocityY * deltaVelocityY
            + deltaVelocityZ * deltaVelocityZ;
        double energy = 0.5 * massKg * speedSq;
        if (!Double.isFinite(energy) || energy < MIN_DAMAGE_ENERGY_J) {
            return new Allocation(-1, 0, 0, 0);
        }

        int motorIndex = nearestMount(deltaVelocityX, deltaVelocityZ, mountX, mountZ);
        int damage = Math.max(20, Math.min(650, (int) Math.round(energy * 20.0)));
        if (energy < STRONG_IMPACT_ENERGY_J) {
            return new Allocation(motorIndex, damage, 0, 0);
        }
        return new Allocation(motorIndex, 0, Math.max(1, Math.round(damage * 0.75f)), damage);
    }

    static int nearestMount(double impactX, double impactZ, float[] mountX, float[] mountZ) {
        if (impactX * impactX + impactZ * impactZ < 1.0e-8) return 0;
        int bestIndex = 0;
        double best = -Double.MAX_VALUE;
        for (int i = 0; i < mountX.length; i++) {
            double radius = Math.sqrt(mountX[i] * mountX[i] + mountZ[i] * mountZ[i]);
            double score = radius > 1.0e-8
                ? (mountX[i] * impactX + mountZ[i] * impactZ) / radius
                : -Double.MAX_VALUE;
            if (score > best) {
                best = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private DroneImpactDamage() {}
}
