package org.restor.fpv.data;

import net.minecraft.nbt.CompoundTag;

public class Battery {
    private static final float PEAK_TO_HOVER = 4.5f;

    private float capacity;        // mAh
    private float currentCapacity; // mAh remaining
    private float voltage;         // Volts
    private float currentDraw;     // Amps
    private float dischargeRate;   // simulated
    private float enduranceMinutes = 8.0f;  // hover time on a full pack
    private float hoverThrottle = 0.35f;    // stick that holds altitude on this airframe

    public Battery(float capacity, float voltage) {
        this.capacity = capacity;
        this.currentCapacity = capacity;
        this.voltage = voltage;
        this.currentDraw = 0;
    }

    /**
     * Airframe endurance: how many minutes of hover a full pack is worth, and the stick
     * position that holds altitude on it. Draw is expressed relative to that hover point,
     * so a bigger pack on the same airframe genuinely flies longer.
     */
    public void setEnduranceProfile(float minutes, float hoverStick) {
        this.enduranceMinutes = Math.max(0.5f, minutes);
        this.hoverThrottle = Math.max(0.08f, Math.min(0.95f, hoverStick));
    }

    public float getEnduranceMinutes() { return enduranceMinutes; }

    public void tick(float throttle, float loadMultiplier) {
        float capacityAh = Math.max(0.001f, capacity / 1000.0f);
        float load = Math.max(0.0f, Math.min(1.0f, throttle));
        float loadScale = Math.max(0.1f, Math.min(2.0f, loadMultiplier));
        // Draw is a multiple of hover draw, bent so full stick costs at most PEAK_TO_HOVER times
        // hover however overpowered the airframe; the constant covers FC, VTX and camera.
        float peak = Math.min(PEAK_TO_HOVER, 1.0f / (hoverThrottle * hoverThrottle));
        float exponent = (float) (Math.log(Math.max(1.01f, peak)) / -Math.log(hoverThrottle));
        float relative = 0.12f + 0.88f * (float) Math.pow(load / hoverThrottle, exponent);
        currentDraw = capacityAh * (60.0f / enduranceMinutes) * relative * loadScale;
        currentCapacity -= currentDraw * 1000.0f / 3600.0f / 20.0f;
        if (currentCapacity < 0) currentCapacity = 0;
        if (capacity > 0) voltage = 4.2f * (currentCapacity / capacity);
    }

    public void charge(float fraction) {
        currentCapacity = Math.max(0, Math.min(capacity, currentCapacity + capacity * fraction));
        if (capacity > 0) voltage = 4.2f * (currentCapacity / capacity);
    }

    public boolean isEmpty() {
        return currentCapacity <= 0;
    }

    public float getPercentRemaining() {
        return capacity > 0 ? currentCapacity / capacity : 0;
    }

    public float getVoltage() {
        return voltage;
    }

    public float getCurrentDraw() {
        return currentDraw;
    }

    public float getCapacity() {
        return capacity;
    }

    public void save(CompoundTag tag) {
        tag.putFloat("capacity", capacity);
        tag.putFloat("currentCapacity", currentCapacity);
        tag.putFloat("voltage", voltage);
        tag.putFloat("currentDraw", currentDraw);
        tag.putFloat("dischargeRate", dischargeRate);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("capacity") && tag.getFloatOr("capacity", 0f) > 0) {
            capacity = tag.getFloatOr("capacity", 0f);
        }
        if (tag.contains("currentCapacity")) {
            currentCapacity = Math.max(0, Math.min(capacity, tag.getFloatOr("currentCapacity", 0f)));
        }
        if (tag.contains("voltage")) voltage = tag.getFloatOr("voltage", 0f);
        if (tag.contains("currentDraw")) currentDraw = tag.getFloatOr("currentDraw", 0f);
        if (tag.contains("dischargeRate")) dischargeRate = tag.getFloatOr("dischargeRate", 0f);
    }
}
