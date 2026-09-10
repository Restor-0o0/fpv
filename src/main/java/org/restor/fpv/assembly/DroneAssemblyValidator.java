package org.restor.fpv.assembly;

import java.util.ArrayList;
import java.util.List;

/**
 * Only a build that physically cannot fly is rejected. Everything else — a half-populated
 * frame, oversized props, an over-volted motor — is a warning, and the mismatch is paid for
 * in the air: less thrust on that corner, constant trim (DroneAssembly.computeTrim) and
 * faster wear (DroneAssembly.stressFactor).
 */
public final class DroneAssemblyValidator {
    private static final float GRAVITY = 9.81f;
    private static final float LIFTOFF_TWR = 1.0f;
    private static final float COMFORTABLE_TWR = 1.4f;
    private static final float MAX_HOVER_THROTTLE = 0.85f;
    private static final float IMBALANCE_THRESHOLD = 0.08f;
    private static final float WING_MIN_TWR = 0.22f;
    private static final float WING_COMFORTABLE_TWR = 0.40f;

    public enum IssueCode {
        NO_FRAME("assembly.validation.no_frame", true),
        NO_BATTERY("assembly.validation.no_battery", true),
        NO_FC("assembly.validation.no_fc", true),
        NO_VTX("assembly.validation.no_vtx", true),
        NO_LIFT("assembly.validation.no_lift", true),
        MISSING_PARTS("assembly.validation.missing_parts", false),
        NO_CAMERA("assembly.validation.no_camera", false),
        FRAME_COMPONENT_MISMATCH("assembly.validation.frame_component_mismatch", false),
        MOTOR_PROP_INCOMPATIBLE("assembly.validation.motor_prop_incompatible", false),
        MOTOR_BATTERY_INCOMPATIBLE("assembly.validation.motor_battery_incompatible", false),
        LOW_TWR("assembly.validation.low_twr", false),
        EXCESSIVE_HOVER_THROTTLE("assembly.validation.excessive_hover_throttle", false),
        THRUST_IMBALANCE("assembly.validation.thrust_imbalance", false),
        DAMAGED_CAMERA("assembly.validation.damaged_camera", false);

        private final String localizationKey;
        private final boolean error;

        IssueCode(String localizationKey, boolean error) {
            this.localizationKey = localizationKey;
            this.error = error;
        }

        public String localizationKey() {
            return localizationKey;
        }

        public boolean isError() {
            return error;
        }

        public int mask() {
            return 1 << ordinal();
        }
    }

    public record MotorSpec(float kv, float power, float diameter) {}
    public record PropSpec(float diameter) {}
    public record BatterySpec(int cells) {}

    public record Input(
        int motorCount,
        boolean framePresent,
        boolean cameraPresent,
        boolean frameComponentsCompatible,
        MotorSpec[] motors,
        PropSpec[] props,
        BatterySpec battery,
        float massKg,
        float thrustN,
        float[] motorThrustFactors,
        float cameraCondition,
        boolean fixedWing,
        boolean fcPresent,
        boolean vtxPresent
    ) {}

    public record Metrics(float massKg, float thrustN, float twr, float hoverThrottle) {}

    public record ValidationResult(List<IssueCode> errors, List<IssueCode> warnings, Metrics metrics) {
        public ValidationResult {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean isComplete() {
            return errors.isEmpty();
        }

        public int mask() {
            int mask = 0;
            for (IssueCode code : errors) mask |= code.mask();
            for (IssueCode code : warnings) mask |= code.mask();
            return mask;
        }
    }

