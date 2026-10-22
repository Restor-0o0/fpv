package org.restor.fpv.assembly;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.DAMAGED_CAMERA;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.EXCESSIVE_HOVER_THROTTLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.FRAME_COMPONENT_MISMATCH;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.LOW_TWR;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MISSING_PARTS;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MOTOR_BATTERY_INCOMPATIBLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MOTOR_PROP_INCOMPATIBLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_BATTERY;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_CAMERA;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_FC;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_FRAME;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_LIFT;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_VTX;
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

    @Test
    void aBuildWithoutTheBasicsIsRejectedOnEveryCount() {
        var result = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, false, false, true,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP},
            null, 1, 30, new float[]{1, 1}, 1, false, false, false));
        assertTrue(result.errors().containsAll(List.of(NO_FRAME, NO_BATTERY, NO_FC, NO_VTX)));
        assertTrue(result.warnings().contains(NO_CAMERA));
        assertFalse(result.isComplete());
    }

    @Test
    void aFramelessBuildIsNotAlsoBlamedForNotLifting() {
        var result = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, false, true, true,
            new DroneAssemblyValidator.MotorSpec[]{null, null},
            new DroneAssemblyValidator.PropSpec[]{null, null},
            new DroneAssemblyValidator.BatterySpec(4), 1, 1, new float[]{0, 0}, 1, false, true, true));
        assertTrue(result.errors().contains(NO_FRAME));
        assertFalse(result.errors().contains(NO_LIFT));
    }

    @Test
    void flightQualityWarningsWaitUntilTheBuildCanFlyAtAll() {
        var result = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, true, true, true,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP},
            null, 1, 11, new float[]{1, 1.5f}, 0.2f, false, true, true));
        assertTrue(result.errors().contains(NO_BATTERY));
        assertFalse(result.warnings().contains(LOW_TWR));
        assertFalse(result.warnings().contains(EXCESSIVE_HOVER_THROTTLE));
        assertFalse(result.warnings().contains(THRUST_IMBALANCE));
        assertFalse(result.warnings().contains(DAMAGED_CAMERA));
    }

    @Test
    void metricsAreThrustOverWeightAndTheStickThatHoldsIt() {
        var result = validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP},
            new DroneAssemblyValidator.BatterySpec(4), 39.24f);
        assertEquals(1f, result.metrics().massKg(), 1e-6f);
        assertEquals(39.24f, result.metrics().thrustN(), 1e-4f);
        assertEquals(4f, result.metrics().twr(), 1e-3f);
        assertEquals(0.5f, result.metrics().hoverThrottle(), 1e-3f);
    }

    @Test
    void aWingFliesOnFarLessThrustThanAMultirotor() {
        var rotor = DroneAssemblyValidator.validate(input(3f, false));
        var wing = DroneAssemblyValidator.validate(input(3f, true));
        assertTrue(rotor.errors().contains(NO_LIFT));
        assertTrue(wing.isComplete());
        assertTrue(wing.warnings().contains(LOW_TWR));

        assertTrue(DroneAssemblyValidator.validate(input(2f, true)).errors().contains(NO_LIFT),
            "even a wing has to beat stall");
        assertTrue(DroneAssemblyValidator.validate(input(5f, true)).warnings().isEmpty());
    }

    @Test
    void hoverThrottleAndImbalanceAreMultirotorProblemsOnly() {
        var wing = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, true, true, true,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP},
            new DroneAssemblyValidator.BatterySpec(4), 1, 5, new float[]{1, 1.5f}, 1, true, true, true));
        assertTrue(wing.isComplete());
        assertTrue(wing.warnings().isEmpty());
    }

    @Test
    void imbalanceIsOnlyFlaggedPastTheTolerance() {
        assertFalse(validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP}, new DroneAssemblyValidator.BatterySpec(4), 30,
            new float[]{1f, 1.07f}).warnings().contains(THRUST_IMBALANCE));
        assertTrue(validate(new DroneAssemblyValidator.MotorSpec[]{MOTOR, MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP}, new DroneAssemblyValidator.BatterySpec(4), 30,
            new float[]{1f, 1.25f}).warnings().contains(THRUST_IMBALANCE));
    }

    @Test
    void everyIssueHasItsOwnBitAndTheMaskCarriesThemAll() {
        var result = DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            2, true, false, true,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR, null},
            new DroneAssemblyValidator.PropSpec[]{PROP, PROP},
            null, 1, 30, new float[]{1, 1}, 1, false, true, true));
        int mask = result.mask();
        assertTrue(result.errors().contains(NO_BATTERY));
        assertTrue(result.warnings().containsAll(List.of(MISSING_PARTS, NO_CAMERA)));
        for (var code : result.errors()) assertTrue((mask & code.mask()) != 0, code.name());
        for (var code : result.warnings()) assertTrue((mask & code.mask()) != 0, code.name());
        assertEquals(0, mask & LOW_TWR.mask());

        int seen = 0;
        for (var code : DroneAssemblyValidator.IssueCode.values()) {
            assertEquals(0, seen & code.mask(), code + " shares a bit");
            seen |= code.mask();
            assertFalse(code.localizationKey().isBlank());
        }
    }

    @Test
    void blockingIssuesAreExactlyTheOnesThatGroundTheBuild() {
        assertTrue(NO_FRAME.isError() && NO_BATTERY.isError() && NO_FC.isError()
            && NO_VTX.isError() && NO_LIFT.isError());
        assertFalse(NO_CAMERA.isError() || LOW_TWR.isError() || MISSING_PARTS.isError()
            || THRUST_IMBALANCE.isError() || MOTOR_PROP_INCOMPATIBLE.isError()
            || MOTOR_BATTERY_INCOMPATIBLE.isError() || FRAME_COMPONENT_MISMATCH.isError()
            || EXCESSIVE_HOVER_THROTTLE.isError() || DAMAGED_CAMERA.isError());
    }

    @Test
    void propSizeIsBoundedByTheMotorItSpinsOn() {
        var whoopMotor = new DroneAssemblyValidator.MotorSpec(8000, 0.3f, 11);
        var cineMotor = new DroneAssemblyValidator.MotorSpec(4500, 0.5f, 14);
        var fiveInch = new DroneAssemblyValidator.MotorSpec(2450, 1.0f, 28);
        var heavy = new DroneAssemblyValidator.MotorSpec(900, 1.35f, 31);

        assertTrue(compatible(whoopMotor, 2.5f));
        assertFalse(compatible(whoopMotor, 3f));
        assertTrue(compatible(cineMotor, 3f));
        assertFalse(compatible(cineMotor, 5f));
        assertTrue(compatible(fiveInch, 5f));
        assertTrue(compatible(fiveInch, 7f));
        assertFalse(compatible(fiveInch, 4f), "a 28mm motor never takes a sub 4.5 inch prop");
        assertTrue(compatible(heavy, 8f));
        assertFalse(compatible(heavy, 9f));
    }

    @Test
    void aPlanePropIsMatchedToShaftPowerInstead() {
        var small = new DroneAssemblyValidator.MotorSpec(1100, 2.1f, 28);
        var big = new DroneAssemblyValidator.MotorSpec(650, 5.0f, 42);
        assertTrue(DroneAssemblyValidator.isMotorPropCompatible(small,
            new DroneAssemblyValidator.PropSpec(8), true));
        assertFalse(DroneAssemblyValidator.isMotorPropCompatible(small,
            new DroneAssemblyValidator.PropSpec(10), true));
        assertTrue(DroneAssemblyValidator.isMotorPropCompatible(big,
            new DroneAssemblyValidator.PropSpec(12), true));
        assertFalse(DroneAssemblyValidator.isMotorPropCompatible(big,
            new DroneAssemblyValidator.PropSpec(8), true));
    }

    @Test
    void cellCountIsBoundedByTheMotorItFeeds() {
        var whoopMotor = new DroneAssemblyValidator.MotorSpec(8000, 0.3f, 11);
        var cineMotor = new DroneAssemblyValidator.MotorSpec(4500, 0.5f, 14);
        var highKv = new DroneAssemblyValidator.MotorSpec(2450, 1.0f, 28);
        var lowKv = new DroneAssemblyValidator.MotorSpec(1300, 1.15f, 28);
        var planeMotor = new DroneAssemblyValidator.MotorSpec(1100, 2.1f, 28);

        assertTrue(cells(whoopMotor, 1));
        assertFalse(cells(whoopMotor, 4));
        assertTrue(cells(cineMotor, 4));
        assertFalse(cells(cineMotor, 6));
        assertTrue(cells(highKv, 4));
        assertFalse(cells(highKv, 6));
        assertTrue(cells(lowKv, 6));
        assertTrue(cells(planeMotor, 6));
        assertFalse(cells(planeMotor, 3));
    }

    private static boolean compatible(DroneAssemblyValidator.MotorSpec motor, float propDiameter) {
        return DroneAssemblyValidator.isMotorPropCompatible(motor,
            new DroneAssemblyValidator.PropSpec(propDiameter));
    }

    private static boolean cells(DroneAssemblyValidator.MotorSpec motor, int cellCount) {
        return DroneAssemblyValidator.isMotorBatteryCompatible(motor,
            new DroneAssemblyValidator.BatterySpec(cellCount));
    }

    private static DroneAssemblyValidator.Input input(float thrust, boolean wing) {
        return new DroneAssemblyValidator.Input(
            1, true, true, true,
            new DroneAssemblyValidator.MotorSpec[]{MOTOR},
            new DroneAssemblyValidator.PropSpec[]{PROP},
            new DroneAssemblyValidator.BatterySpec(4), 1, thrust, new float[]{1}, 1, wing, true, true);
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
