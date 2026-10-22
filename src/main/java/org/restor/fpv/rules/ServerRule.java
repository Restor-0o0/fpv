package org.restor.fpv.rules;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.restor.fpv.CommonConfig;

import java.util.Locale;

public enum ServerRule {
    BATTERY("battery", CommonConfig.REALISTIC_BATTERY, 0, 1),
    INFINITE_AMMO("infiniteAmmo", CommonConfig.INFINITE_AMMO, 0, 1),
    NO_DAMAGE("noDamage", CommonConfig.DEV_NO_DAMAGE, 0, 1),
    RADIO("radio", CommonConfig.REALISTIC_RADIO, 0, 1),
    REQUIRE_TRANSMITTER("requireTransmitter", CommonConfig.REQUIRE_TRANSMITTER, 0, 1),
    REQUIRE_GOGGLES("requireGoggles", CommonConfig.REQUIRE_GOGGLES_FOR_FPV, 0, 1),
    EXPLOSION_POWER("explosionPower", CommonConfig.DEV_EXPLOSION_POWER, 0.4, 40),
    BLOCK_BREAK("blockBreak", CommonConfig.DEV_EXPLOSION_BLOCK_BREAK_PERCENT, 0, 100),
    DEBRIS("debris", CommonConfig.DEV_EXPLOSION_BLOCK_FLING_PERCENT, 0, 200),
    MOB_DAMAGE("mobDamage", CommonConfig.DEV_EXPLOSION_MOB_DAMAGE_MULT, 0, 5),
    BLAST_CAMERA("blastCameraSeconds", CommonConfig.DEV_EXPLOSION_CAMERA_SECONDS, 0, 6),
    MOTOR_POWER("motorPower", CommonConfig.DEV_POWER_MULT, 0.2, 5),
    MAX_SPEED("maxSpeed", CommonConfig.DEV_MAX_SPEED, 10, 150);

    private static volatile double[] remote;

    public final String id;
    public final double min;
    public final double max;
    private final ModConfigSpec.ConfigValue<?> config;

    ServerRule(String id, ModConfigSpec.ConfigValue<?> config, double min, double max) {
        this.id = id;
        this.config = config;
        this.min = min;
        this.max = max;
    }

    public boolean flag() {
        return config instanceof ModConfigSpec.BooleanValue;
    }

    public boolean on() {
        return value() >= 0.5;
    }

    public double value() {
        double[] synced = remote;
        return synced != null ? synced[ordinal()] : local();
    }

    public double local() {
        return toDouble(config.get());
    }

    public double defaultValue() {
        return toDouble(config.getDefault());
    }

    public double clamp(double v) {
        if (flag()) return v >= 0.5 ? 1 : 0;
        return Math.max(min, Math.min(max, v));
    }

    @SuppressWarnings("unchecked")
    public void setLocal(double v) {
        double clamped = clamp(v);
        if (config instanceof ModConfigSpec.BooleanValue b) b.set(clamped >= 0.5);
        else ((ModConfigSpec.ConfigValue<Double>) config).set(clamped);
    }

    public String format(double v) {
        if (flag()) return v >= 0.5 ? "true" : "false";
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    public static ServerRule byId(String id) {
        for (ServerRule rule : values()) {
            if (rule.id.equals(id)) return rule;
        }
        return null;
    }

    public static double[] localValues() {
        double[] out = new double[values().length];
        for (ServerRule rule : values()) out[rule.ordinal()] = rule.local();
        return out;
    }

    public static double[] currentValues() {
        double[] out = new double[values().length];
        for (ServerRule rule : values()) out[rule.ordinal()] = rule.value();
        return out;
    }

    public static void setRemote(double[] values) {
        double[] copy = localValues();
        System.arraycopy(values, 0, copy, 0, Math.min(values.length, copy.length));
        remote = copy;
    }

    public static void clearRemote() {
        remote = null;
    }

    private static double toDouble(Object v) {
        if (v instanceof Boolean b) return b ? 1 : 0;
        return ((Number) v).doubleValue();
    }
}
