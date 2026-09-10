package org.restor.fpv.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.DAMAGED_CAMERA;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.EXCESSIVE_HOVER_THROTTLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.FRAME_COMPONENT_MISMATCH;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MISSING_PARTS;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MOTOR_BATTERY_INCOMPATIBLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MOTOR_PROP_INCOMPATIBLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_BATTERY;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_LIFT;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.THRUST_IMBALANCE;

class DroneAssemblyValidatorTest {
    private static final DroneAssemblyValidator.MotorSpec MOTOR =
        new DroneAssemblyValidator.MotorSpec(1700, 0.8f, 28);
    private static final DroneAssemblyValidator.PropSpec PROP = new DroneAssemblyValidator.PropSpec(5);

    @Test
    void missingPropIsOnlyAWarning() {
        var result = validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, null}, new DroneAssemblyValidator.BatterySpec(4), 30);
        assertTrue(result.warnings().contains(MISSING_PARTS));
        assertTrue(result.isComplete());
    }

    @Test
    void everySlotEmptyBlocksAssembly() {
        var result = validate(new DroneAssemblyValidator.MotorSpec[]{null, null},
            new DroneAssemblyValidator.PropSpec[]{null, null}, new DroneAssemblyValidator.BatterySpec(4), 30);
        assertEquals(NO_LIFT, result.errors().get(0));
        assertFalse(result.isComplete());
    }

    @Test
    void missingBatteryBlocksAssembly() {
        var result = validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP}, null, 30);
        assertTrue(result.errors().contains(NO_BATTERY));
    }

    @Test
    void highKvMotorOnSixCellsStillAssembles() {
        var highKv = new DroneAssemblyValidator.MotorSpec(2450, 1, 28);
        var result = validate(new DroneAssemblyValidator.MotorSpec[]{highKv, highKv},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP}, new DroneAssemblyValidator.BatterySpec(6), 30);
        assertTrue(result.warnings().contains(MOTOR_BATTERY_INCOMPATIBLE));
        assertTrue(result.isComplete());
    }

    @Test
    void reportsFrameAndMotorPropMismatchAsWarnings() {
        var oversizedProp = new DroneAssemblyValidator.PropSpec(8);
        var result = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, true, true, false,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{oversizedProp, oversizedProp},
            new DroneAssemblyValidator.BatterySpec(4), 1, 30, new float[]{1, 1}, 1, false, true, true));
        assertTrue(result.warnings().contains(FRAME_COMPONENT_MISMATCH));
        assertTrue(result.warnings().contains(MOTOR_PROP_INCOMPATIBLE));
        assertTrue(result.isComplete());
    }

    @Test
    void reportsHoverThrottleAndImbalance() {
        var hoverResult = validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP}, new DroneAssemblyValidator.BatterySpec(4), 11);
        var imbalanceResult = validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP}, new DroneAssemblyValidator.BatterySpec(4), 30,
            new float[]{1, 1.2f});
        assertTrue(hoverResult.warnings().contains(EXCESSIVE_HOVER_THROTTLE));
        assertTrue(imbalanceResult.warnings().contains(THRUST_IMBALANCE));
    }

    @Test
    void warnsAboutDamagedCameraSignal() {
        var result = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, true, true, true,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP},
            new DroneAssemblyValidator.BatterySpec(4), 1, 30, new float[]{1, 1}, 0.5f, false, true, true));
        assertTrue(result.warnings().contains(DAMAGED_CAMERA));
    }

    private static DroneAssemblyValidator.ValidationResult validate(
        DroneAssemblyValidator.MotorSpec[] motors,
        DroneAssemblyValidator.PropSpec[] props,
        DroneAssemblyValidator.BatterySpec battery,
        float thrust
    ) {
        return validate(motors, props, battery, thrust, new float[]{1, 1});
    }

    private static DroneAssemblyValidator.ValidationResult validate(
        DroneAssemblyValidator.MotorSpec[] motors,
        DroneAssemblyValidator.PropSpec[] props,
        DroneAssemblyValidator.BatterySpec battery,
        float thrust,
        float[] factors
    ) {
        return DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            motors.length, true, true, true, motors, props, battery, 1, thrust, factors, 1, false, true, true));
    }
}
