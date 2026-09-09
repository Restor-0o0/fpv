package org.restor.fpv.flight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadioFailsafeTest {
    private static final RadioFailsafe.Policy POLICY = new RadioFailsafe.Policy(2, 2, 3, 3, 0.05f);

    @Test
    void armsOnlyWithLowThrottleAndDisarmsWhenSwitchTurnsOff() {
        RadioFailsafe radio = new RadioFailsafe();
        radio.acceptPacket(0.2f, true, POLICY);
        assertEquals(RadioFailsafe.State.DISARMED, radio.state());
        radio.acceptPacket(0.05f, true, POLICY);
        assertEquals(RadioFailsafe.State.ARMED, radio.state());
        radio.acceptPacket(0.7f, false, POLICY);
        assertEquals(RadioFailsafe.State.DISARMED, radio.state());
    }

    @Test
    void holdsThenNeutralizesThenDeterministicallyDisarms() {
        RadioFailsafe radio = armedRadio();
        tickWithoutPackets(radio, 3);
        assertEquals(RadioFailsafe.State.FAILSAFE_HOLD, radio.state());
        assertTrue(radio.state().motorsArmed());
        tickWithoutPackets(radio, 2);
        assertEquals(RadioFailsafe.State.FAILSAFE_NEUTRAL, radio.state());
        assertTrue(radio.state().motorsArmed());
        tickWithoutPackets(radio, 3);
        assertEquals(RadioFailsafe.State.FAILSAFE_DISARMED, radio.state());
        assertFalse(radio.state().motorsArmed());
    }

    @Test
    void recoveryRequiresStablePacketsLowThrottleAndSwitchCycle() {
        RadioFailsafe radio = armedRadio();
        tickWithoutPackets(radio, 8);
        assertEquals(RadioFailsafe.State.FAILSAFE_DISARMED, radio.state());
        acceptAndTick(radio, 0.0f, true);
        acceptAndTick(radio, 0.0f, true);
        acceptAndTick(radio, 0.0f, true);
        assertEquals(RadioFailsafe.State.FAILSAFE_DISARMED, radio.state());
        acceptAndTick(radio, 0.0f, false);
        acceptAndTick(radio, 0.0f, false);
        acceptAndTick(radio, 0.0f, false);
        assertEquals(RadioFailsafe.State.DISARMED, radio.state());
        radio.acceptPacket(0.0f, true, POLICY);
        assertEquals(RadioFailsafe.State.ARMED, radio.state());
    }

    @Test
    void interruptedRecoveryPacketSequenceRestartsStabilityCount() {
        RadioFailsafe radio = armedRadio();
        tickWithoutPackets(radio, 8);
        acceptAndTick(radio, 0.0f, false);
        acceptAndTick(radio, 0.0f, false);
        radio.tick(POLICY);
        acceptAndTick(radio, 0.0f, false);
        assertEquals(RadioFailsafe.State.FAILSAFE_DISARMED, radio.state());
    }

    private static RadioFailsafe armedRadio() {
        RadioFailsafe radio = new RadioFailsafe();
        radio.acceptPacket(0.0f, true, POLICY);
        radio.tick(POLICY);
        return radio;
    }

    private static void tickWithoutPackets(RadioFailsafe radio, int ticks) {
        for (int i = 0; i < ticks; i++) radio.tick(POLICY);
    }

    private static void acceptAndTick(RadioFailsafe radio, float throttle, boolean arm) {
        radio.acceptPacket(throttle, arm, POLICY);
        radio.tick(POLICY);
    }
}
