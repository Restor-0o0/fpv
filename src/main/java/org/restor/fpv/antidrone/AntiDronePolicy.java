package org.restor.fpv.antidrone;

import java.util.UUID;

public final class AntiDronePolicy {
    public record Owner(UUID id, String team, boolean privileged) {
        public Owner {
            team = team == null ? "" : team;
        }
    }

    public static boolean canTarget(Owner source, Owner target) {
        if (target.privileged()) return false;
        if (source.id() != null && source.id().equals(target.id())) return false;
        return source.team().isBlank() || !source.team().equals(target.team());
    }

    public static float falloff(double distance, double radius, float maximumStrength) {
        if (!Double.isFinite(distance) || !Double.isFinite(radius) || !Float.isFinite(maximumStrength)
            || radius <= 0.0 || maximumStrength <= 0.0f || distance >= radius) return 0.0f;
        double normalizedDistance = Math.max(0.0, distance) / radius;
        return clamp01((float) ((1.0 - normalizedDistance) * maximumStrength));
    }

    public static float applyToRssi(float rssi, float interference) {
        if (!Float.isFinite(rssi)) return 0.0f;
        if (!Float.isFinite(interference)) return clamp01(rssi);
        return clamp01(rssi - Math.max(0.0f, interference));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private AntiDronePolicy() {}
}
