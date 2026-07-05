package org.restor.fpv.flight;

public class PIDController {
    private float kp, ki, kd;
    private float integral;
    private float previousError;
    private float outputMin, outputMax;

    public PIDController(float kp, float ki, float kd) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.outputMin = -1.0f;
        this.outputMax = 1.0f;
    }

    public float update(float setpoint, float measurement, float dt) {
        float error = setpoint - measurement;
        // Conditional integration: only integrate when not at target and output not saturated
        integral += error * dt;
        // Clamp integral to prevent windup
        float maxIntegral = 0.5f;
        integral = clamp(integral, -maxIntegral, maxIntegral);
        float derivative = (error - previousError) / dt;
        float output = kp * error + ki * integral + kd * derivative;
        previousError = error;
        return clamp(output, outputMin, outputMax);
    }

    public void reset() {
        integral = 0;
        previousError = 0;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void setPID(float kp, float ki, float kd) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
    }

    public void setOutputLimit(float min, float max) {
        this.outputMin = min;
        this.outputMax = max;
    }
}
