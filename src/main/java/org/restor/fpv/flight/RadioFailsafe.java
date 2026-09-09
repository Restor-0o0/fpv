package org.restor.fpv.flight;

public final class RadioFailsafe {
    public enum State {
        DISARMED,
        ARMED,
        FAILSAFE_HOLD,
        FAILSAFE_NEUTRAL,
        FAILSAFE_DISARMED;

        public boolean isFailsafe() {
            return this == FAILSAFE_HOLD || this == FAILSAFE_NEUTRAL || this == FAILSAFE_DISARMED;
        }

        public boolean motorsArmed() {
            return this == ARMED || this == FAILSAFE_HOLD || this == FAILSAFE_NEUTRAL;
        }
    }

    public record Policy(int packetTimeoutTicks, int holdTicks, int neutralTicks,
                         int recoveryPackets, float armThrottleMaximum) {
        public Policy {
            if (packetTimeoutTicks < 0 || holdTicks < 0 || neutralTicks < 0 || recoveryPackets < 1) {
                throw new IllegalArgumentException("Invalid radio failsafe timing");
            }
            if (!Float.isFinite(armThrottleMaximum)
                || armThrottleMaximum < 0.0f || armThrottleMaximum > 1.0f) {
                throw new IllegalArgumentException("Invalid arm throttle maximum");
            }
        }
    }

    private State state = State.DISARMED;
    private int packetAge = Integer.MAX_VALUE;
    private int stageTicks;
    private int recoveryPackets;
    private boolean packetThisTick;
    private boolean recoverySwitchReleased;

    public void acceptPacket(float throttle, boolean armSwitch, Policy policy) {
        packetAge = 0;
        packetThisTick = true;

        if (state.isFailsafe()) {
            boolean lowThrottle = throttle <= policy.armThrottleMaximum();
            recoveryPackets = lowThrottle ? recoveryPackets + 1 : 0;
            if (!armSwitch && lowThrottle) {
                recoverySwitchReleased = true;
                state = State.FAILSAFE_DISARMED;
            }
            if (state == State.FAILSAFE_DISARMED
                && recoveryPackets >= policy.recoveryPackets()
                && recoverySwitchReleased && !armSwitch && lowThrottle) {
                state = State.DISARMED;
            }
            return;
        }

        if (!armSwitch) {
            state = State.DISARMED;
        } else if (state == State.DISARMED && throttle <= policy.armThrottleMaximum()) {
            state = State.ARMED;
        }
    }

    public void tick(Policy policy) {
        if (packetThisTick) {
            packetThisTick = false;
        } else {
            if (packetAge < Integer.MAX_VALUE) packetAge++;
            if (state.isFailsafe()) recoveryPackets = 0;
        }

        if (state == State.ARMED && packetAge > policy.packetTimeoutTicks()) {
            enterFailsafe(State.FAILSAFE_HOLD);
            advanceZeroLengthStages(policy);
            return;
        }

        if (state == State.FAILSAFE_HOLD && ++stageTicks >= policy.holdTicks()) {
            state = State.FAILSAFE_NEUTRAL;
            stageTicks = 0;
            advanceZeroLengthStages(policy);
        } else if (state == State.FAILSAFE_NEUTRAL && ++stageTicks >= policy.neutralTicks()) {
            state = State.FAILSAFE_DISARMED;
            stageTicks = 0;
        }
    }

    private void enterFailsafe(State initialState) {
        state = initialState;
        stageTicks = 0;
        recoveryPackets = 0;
        recoverySwitchReleased = false;
    }

    private void advanceZeroLengthStages(Policy policy) {
        if (state == State.FAILSAFE_HOLD && policy.holdTicks() == 0) state = State.FAILSAFE_NEUTRAL;
        if (state == State.FAILSAFE_NEUTRAL && policy.neutralTicks() == 0) state = State.FAILSAFE_DISARMED;
    }

    public void forceDisarm() {
        state = State.DISARMED;
        packetAge = Integer.MAX_VALUE;
        stageTicks = 0;
        recoveryPackets = 0;
        packetThisTick = false;
        recoverySwitchReleased = false;
    }

    public State state() {
        return state;
    }

    public int packetAge() {
        return packetAge;
    }
}
