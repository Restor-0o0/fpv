package org.restor.fpv.rules;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Locale;

public final class ExplosiveParam {
    public enum Kind { NUMBER, INTEGER, FLAG }

    private static volatile double[] remote;

    public final Explosive.Stat stat;
    public final Kind kind;
    public final double min;
    public final double max;
    private final double def;
    Explosive owner;
    int index;
    ModConfigSpec.ConfigValue<?> config;

    ExplosiveParam(Explosive.Stat stat, Kind kind, double def, double min, double max) {
        this.stat = stat;
        this.kind = kind;
        this.def = def;
        this.min = min;
        this.max = max;
    }

    static ExplosiveParam number(Explosive.Stat stat, double def, double min, double max) {
        return new ExplosiveParam(stat, Kind.NUMBER, def, min, max);
    }

    static ExplosiveParam integer(Explosive.Stat stat, double def, double max) {
        return new ExplosiveParam(stat, Kind.INTEGER, def, 0, max);
    }

    static ExplosiveParam flag(Explosive.Stat stat, boolean def) {
        return new ExplosiveParam(stat, Kind.FLAG, def ? 1 : 0, 0, 1);
    }

    public Explosive owner() { return owner; }

    public String id() { return owner.id + "." + stat.id; }

    public boolean flag() { return kind == Kind.FLAG; }

    public double value() {
        double[] synced = remote;
        if (synced != null && index < synced.length) return synced[index];
        return local();
    }

    public float f() { return (float) value(); }

    public int i() { return (int) Math.round(value()); }

    public boolean on() { return value() >= 0.5; }

    public double local() {
        if (config == null) return def;
        Object v;
        try {
            v = config.get();
        } catch (IllegalStateException notLoaded) {
            return def;
        }
        return v instanceof Boolean b ? (b ? 1 : 0) : ((Number) v).doubleValue();
    }

    public double defaultValue() { return def; }

    public double clamp(double v) {
        return switch (kind) {
            case FLAG -> v >= 0.5 ? 1 : 0;
            case INTEGER -> Math.round(Math.max(min, Math.min(max, v)));
            case NUMBER -> Math.max(min, Math.min(max, v));
        };
    }

    @SuppressWarnings("unchecked")
    public void setLocal(double v) {
        double clamped = clamp(v);
        if (config instanceof ModConfigSpec.BooleanValue b) b.set(clamped >= 0.5);
        else if (config != null) ((ModConfigSpec.ConfigValue<Double>) config).set(clamped);
    }

    public String format(double v) {
        if (kind == Kind.FLAG) return v >= 0.5 ? "true" : "false";
        if (kind == Kind.INTEGER) return Long.toString(Math.round(v));
        String s = String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    public String range() {
        return flag() ? "true / false" : format(min) + " .. " + format(max);
    }

    public static ExplosiveParam byId(String id) {
        int dot = id.indexOf('.');
        if (dot < 0) return null;
        Explosive e = Explosive.byId(id.substring(0, dot));
        return e == null ? null : e.param(id.substring(dot + 1));
    }

    public static List<ExplosiveParam> all() { return Explosive.PARAMS; }

    public static double[] localValues() {
        double[] out = new double[all().size()];
        for (ExplosiveParam p : all()) out[p.index] = p.local();
        return out;
    }

    public static double[] currentValues() {
        double[] out = new double[all().size()];
        for (ExplosiveParam p : all()) out[p.index] = p.value();
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
}
