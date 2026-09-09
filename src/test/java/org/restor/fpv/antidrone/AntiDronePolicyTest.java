package org.restor.fpv.antidrone;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiDronePolicyTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void ignoresOwnerTeamAndPrivilegedTargets() {
        AntiDronePolicy.Owner source = new AntiDronePolicy.Owner(OWNER, "blue", false);

        assertFalse(AntiDronePolicy.canTarget(source, new AntiDronePolicy.Owner(OWNER, "", false)));
        assertFalse(AntiDronePolicy.canTarget(source, new AntiDronePolicy.Owner(OTHER, "blue", false)));
        assertFalse(AntiDronePolicy.canTarget(source, new AntiDronePolicy.Owner(OTHER, "red", true)));
        assertTrue(AntiDronePolicy.canTarget(source, new AntiDronePolicy.Owner(OTHER, "red", false)));
    }

    @Test
    void falloffIsLinearAndBounded() {
        assertEquals(1.0f, AntiDronePolicy.falloff(0.0, 20.0, 1.0f), 0.0001f);
        assertEquals(0.5f, AntiDronePolicy.falloff(10.0, 20.0, 1.0f), 0.0001f);
        assertEquals(0.0f, AntiDronePolicy.falloff(20.0, 20.0, 1.0f), 0.0001f);
        assertEquals(0.0f, AntiDronePolicy.falloff(1.0, 0.0, 1.0f), 0.0001f);
    }

    @Test
    void interferenceReducesRssiWithoutLeavingSignalBounds() {
        assertEquals(0.35f, AntiDronePolicy.applyToRssi(0.8f, 0.45f), 0.0001f);
        assertEquals(0.0f, AntiDronePolicy.applyToRssi(0.2f, 0.8f), 0.0001f);
        assertEquals(0.8f, AntiDronePolicy.applyToRssi(0.8f, -1.0f), 0.0001f);
    }
}
