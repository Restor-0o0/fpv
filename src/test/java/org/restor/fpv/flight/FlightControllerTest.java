package org.restor.fpv.flight;

import org.junit.jupiter.api.Test;
import org.restor.fpv.data.DroneData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightControllerTest {
    private static final float DT = 0.05f;

    private static FlightController armed(FlightMode mode) {
        FlightController fc = new FlightController(DroneData.RACING);
        fc.setMode(mode);
        fc.armed = true;
        return fc;
    }

    private static void fly(FlightController fc, float roll, float pitch, float yaw,
                            float currentRoll, float currentPitch) {
        fc.targetRoll = roll;
        fc.targetPitch = pitch;
        fc.targetYaw = yaw;
        fc.update(DT, 0f, 0f, 0f, currentRoll, currentPitch);
    }

    @Test
    void acroIgnoresAttitudeAndHoldsRateWithCentredSticks() {
        FlightController fc = armed(FlightMode.ACRO);
        fly(fc, 0f, 0f, 0f, 70f, 0f);
        assertEquals(0f, fc.rollOutput, 1.0e-4f);

        fly(fc, 1f, 0f, 0f, 0f, 0f);
        assertEquals(1f, fc.rollOutput, 1.0e-4f);
    }

    @Test
    void angleLevelsTheDroneWhenTheSticksAreReleased() {
        FlightController fc = armed(FlightMode.ANGLE);
        fly(fc, 0f, 0f, 0f, 40f, 0f);
        assertTrue(fc.rollOutput < -0.1f, "banked right with centred sticks must roll back left");

        fly(fc, 0f, 0f, 0f, 0f, -30f);
        assertTrue(fc.pitchOutput > 0.1f, "nose up with centred sticks must pitch back down");
    }

    @Test
    void angleStopsCommandingOnceTheDroneSitsAtTheCommandedBank() {
        FlightController fc = armed(FlightMode.ANGLE);
        fly(fc, 1f, 0f, 0f, FlightMode.ANGLE.tiltLimit(), 0f);
        assertEquals(0f, fc.rollOutput, 1.0e-4f);
    }

    @Test
    void horizonLevelsNearCentreAndHandsOverFullRateAtFullStick() {
        FlightController fc = armed(FlightMode.HORIZON);
        fly(fc, 0f, 0f, 0f, 30f, 0f);
        assertTrue(fc.rollOutput < -0.1f, "small deflection still self-levels");

        fly(fc, 1f, 0f, 0f, 30f, 0f);
        assertEquals(1f, fc.rollOutput, 1.0e-4f);
    }

    @Test
    void turtlePassesTheSticksStraightThroughAndLocksYaw() {
        FlightController fc = armed(FlightMode.TURTLE);
        fly(fc, 0.5f, -0.5f, 1f, 180f, 0f);
        assertEquals(0.5f, fc.rollOutput, 1.0e-4f);
        assertEquals(-0.5f, fc.pitchOutput, 1.0e-4f);
        assertEquals(0f, fc.yawOutput, 1.0e-4f);
    }

    @Test
    void disarmedControllerCommandsNothing() {
        FlightController fc = armed(FlightMode.ACRO);
        fc.armed = false;
        fc.throttle = 0.8f;
        fly(fc, 1f, 1f, 1f, 0f, 0f);
        assertEquals(0f, fc.rollOutput, 1.0e-4f);
        assertEquals(0f, fc.throttle, 1.0e-4f);
    }

    @Test
    void onlyAltHoldTakesOverTheThrottleStick() {
        assertTrue(FlightMode.ALT_HOLD.holdsAltitude());
        assertFalse(FlightMode.ANGLE.holdsAltitude());
        assertTrue(FlightMode.ALT_HOLD.selfLevelling());
        assertFalse(FlightMode.HORIZON.selfLevelling());
        assertEquals(FlightMode.ACRO, FlightMode.byOrdinal(-1));
        assertEquals(FlightMode.ACRO, FlightMode.byOrdinal(99));
    }
}
