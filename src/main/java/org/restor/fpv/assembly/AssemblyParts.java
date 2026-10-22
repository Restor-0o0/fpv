package org.restor.fpv.assembly;

import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.data.DroneData;

/**
 * A build with the items stripped off: the stats of every fitted part and how worn it is.
 * Everything a build is worth in the air — mass, thrust, trim, wear — is derived here, from
 * the numbers alone, so a build can be weighed without a world around it.
 */
public record AssemblyParts(
    ComponentStats frame, float frameCondition,
    ComponentStats battery, float batteryCondition,
    ComponentStats camera, float cameraCondition,
    ComponentStats module,
    ComponentStats firmware,
    ComponentStats vtx,
    ComponentStats fc,
    ComponentStats[] motors, float[] motorConditions,
    ComponentStats[] props, float[] propConditions,
    boolean wing
) {
    public static final int SLOTS = DroneAssembly.MAX_MOTORS;

    // Thrust per motor-power unit. A 4×2207+5x4x3 4S build lands at ~TWR 5 — enough that level
    // cruise sits at ~40-45° pitch (not 65°, which TWR ~8+ forced), while low drag still gives a
    // high top speed. More cells (6S) or stronger motors push TWR up for a punchier build.
    private static final float THRUST_N_PER_POWER = 6.6f;
    // Multirotors are calibrated to real static thrust: a 2207 on a 5x4 makes ~11 N per motor.
    private static final float ROTOR_THRUST_N_PER_POWER = 11f;
    // An unloading prop spins up toward its no-load RPM, and its airfoil still lifts slightly past
    // the geometric pitch, so thrust only dies at about 1.25x (no-load RPM x pitch).
    private static final float ZERO_THRUST_PITCH_RATIO = 1.25f;
    // Link range factor of a build with no video transmitter fitted.
    private static final float NO_VTX_RANGE = 0.4f;

    public AssemblyParts {
        if (motors.length != SLOTS || props.length != SLOTS
            || motorConditions.length != SLOTS || propConditions.length != SLOTS) {
            throw new IllegalArgumentException("motor and prop slots must be " + SLOTS + " long");
        }
    }

    /** Frame size in inches (prop class), or 5 when the frame slot is empty. */
    public float frameSize() {
        return frame != null ? frame.frameSize() : 5f;
    }

    public float vtxRangeMultiplier() {
        return vtx != null ? vtx.vtxRangeMultiplier() : NO_VTX_RANGE;
    }

    public boolean hasOsd() {
        return fc != null && fc.fcHasOsd();
    }

    /** Axial airspeed in m/s at which the props stop making thrust. */
    public float propPitchSpeed() {
        float pitch = 0f, kv = 0f;
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (props[i] == null || motors[i] == null) continue;
            pitch += props[i].propPitch();
            kv += motors[i].motorKv();
            n++;
        }
        if (n == 0) return 40f;
        float volts = (battery != null && battery.batteryCells() > 0 ? battery.batteryCells() : 4) * 3.7f;
        float rpm = kv / n * volts * ZERO_THRUST_PITCH_RATIO;
        return Math.max(5f, rpm / 60f * (pitch / n) * 0.0254f);
    }

    /** Average prop diameter in inches; falls back to the frame size when no props are fitted. */
    public float propDiameterInches() {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (props[i] == null) continue;
            sum += props[i].propDiameter();
            n++;
        }
        return n > 0 ? sum / n : frameSize();
    }

    public float totalMassKg() {
        float frameSize = frameSize();
        // Electronics (FC, ESC, RX, wiring, VTX, mounts) scale with frame size — a whoop carries
        // a few grams, a 7" a full stack. A flat baseline made small builds absurdly heavy.
        float g = wing ? 8f + frameSize * 14f : 3.12f * frameSize * frameSize;
        g += weight(frame) + weight(battery) + weight(camera) + weight(module) + weight(firmware);
        g += weight(vtx) + weight(fc);
        for (int i = 0; i < SLOTS; i++) g += weight(motors[i]) + weight(props[i]);
        return Math.max(0.02f, g / 1000f);
    }

    private static float weight(ComponentStats stats) {
        return stats != null ? stats.weight() : 0f;
    }

    /** Relative thrust factor of motor slot i (motor power × prop geometry/efficiency). */
    public float motorThrustFactor(int i) {
        ComponentStats motor = motors[i];
        ComponentStats prop = props[i];
        if (motor == null || prop == null) return 0;
        // Momentum theory: static thrust goes as (shaft power × disc diameter)^(2/3), so a small
        // prop loses far less thrust than its area suggests — it just spins much faster. Scaling
        // thrust linearly with diameter on top of the already-small motorPower double-counted the
        // size penalty and left whoops and cinewhoops unable to lift themselves. Normalized so a
        // 2207 on a 5x4x3 still reads 1.0.
        // A multirotor prop is thrust-limited by its disc, not its motor: a 65mm whoop prop makes
        // about 1/20 of a 5" prop. Planes keep the gentler power-limited curve.
        double diameterExp = wing ? 2.0 / 3.0 : 2.0;
        float propFactor = prop.propEfficiency()
            * (float) Math.pow(prop.propDiameter() / 5f, diameterExp)
            * (float) Math.pow(Math.max(0.1f, prop.propPitch()) / 4f, 0.3);
        return (float) Math.pow(motor.motorPower(), 2.0 / 3.0) * propFactor * slotEfficiency(i)
            * motorConditions[i] * propConditions[i];
    }

    /**
     * How well the parts in motor slot i work together: 1.0 when everything fits, lower when
     * the prop is wrong for the motor or either part is wrong for the frame. Nothing is
     * forbidden — a mismatched corner simply pulls less, which computeTrim turns into drift.
     */
    public float slotEfficiency(int i) {
        ComponentStats motor = motors[i];
        ComponentStats prop = props[i];
        if (motor == null || prop == null) return 0;
        float efficiency = 1f;
        var motorSpec = new DroneAssemblyValidator.MotorSpec(
            motor.motorKv(), motor.motorPower(), motor.motorDiameter());
        if (!DroneAssemblyValidator.isMotorPropCompatible(motorSpec,
                new DroneAssemblyValidator.PropSpec(prop.propDiameter()), wing)) {
            efficiency *= 0.6f;
        }
        if (frame != null) {
            float size = frame.frameSize();
            if (!ComponentFit.motor(size, wing, motor)) efficiency *= 0.82f;
            if (!ComponentFit.propeller(size, wing, prop)) efficiency *= 0.82f;
        }
        return efficiency;
    }

    /**
     * How hard the build runs outside its spec. 1.0 = everything within limits; higher values
     * wear motors and props out over the course of a flight (ModularDroneEntity).
     */
    public float stressFactor(int motorCount) {
        float stress = 1f;
        if (frame != null && battery != null && !ComponentFit.battery(frame.frameSize(), wing, battery)) {
            stress += 0.4f;
        }
        int mismatched = 0;
        for (int i = 0; i < motorCount; i++) {
            ComponentStats motor = motors[i];
            if (motor == null) continue;
            if (battery != null && !DroneAssemblyValidator.isMotorBatteryCompatible(
                    new DroneAssemblyValidator.MotorSpec(
                        motor.motorKv(), motor.motorPower(), motor.motorDiameter()),
                    new DroneAssemblyValidator.BatterySpec(battery.batteryCells()))) {
                mismatched++;
            }
            if (slotEfficiency(i) < 1f) mismatched++;
        }
        return motorCount == 0 ? stress : stress + 0.6f * mismatched / motorCount;
    }

    /** Max total thrust in Newtons. */
    public float totalThrustN(int motorCount) {
        float sum = 0;
        for (int i = 0; i < motorCount; i++) sum += motorThrustFactor(i);
        float frameFactor = 0.5f + 0.5f * frameCondition;
        float perPower = wing ? THRUST_N_PER_POWER : ROTOR_THRUST_N_PER_POWER;
        return Math.max(1f, sum * perPower * batteryThrustFactor() * frameFactor);
    }

    // More battery cells = more voltage = more motor RPM = more thrust. Normalized to the frame's
    // nominal cell count (a whoop on 1S, a 5" on 4S, a 7"/octo on 6S all read 1.0), so it only
    // rewards over-volting / penalizes under-volting — otherwise a heavier 6S pack only ADDED
    // mass and a tiny 1S read the HIGHEST TWR, which was backwards.
    private float batteryThrustFactor() {
        if (battery == null || battery.batteryCells() <= 0) return 1f;
        float size = frameSize();
        float refCells = size <= 2f ? 1f : size <= 6.5f ? 4f : 6f;
        float voltageFactor = (float) Math.pow(battery.batteryCells() / refCells, 0.7);
        float fitFactor = frame != null && !ComponentFit.battery(size, wing, battery) ? 0.9f : 1f;
        return voltageFactor * fitFactor * (0.5f + 0.5f * batteryCondition);
    }

    /**
     * Constant attitude trim in °/s at FULL throttle caused by thrust imbalance
     * between motor slots (e.g. one weaker prop). Returns {roll, pitch, yaw}.
     * Sign conventions match DroneEntity: positive roll = right bank, positive
     * pitch rate = nose down, yaw uses stick sign (positive = turn right).
     */
    public float[] computeTrim(FrameSpec spec) {
        if (spec == null) return new float[]{0, 0, 0};
        int n = spec.motorCount();
        float avg = 0;
        for (int i = 0; i < n; i++) avg += motorThrustFactor(i);
        avg /= n;
        if (avg <= 0) return new float[]{0, 0, 0};

        float roll = 0, pitch = 0, yaw = 0;
        for (int i = 0; i < n; i++) {
            float rel = motorThrustFactor(i) / avg - 1f; // + = this corner is stronger
            MountPoint m = spec.motorMounts().get(i);
            float nx = m.x() / spec.armLength();
            float nz = m.z() / spec.armLength();
            // Stronger right side (+x) lifts right → banks LEFT (negative roll).
            roll  += -rel * nx;
            // Stronger front (-z) lifts nose → pitches UP (negative pitch rate) —
            // rel * nz is already negative for front mounts.
            pitch += rel * nz;
            // Standard props-out spin layout: even slots CCW, odd CW. A stronger
            // CCW motor adds CW body yaw (turn right = positive stick sense).
            yaw   += rel * (i % 2 == 0 ? 1f : -1f);
        }
        // Full mismatch on one corner of a quad ≈ 0.25 → ~30°/s trim at full throttle.
        float k = 120f;
        return new float[]{roll * k, pitch * k, yaw * k * 0.5f};
    }

    public DroneAssemblyValidator.ValidationResult validate(FrameSpec spec, boolean frameComponentsCompatible) {
        int motorCount = spec != null ? spec.motorCount() : 0;
        var motorSpecs = new DroneAssemblyValidator.MotorSpec[SLOTS];
        var propSpecs = new DroneAssemblyValidator.PropSpec[SLOTS];
        float[] thrustFactors = new float[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            if (motors[i] != null) {
                motorSpecs[i] = new DroneAssemblyValidator.MotorSpec(
                    motors[i].motorKv(), motors[i].motorPower(), motors[i].motorDiameter());
            }
            if (props[i] != null) propSpecs[i] = new DroneAssemblyValidator.PropSpec(props[i].propDiameter());
            thrustFactors[i] = motorThrustFactor(i);
        }
        var batterySpec = battery == null ? null
            : new DroneAssemblyValidator.BatterySpec(battery.batteryCells());
        return DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            motorCount, frame != null, camera != null, frameComponentsCompatible,
            motorSpecs, propSpecs, batterySpec,
            totalMassKg(), spec == null ? 1 : totalThrustN(motorCount), thrustFactors,
            cameraCondition, wing, fc != null, vtx != null));
    }

    /** Flight characteristics derived from the installed components. */
    public DroneData buildDroneData(FrameSpec spec) {
        float mass = totalMassKg();
        float thrust = spec == null ? 1 : totalThrustN(spec.motorCount());
        float frameSize = frameSize();
        // Drag area grows with frame size. Was 0.005·size (5" ≈ 0.025) which capped top speed
        // ~2× too low vs a real 5"; 0.0032·size (5" ≈ 0.016) gives ~170 km/h headroom.
        FrameTuning tuning = spec != null ? spec.tuning() : FrameTuning.DEFAULT;
        float dragCdA = 0.0032f * frameSize * tuning.dragFactor();
        float camTilt = camera != null ? camera.cameraTiltAngle() : 20f;
        int capacity = battery != null
            ? Math.max(1, Math.round(battery.batteryCapacity() * (0.5f + 0.5f * batteryCondition)))
            : 650;

        // Bigger frames rotate slower; responsiveness drops with size too.
        float rate = Math.max(200f, 700f - frameSize * 50f) * tuning.agility();
        float resp = Math.max(10f, 26f - frameSize * 1.5f) * tuning.responsiveness();

        if (spec != null && spec.isHeavyLift()) {
            camTilt = -45f;
            rate *= 0.55f;
            resp *= 0.75f;
        }
        // The tailsitter camera looks straight out of the nose, i.e. along the thrust axis.
        if (spec != null && spec.isTailsitter()) {
            camTilt = 90f;
        }

        return new DroneData(
            DroneData.DroneType.CUSTOM, "Custom Build", 1.0f,
            rate * 0.9f, rate, rate * 0.6f,
            mass, dragCdA, capacity,
            camTilt, thrust, spec != null ? spec.armLength() : 0.5f,
            resp, propPitchSpeed()
        );
    }
}
