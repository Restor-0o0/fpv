package org.restor.fpv.data;

import net.minecraft.nbt.CompoundTag;

public class DroneData {
    public enum DroneType {
        CINEWHOOP,
        RACING,
        TINY_WHOOP,
        CUSTOM
    }

    public final DroneType type;
    public final String displayName;
    public final float maxThrottle;
    public final float maxRollRate;
    public final float maxPitchRate;
    public final float maxYawRate;
    public final float mass;          // kg
    public final float dragCoefficient; // Cd * frontal_area (m²) — used in ½ρCdA v²
    public final float batteryCapacity;
    public final float cameraAngle;
    public final float motorPower;  // max total thrust in Newtons (SI physics)
    public final float armLength;   // centre-to-motor distance (blocks) — for ground effect
    // How fast angular velocity lerps toward stick target (1/s). Higher = sharper response.
    // factor per tick = min(rotationResponsiveness * dt, 1.0). At dt=0.05:
    //   20 → factor=1.0 (instant), 15 → 0.75, 8 → 0.40 (soft/cinematic)
    public final float rotationResponsiveness;

    public DroneData(DroneType type, String displayName, float maxThrottle,
                     float maxRollRate, float maxPitchRate, float maxYawRate,
                     float mass, float dragCoefficient, float batteryCapacity,
                     float cameraAngle, float motorPower, float armLength,
                     float rotationResponsiveness) {
        this.type = type;
        this.displayName = displayName;
        this.maxThrottle = maxThrottle;
        this.maxRollRate = maxRollRate;
        this.maxPitchRate = maxPitchRate;
        this.maxYawRate = maxYawRate;
        this.mass = mass;
        this.dragCoefficient = dragCoefficient;
        this.batteryCapacity = batteryCapacity;
        this.cameraAngle = cameraAngle;
        this.motorPower = motorPower;
        this.armLength = armLength;
        this.rotationResponsiveness = rotationResponsiveness;
    }

    // motorPower is max total thrust in Newtons, tuned for TWR ≈ 11 — full throttle
    // slams like a top-end racing quad. Hover stick lands at ≈ 9%
    // (hover_throttle = GRAVITY * mass / (maxThrottle * motorPower)).
    // Rates are full-stick °/s: expo keeps the center gentle, and ACRO has no attitude
    // limits — full stick held long enough performs flips/rolls through ±180°.
    // Presets tuned to real-world AUW masses (fully equipped, with battery) and top-speed
    // targets. Top speed is a thrust↔drag force balance (mass cancels): at full throttle a
    // high-TWR quad flies heavily pitched, so the prop disc faces the airflow and the 4x
    // axial drag (AXIAL_DRAG_MULT) dominates → v_max ≈ sqrt(maxThrottle*motorPower /
    // (k*AIR_DENSITY*dragCoefficient)), k ≈ 3 for the mixed axial/planar case. So drag
    // controls BOTH top speed and braking — lower drag = faster but softer braking.
    // For live testing, DEV_MASS_MULT / DEV_POWER_MULT (dev settings) scale mass/motorPower
    // globally across every drone type, including the remote/kamikaze ones below.
    //   type       AUW      TWR    hover%   target v_max
    //   Tiny Whoop 0.03kg   10.2   ~9.8%    ~40 km/h (11 m/s)
    //   Cinewhoop  0.35kg    7.5   ~13%     ~70 km/h (19 m/s)
    //   5" Racing  0.55kg   14.8   ~6.8%    ~180 km/h (50 m/s)
    public static final DroneData CINEWHOOP = new DroneData(
        DroneType.CINEWHOOP, "Cinewhoop", 0.8f,
        220f, 300f, 250f,   // rolls take ~1.6s at full stick; expo softens the center
        0.48f, 0.037f, 650f,
        15f, 32.0f, 0.55f,  // stable cruiser, not built for speed
        8.0f
    );

    public static final DroneData RACING = new DroneData(
        DroneType.RACING, "5-inch Racing", 1.0f,
        450f, 500f, 350f,   // racing: aggressive but roll toned down from 600°/s
        0.75f, 0.017f, 1300f,
        20f, 80.0f, 0.80f,  // light + max thrust + low drag = highest top speed
        15.0f
    );

    public static final DroneData TINY_WHOOP = new DroneData(
        DroneType.TINY_WHOOP, "Tiny Whoop", 0.6f,
        240f, 300f, 270f,   // playful: nimble but not twitchy
        0.045f, 0.013f, 300f,
        0f, 5.0f, 0.40f,    // almost a toy — slow top speed, very light
        10.0f
    );

    public static DroneData getData(DroneType type) {
        return switch (type) {
            case CINEWHOOP -> CINEWHOOP;
            case RACING -> RACING;
            case TINY_WHOOP -> TINY_WHOOP;
            case CUSTOM -> CINEWHOOP;
        };
    }
}
