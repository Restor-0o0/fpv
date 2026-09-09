package org.restor.fpv.data;

import net.minecraft.nbt.CompoundTag;

public class Battery {
    private float capacity;        // mAh
    private float currentCapacity; // mAh remaining
    private float voltage;         // Volts
    private float currentDraw;     // Amps
    private float dischargeRate;   // simulated

    public Battery(float capacity, float voltage) {
        this.capacity = capacity;
        this.currentCapacity = capacity;
        this.voltage = voltage;
        this.currentDraw = 0;
    }

    public void tick(float throttle, float loadMultiplier) {
        float capacityAh = Math.max(0.001f, capacity / 1000.0f);
        float load = Math.max(0.0f, Math.min(1.0f, throttle));
        float loadScale = Math.max(0.1f, Math.min(2.0f, loadMultiplier));
        // FPV LiPos are commonly used around 80-120C. Quadratic motor load gives realistic
        // 3-6 minute flights around hover without treating mechanical thrust (N) as amps.
        currentDraw = capacityAh * (0.5f + 100.0f * load * load * loadScale);
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
        if (tag.contains("capacity") && tag.getFloat("capacity") > 0) {
            capacity = tag.getFloat("capacity");
        }
        if (tag.contains("currentCapacity")) {
            currentCapacity = Math.max(0, Math.min(capacity, tag.getFloat("currentCapacity")));
        }
        if (tag.contains("voltage")) voltage = tag.getFloat("voltage");
        if (tag.contains("currentDraw")) currentDraw = tag.getFloat("currentDraw");
        if (tag.contains("dischargeRate")) dischargeRate = tag.getFloat("dischargeRate");
    }
}
