package org.restor.fpv.entity;

/** Autonomous behaviour a firmware-equipped drone is currently flying. */
public enum DroneMode {
    OFF,
    FOLLOW,
    PATROL,
    GUARD_PLAYER,
    GUARD_AREA;

    public String translationKey() {
        return "msg.fpv.mode." + name().toLowerCase();
    }

    public boolean isAutonomous() { return this != OFF; }

    /** Modes that hold a fixed spot instead of trailing the owner. */
    public boolean isAnchored() { return this == PATROL || this == GUARD_AREA; }

    public boolean engagesHostiles() { return this == GUARD_PLAYER || this == GUARD_AREA; }

    public static DroneMode byOrdinal(int i) {
        DroneMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : OFF;
    }
}
