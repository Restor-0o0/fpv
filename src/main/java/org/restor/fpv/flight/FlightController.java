package org.restor.fpv.flight;

import org.restor.fpv.data.DroneData;

public class FlightController {
    // Single-mode build: ACRO only (per user request). ANGLE/HORIZON code is kept
    // below for a future mode switch but cycleMode() is a no-op.
    private FlightMode mode = FlightMode.ACRO;
    private final PIDController pidRoll;
    private final PIDController pidPitch;
    private final PIDController pidYaw;
    private final PIDController anglePidRoll;
    private final PIDController anglePidPitch;

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
    private float currentYaw;

    public FlightController(DroneData data) {
        this.data = data;
        this.pidRoll = new PIDController(0.3f, 0.01f, 0.005f);
        this.pidPitch = new PIDController(0.3f, 0.01f, 0.005f);
        this.pidYaw = new PIDController(0.5f, 0.01f, 0.000f);
        this.anglePidRoll = new PIDController(2.0f, 0.0f, 0.0f);
        this.anglePidPitch = new PIDController(2.0f, 0.0f, 0.0f);
        this.anglePidRoll.setOutputLimit(-data.maxRollRate, data.maxRollRate);
        this.anglePidPitch.setOutputLimit(-data.maxPitchRate, data.maxPitchRate);
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

        float rcRoll = targetRoll;
        float rcPitch = targetPitch;
        float rcYaw = targetYaw;

        switch (mode) {
            case ACRO -> computeAcro(rcRoll, rcPitch, rcYaw, dt);
            case HORIZON -> computeHorizon(rcRoll, rcPitch, rcYaw, dt);
            case ANGLE -> computeAngle(rcRoll, rcPitch, rcYaw, dt);
        }
    }

    // expo*x³ + (1-expo)*x — gentle center, full rate at max deflection.
    private static float rcExpo(float x, float expo) {
        return expo * x * x * x + (1.0f - expo) * x;
    }

    private void computeAcro(float rcRoll, float rcPitch, float rcYaw, float dt) {
        rollOutput  = rcExpo(rcRoll,  0.45f);
        pitchOutput = rcExpo(rcPitch, 0.45f);
        yawOutput   = rcExpo(rcYaw,   0.55f);
    }

    private void computeAngle(float rcRoll, float rcPitch, float rcYaw, float dt) {
        float targetAngleRoll = rcRoll * 45.0f;
        float targetAnglePitch = rcPitch * 45.0f;

        rollOutput = levelOutput(targetAngleRoll, currentRoll, currentRollRate, data.maxRollRate);
        pitchOutput = levelOutput(targetAnglePitch, currentPitch, currentPitchRate, data.maxPitchRate);
        yawOutput = rcYaw * 0.6f;
    }

    private void computeHorizon(float rcRoll, float rcPitch, float rcYaw, float dt) {
        float stickDeflection = Math.max(Math.abs(rcRoll), Math.abs(rcPitch));
        acroFactor = clamp((stickDeflection - 0.35f) / 0.65f, 0f, 1f);

        float targetAngleRoll = rcRoll * 45.0f;
        float targetAnglePitch = rcPitch * 45.0f;

        float angleRoll = levelOutput(targetAngleRoll, currentRoll, currentRollRate, data.maxRollRate);
        float anglePitch = levelOutput(targetAnglePitch, currentPitch, currentPitchRate, data.maxPitchRate);

        rollOutput = lerp(angleRoll, rcRoll, acroFactor);
        pitchOutput = lerp(anglePitch, rcPitch, acroFactor);
        yawOutput = rcYaw * 0.75f;
    }

    private float levelOutput(float targetAngle, float currentAngle, float currentRate, float maxRate) {
        float error = targetAngle - currentAngle;
        float desiredRate = error * 4.5f - currentRate * 0.08f;
        return clamp(desiredRate / maxRate, -1f, 1f);
    }

    private float computeYaw(float rcYaw, float dt) {
        return computeYaw(rcYaw, dt, 1.0f);
    }

    private float computeYaw(float rcYaw, float dt, float rateMultiplier) {
        if (Math.abs(rcYaw) < 0.05f) {
            pidYaw.reset();
            return 0;
        }
        float targetYawRate = rcYaw * data.maxYawRate * rateMultiplier;
        return pidYaw.update(targetYawRate, currentYawRate, dt);
    }

    private float acroFactor = 0;

    public void cycleMode() {
        // Single-mode build: locked to ACRO.
    }

    public FlightMode getMode() {
        return mode;
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
            pidRoll.reset();
            pidPitch.reset();
            pidYaw.reset();
            anglePidRoll.reset();
            anglePidPitch.reset();
        }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
}
