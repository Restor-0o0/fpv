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
    //   20 → factor=1.0 (instant), 13 → 0.65, 8 → 0.40 (soft/cinematic)
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

    // motorPower is max total thrust in Newtons. Thrust follows thrust = motorPower·throttle²
    // (THROTTLE_EXP=2 in DroneEntity, matching real props where thrust ∝ RPM² and RPM ∝ throttle),
    // so hover stick = √(1/(maxThrottle·TWR)). With maxThrottle=1 that is just √(1/TWR): a 5"
    // racer (TWR ~13) hovers ~27% stick, a cinewhoop (TWR ~7.6) ~36%, a whoop (TWR ~9) ~33% — and
    // full stick still slams (net climb ≈ (TWR−1)·g). Rates are full-stick °/s: expo keeps the
    // center gentle, ACRO has no attitude limits.
    // Presets tuned to real-world AUW masses (fully equipped, with battery). Top speed is a
    // thrust↔drag force balance (mass cancels): a pitched-forward quad flies edge-on to the
    // airflow, so forward flight uses the LOW planar dragCoefficient (see DroneEntity.applyDrag —
    // the heavy AXIAL_DRAG_MULT only kicks in when air hits the disc head-on: descent/braking).
    // v_max ≈ sqrt(maxThrottle*motorPower / (k*AIR_DENSITY*dragCoefficient)), capped by DEV_MAX_SPEED.
    // DEV_POWER_MULT is a global fine test multiplier; there is no longer a global mass/TWR knob.
    //   type       AUW      thrust  TWR    hover%
    //   Tiny Whoop 0.028kg   2.5 N   9.1   ~33%
    //   Cinewhoop  0.35kg   26.0 N   7.6   ~36%
    //   5" Racing  0.65kg   85.0 N  13.3   ~27%
    public static final DroneData CINEWHOOP = new DroneData(
        DroneType.CINEWHOOP, "Cinewhoop", 1.0f,
        220f, 300f, 250f,   // rolls take ~1.6s at full stick; expo softens the center
        0.35f, 0.037f, 650f,
        15f, 26.0f, 0.55f,  // ducted cruiser: TWR ~7.6, hover ~36%
        13.0f
    );

    public static final DroneData RACING = new DroneData(
        DroneType.RACING, "5-inch Racing", 1.0f,
        450f, 500f, 350f,   // racing: aggressive but roll toned down from 600°/s
        0.65f, 0.017f, 1300f,
        20f, 85.0f, 0.80f,  // TWR ~13.3: hover ~27%, ~12 g of climb authority on full stick
        22.0f
    );

    public static final DroneData TINY_WHOOP = new DroneData(
        DroneType.TINY_WHOOP, "Tiny Whoop", 1.0f,
        240f, 300f, 270f,   // playful: nimble but not twitchy
        0.028f, 0.013f, 300f,
        0f, 2.5f, 0.40f,    // 65mm 1S: TWR ~9.1, hover ~33%
        16.0f
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
