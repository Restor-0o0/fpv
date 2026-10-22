package org.restor.fpv.flight;

/**
 * Control laws the flight controller can fly, mirroring the ones a real FPV firmware offers.
 * Ordinals are persisted in NBT and synced to clients, so new modes are appended.
 */
public enum FlightMode {
    /** Rate mode: the sticks command angular velocity and nothing levels the craft. */
    ACRO,
    /** Level below a stick threshold, full rate authority past it — flips still possible. */
    HORIZON,
    /** Self-levelling: stick deflection maps to a bank angle, released sticks level off. */
    ANGLE,
    /** Angle plus a vertical-speed throttle: centred stick holds the current altitude. */
    ALT_HOLD,
    /** Flip-over-after-crash: props push the other way so an upended drone rights itself. */
    TURTLE;

    public String translationKey() {
        return "msg.fpv.flight_mode." + name().toLowerCase();
    }

    public boolean selfLevelling() {
        return this == ANGLE || this == ALT_HOLD;
    }

    public boolean holdsAltitude() {
        return this == ALT_HOLD;
    }

    /** Maximum bank the levelling law will command, in degrees. */
    public float tiltLimit() {
        return switch (this) {
            case ALT_HOLD -> 30f;
            case HORIZON -> 55f;
            default -> 45f;
        };
    }

    public static FlightMode byOrdinal(int i) {
        FlightMode[] v = values();
        return i >= 0 && i < v.length ? v[i] : ACRO;
    }
}
