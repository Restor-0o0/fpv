package org.restor.fpv.flight;

import org.restor.fpv.data.DroneData;

public class FlightController {
    private FlightMode mode = FlightMode.ACRO;

    private final DroneData data;
    public boolean armed = false;
    public float throttle;

    public float targetRoll;
    public float targetPitch;
    public float targetYaw;

    public float rollOutput;
    public float pitchOutput;
    public float yawOutput;

    private float currentRollRate;
    private float currentPitchRate;
    private float currentYawRate;

    private float currentRoll;
    private float currentPitch;

    public FlightController(DroneData data) {
        this.data = data;
    }

    public void update(float dt, float currentRollRate, float currentPitchRate, float currentYawRate,
                       float currentRoll, float currentPitch) {
        this.currentRollRate = currentRollRate;
        this.currentPitchRate = currentPitchRate;
        this.currentYawRate = currentYawRate;
        this.currentRoll = currentRoll;
        this.currentPitch = currentPitch;

        if (!armed || dt <= 0) {
            throttle = 0;
            rollOutput = 0;
            pitchOutput = 0;
            yawOutput = 0;
            return;
        }

        switch (mode) {
            case ACRO -> computeAcro(targetRoll, targetPitch, targetYaw);
            case HORIZON -> computeHorizon(targetRoll, targetPitch, targetYaw);
            case ANGLE, ALT_HOLD -> computeAngle(targetRoll, targetPitch, targetYaw);
            case TURTLE -> computeTurtle(targetRoll, targetPitch);
        }
    }

    // expo*x³ + (1-expo)*x — gentle center, full rate at max deflection.
    private static float rcExpo(float x, float expo) {
        return expo * x * x * x + (1.0f - expo) * x;
    }

    private void computeAcro(float rcRoll, float rcPitch, float rcYaw) {
        rollOutput  = rcExpo(rcRoll,  0.30f);
        pitchOutput = rcExpo(rcPitch, 0.30f);
        yawOutput   = rcExpo(rcYaw,   0.40f);
    }

    private void computeAngle(float rcRoll, float rcPitch, float rcYaw) {
        float limit = mode.tiltLimit();
        rollOutput = levelOutput(rcRoll * limit, currentRoll, currentRollRate, data.maxRollRate);
        pitchOutput = levelOutput(rcPitch * limit, currentPitch, currentPitchRate, data.maxPitchRate);
        yawOutput = rcExpo(rcYaw, 0.40f) * (mode == FlightMode.ALT_HOLD ? 0.5f : 0.65f);
    }

    private void computeHorizon(float rcRoll, float rcPitch, float rcYaw) {
        float stickDeflection = Math.max(Math.abs(rcRoll), Math.abs(rcPitch));
        float acroFactor = clamp((stickDeflection - 0.35f) / 0.65f, 0f, 1f);

        float limit = mode.tiltLimit();
        float angleRoll = levelOutput(rcRoll * limit, currentRoll, currentRollRate, data.maxRollRate);
        float anglePitch = levelOutput(rcPitch * limit, currentPitch, currentPitchRate, data.maxPitchRate);

        rollOutput = lerp(angleRoll, rcExpo(rcRoll, 0.30f), acroFactor);
        pitchOutput = lerp(anglePitch, rcExpo(rcPitch, 0.30f), acroFactor);
        yawOutput = rcExpo(rcYaw, 0.40f) * 0.85f;
    }

    // Righting an upended drone: raw stick, no expo and no levelling to fight.
    private void computeTurtle(float rcRoll, float rcPitch) {
        rollOutput = clamp(rcRoll, -1f, 1f);
        pitchOutput = clamp(rcPitch, -1f, 1f);
        yawOutput = 0;
    }

    private float levelOutput(float targetAngle, float currentAngle, float currentRate, float maxRate) {
        float error = targetAngle - currentAngle;
        float desiredRate = error * 4.5f - currentRate * 0.08f;
        return clamp(desiredRate / maxRate, -1f, 1f);
    }

    public FlightMode getMode() {
        return mode;
    }

    public void setMode(FlightMode mode) {
        this.mode = mode;
    }

    public boolean isArmed() {
        return armed;
    }

    public void toggleArm() {
        armed = !armed;
        if (!armed) {
            throttle = 0;
            rollOutput = 0;
            pitchOutput = 0;
            yawOutput = 0;
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
}
