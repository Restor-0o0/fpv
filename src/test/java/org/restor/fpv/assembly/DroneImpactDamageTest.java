package org.restor.fpv.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneImpactDamageTest {
    private static final float[] MOUNT_X = {1, -1, 1, -1};
    private static final float[] MOUNT_Z = {-1, -1, 1, 1};

    @Test
    void ignoresLowEnergyContact() {
        var result = DroneImpactDamage.allocate(0.5f, 1, 0, 0, MOUNT_X, MOUNT_Z);
        assertFalse(result.hasDamage());
    }

    @Test
    void lightImpactDamagesNearestPropDeterministically() {
        var result = DroneImpactDamage.allocate(0.5f, 0, 0, -3, MOUNT_X, MOUNT_Z);
        assertEquals(0, result.motorIndex());
        assertTrue(result.propDamage() > 0);
        assertEquals(0, result.motorDamage());
        assertEquals(0, result.frameDamage());
    }

    @Test
    void strongImpactDamagesFrameAndNearestMotor() {
        var result = DroneImpactDamage.allocate(1.0f, -5, 0, 0, MOUNT_X, MOUNT_Z);
        assertEquals(1, result.motorIndex());
        assertEquals(0, result.propDamage());
        assertTrue(result.motorDamage() > 0);
        assertTrue(result.frameDamage() > 0);
    }

    @Test
    void massChangesAllocationAtTheSameVelocityDelta() {
        var light = DroneImpactDamage.allocate(0.2f, 4, 0, 0, MOUNT_X, MOUNT_Z);
        var heavy = DroneImpactDamage.allocate(1.0f, 4, 0, 0, MOUNT_X, MOUNT_Z);
        assertTrue(light.propDamage() > 0);
        assertEquals(0, light.frameDamage());
        assertTrue(heavy.frameDamage() > 0);
    }
}
