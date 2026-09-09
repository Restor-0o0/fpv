package org.restor.fpv.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryTest {
    @Test
    void hoverDoesNotDrainPackInTwentySeconds() {
        Battery battery = new Battery(1300, 4.2f);

        for (int tick = 0; tick < 20 * 20; tick++) {
            battery.tick(0.30f, 1.0f);
        }

        assertTrue(battery.getPercentRemaining() > 0.9f);
    }

    @Test
    void hoverProducesMultiMinuteFlightTime() {
        Battery battery = new Battery(1300, 4.2f);

        for (int tick = 0; tick < 20 * 60 * 4; tick++) {
            battery.tick(0.30f, 1.0f);
        }

        assertTrue(battery.getPercentRemaining() > 0.25f);
        assertTrue(battery.getPercentRemaining() < 0.5f);
    }
}