    public static ValidationResult validate(Input input) {
        List<IssueCode> errors = new ArrayList<>();
        List<IssueCode> warnings = new ArrayList<>();
        boolean wing = input.fixedWing();
        float twr = input.massKg() > 0 ? input.thrustN() / (input.massKg() * GRAVITY) : 0;
        // thrust ∝ throttle² (DroneEntity.THROTTLE_EXP=2), so hover stick = √(1/TWR).
        float hoverThrottle = twr > 0 ? (float) Math.sqrt(1f / twr) : 1f;
        Metrics metrics = new Metrics(input.massKg(), input.thrustN(), twr, hoverThrottle);

        int working = 0;
        int missing = 0;
        for (int i = 0; i < input.motorCount(); i++) {
            if (input.motors()[i] != null && input.props()[i] != null) working++;
            else missing++;
        }

        addIf(errors, !input.framePresent(), IssueCode.NO_FRAME);
        addIf(errors, input.battery() == null, IssueCode.NO_BATTERY);
        addIf(errors, !input.fcPresent(), IssueCode.NO_FC);
        addIf(errors, !input.vtxPresent(), IssueCode.NO_VTX);
        // A wing is carried by its wing, not by its prop: it only needs enough thrust to stay
        // above stall speed.
        float liftoff = wing ? WING_MIN_TWR : LIFTOFF_TWR;
        addIf(errors, input.framePresent() && (working == 0 || twr < liftoff), IssueCode.NO_LIFT);

        addIf(warnings, missing > 0 && working > 0, IssueCode.MISSING_PARTS);
        addIf(warnings, !input.cameraPresent(), IssueCode.NO_CAMERA);
        addIf(warnings, input.framePresent() && !input.frameComponentsCompatible(),
            IssueCode.FRAME_COMPONENT_MISMATCH);

        boolean motorPropMismatch = false;
        boolean motorBatteryMismatch = false;
        for (int i = 0; i < input.motorCount(); i++) {
            MotorSpec motor = input.motors()[i];
            PropSpec prop = input.props()[i];
            if (motor != null && prop != null && !isMotorPropCompatible(motor, prop, wing)) motorPropMismatch = true;
            if (motor != null && input.battery() != null && !isMotorBatteryCompatible(motor, input.battery())) {
                motorBatteryMismatch = true;
            }
        }
        addIf(warnings, motorPropMismatch, IssueCode.MOTOR_PROP_INCOMPATIBLE);
        addIf(warnings, motorBatteryMismatch, IssueCode.MOTOR_BATTERY_INCOMPATIBLE);

        if (errors.isEmpty()) {
            addIf(warnings, twr < (wing ? WING_COMFORTABLE_TWR : COMFORTABLE_TWR), IssueCode.LOW_TWR);
            addIf(warnings, !wing && hoverThrottle > MAX_HOVER_THROTTLE, IssueCode.EXCESSIVE_HOVER_THROTTLE);
            addIf(warnings, !wing && hasThrustImbalance(input.motorThrustFactors(), input.motorCount()),
                IssueCode.THRUST_IMBALANCE);
            addIf(warnings, input.cameraPresent() && input.cameraCondition() < 0.7f, IssueCode.DAMAGED_CAMERA);
        }
        return new ValidationResult(errors, warnings, metrics);
    }

    static boolean isMotorPropCompatible(MotorSpec motor, PropSpec prop) {
        return isMotorPropCompatible(motor, prop, false);
    }

    static boolean isMotorPropCompatible(MotorSpec motor, PropSpec prop, boolean fixedWing) {
        if (fixedWing) {
            float max = motor.power() <= 2.5f ? 9f : motor.power() <= 4f ? 11f : 13f;
            float min = motor.power() <= 2.5f ? 0f : motor.power() <= 4f ? 8f : 10f;
            return prop.diameter() >= min && prop.diameter() <= max;
        }
        float maxDiameter = motor.diameter() <= 12f ? 2.6f
            : motor.diameter() <= 16f ? 3.6f
            : motor.power() < 0.9f ? 5.5f
            : motor.power() <= 1.2f ? 7.2f : 8.5f;
        float minDiameter = motor.diameter() >= 20f ? 4.5f : 0f;
        return prop.diameter() >= minDiameter && prop.diameter() <= maxDiameter;
    }

    static boolean isMotorBatteryCompatible(MotorSpec motor, BatterySpec battery) {
        int cells = battery.cells();
        if (motor.power() >= 1.5f) return cells >= 4 && cells <= 6;
        if (motor.diameter() <= 12f) return cells == 1;
        if (motor.diameter() <= 16f) return cells >= 3 && cells <= 4;
        return cells >= 4 && cells <= 6 && (motor.kv() <= 2000f || cells <= 4);
    }

    private static boolean hasThrustImbalance(float[] factors, int motorCount) {
        if (motorCount == 0) return false;
        float average = 0;
        for (int i = 0; i < motorCount; i++) average += factors[i];
        average /= motorCount;
        if (average <= 0) return false;
        for (int i = 0; i < motorCount; i++) {
            if (Math.abs(factors[i] / average - 1f) > IMBALANCE_THRESHOLD) return true;
        }
        return false;
    }

    private static void addIf(List<IssueCode> issues, boolean condition, IssueCode code) {
        if (condition) issues.add(code);
    }

    private DroneAssemblyValidator() {}
}
