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

    // motorPower is max total thrust in Newtons. Thrust is per-drone (this preset, or a
    // module-built DroneAssembly), applied through a throttle² curve in DroneEntity, so a
    // realistic TWR (~5-11) hovers near ~30-43% stick — NOT the old linear ~9%. Full stick still
    // slams. Rates are full-stick °/s: expo keeps the center gentle, ACRO has no attitude limits.
    // Presets tuned to real-world AUW masses (fully equipped, with battery) and top-speed targets.
    // Top speed is a thrust↔drag force balance (mass cancels): a pitched-forward quad flies
    // edge-on to the airflow, so forward flight uses the LOW planar dragCoefficient (see
    // DroneEntity.applyDrag — the heavy AXIAL_DRAG_MULT only kicks in when air hits the disc
    // head-on: descent/braking). So more forward tilt → more speed, and v_max ≈
    // sqrt(maxThrottle*motorPower / (k*AIR_DENSITY*dragCoefficient)), capped by DEV_MAX_SPEED.
    // DEV_POWER_MULT is a global fine test multiplier; there is no longer a global mass/TWR knob.
    //   type       AUW      TWR    hover%   target v_max
    //   Tiny Whoop 0.03kg   10.2   ~31%     ~40 km/h (11 m/s)
    //   Cinewhoop  0.48kg    5.4   ~43%     ~70 km/h (19 m/s)
    //   5" Racing  0.75kg   10.9   ~30%     ~180 km/h (50 m/s)
    public static final DroneData CINEWHOOP = new DroneData(
        DroneType.CINEWHOOP, "Cinewhoop", 0.8f,
        220f, 300f, 250f,   // rolls take ~1.6s at full stick; expo softens the center
        0.48f, 0.037f, 650f,
        15f, 22.0f, 0.55f,  // heavy cruiser: TWR ~4.7, hover ~46% — weight is felt
        8.0f
    );

    public static final DroneData RACING = new DroneData(
        DroneType.RACING, "5-inch Racing", 1.0f,
        450f, 500f, 350f,   // racing: aggressive but roll toned down from 600°/s
        0.75f, 0.017f, 1300f,
        20f, 37.0f, 0.80f,  // TWR ~5: level cruise ~40-45° pitch, top speed still high (low drag)
        15.0f
    );

    public static final DroneData TINY_WHOOP = new DroneData(
        DroneType.TINY_WHOOP, "Tiny Whoop", 0.6f,
        240f, 300f, 270f,   // playful: nimble but not twitchy
        0.045f, 0.013f, 300f,
        0f, 3.5f, 0.40f,    // whoops stay punchy (very light): TWR ~12
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
