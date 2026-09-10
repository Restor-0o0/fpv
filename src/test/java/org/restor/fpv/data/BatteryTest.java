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
    void hoverAtRatedStickEmptiesPackAtRatedEndurance() {
        Battery battery = new Battery(1300, 4.2f);
        battery.setEnduranceProfile(15.0f, 0.35f);

        for (int tick = 0; tick < 20 * 60 * 14; tick++) {
            battery.tick(0.35f, 1.0f);
        }
        assertTrue(battery.getPercentRemaining() > 0.02f);

        for (int tick = 0; tick < 20 * 60 * 2; tick++) {
            battery.tick(0.35f, 1.0f);
        }
        assertTrue(battery.isEmpty());
    }

    @Test
    void whoopBurnsItsPackInAboutFiveMinutes() {
        Battery battery = new Battery(450, 4.2f);
        battery.setEnduranceProfile(5.0f, 0.40f);

        for (int tick = 0; tick < 20 * 60 * 4; tick++) {
            battery.tick(0.40f, 1.0f);
        }

        assertTrue(battery.getPercentRemaining() > 0.1f);
        assertTrue(battery.getPercentRemaining() < 0.3f);
    }

    @Test
    void planeLoitersForOverAnHour() {
        Battery battery = new Battery(2200, 4.2f);
        battery.setEnduranceProfile(120.0f, 0.35f);

        for (int tick = 0; tick < 20 * 60 * 60; tick++) {
            battery.tick(0.30f, 1.0f);
        }

        assertTrue(battery.getPercentRemaining() > 0.5f);
    }

    @Test
    void throttleAboveHoverDrainsFasterThanBelow() {
        Battery cruise = new Battery(1300, 4.2f);
        Battery punch = new Battery(1300, 4.2f);
        cruise.setEnduranceProfile(15.0f, 0.35f);
        punch.setEnduranceProfile(15.0f, 0.35f);

        for (int tick = 0; tick < 20 * 60; tick++) {
            cruise.tick(0.25f, 1.0f);
            punch.tick(0.75f, 1.0f);
        }

        assertTrue(punch.getPercentRemaining() < cruise.getPercentRemaining());
    }
}
