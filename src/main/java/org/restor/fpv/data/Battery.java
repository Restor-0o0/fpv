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

    public void tick(float throttle, float motorPowerFactor) {
        currentDraw = throttle * (20.0f + motorPowerFactor * 8.0f);
        currentCapacity -= currentDraw / 3600.0f / 20.0f; // per tick
        if (currentCapacity < 0) currentCapacity = 0;
        voltage = 4.2f * (currentCapacity / capacity);
    }

    public boolean isEmpty() {
        return currentCapacity <= 0;
    }

    public float getPercentRemaining() {
        return currentCapacity / capacity;
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
    }

    public void load(CompoundTag tag) {
        capacity = tag.getFloat("capacity");
        currentCapacity = tag.getFloat("currentCapacity");
        voltage = tag.getFloat("voltage");
    }
}
