package org.restor.fpv.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RacePlaneCrossingTest {
    @Test
    void detectsFastForwardCrossing() {
        double hit = crossing(0.0, 0.0, -20.0, 0.0, 0.0, 20.0, 3.0);
        assertEquals(0.5, hit, 1.0e-9);
    }

    @Test
    void rejectsReverseCrossing() {
        double hit = crossing(0.0, 0.0, 2.0, 0.0, 0.0, -2.0, 3.0);
        assertTrue(Double.isNaN(hit));
    }

    @Test
    void rejectsCrossingOutsideAperture() {
        double hit = crossing(3.01, 0.0, -2.0, 3.01, 0.0, 2.0, 3.0);
        assertTrue(Double.isNaN(hit));
    }

    @Test
    void detectsCrossingOfTiltedPlane() {
        double hit = RacePlaneCrossing.crossingParameter(
            -2.0, 0.0, -2.0, 2.0, 0.0, 2.0,
            0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0);
        assertEquals(0.5, hit, 1.0e-9);
    }

    @Test
    void doesNotRetriggerFromPlane() {
        double hit = crossing(0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 3.0);
        assertTrue(Double.isNaN(hit));
    }

    private static double crossing(double fromX, double fromY, double fromZ,
                                   double toX, double toY, double toZ, double halfAperture) {
        return RacePlaneCrossing.crossingParameter(
            fromX, fromY, fromZ, toX, toY, toZ,
            0.0, 0.0, 0.0, 0.0, 0.0, 1.0, halfAperture);
    }
}
