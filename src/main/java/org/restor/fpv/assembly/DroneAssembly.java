package org.restor.fpv.assembly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.data.DroneData;

/**
 * A modular drone build: frame + battery + camera + per-slot motors and props.
 * Serialized into item/entity NBT; all flight characteristics are derived from the
 * installed components, including per-motor thrust imbalance (mismatched props →
 * constant pitch/roll/yaw trim).
 */
public class DroneAssembly {
    public static final int MAX_MOTORS = 8;
    // Tuned so a 4×"2207" (power 1.0) + 5x4x3 props build ≈ 70 N total (TWR ~11).
    private static final float THRUST_N_PER_POWER = 17.5f;
    private static final float ELECTRONICS_GRAMS = 110f; // ESC, wiring, FC, mounts baseline

    public ItemStack frame = ItemStack.EMPTY;
    public ItemStack battery = ItemStack.EMPTY;
    public ItemStack camera = ItemStack.EMPTY;
    public final ItemStack[] motors = new ItemStack[MAX_MOTORS];
    public final ItemStack[] props = new ItemStack[MAX_MOTORS];

    public DroneAssembly() {
        java.util.Arrays.fill(motors, ItemStack.EMPTY);
        java.util.Arrays.fill(props, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Frame", frame.save(new CompoundTag()));
        tag.put("Battery", battery.save(new CompoundTag()));
        tag.put("Camera", camera.save(new CompoundTag()));
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (!motors[i].isEmpty()) tag.put("Motor" + i, motors[i].save(new CompoundTag()));
            if (!props[i].isEmpty())  tag.put("Prop" + i,  props[i].save(new CompoundTag()));
        }
        return tag;
    }

    public static DroneAssembly load(CompoundTag tag) {
        DroneAssembly a = new DroneAssembly();
        a.frame = ItemStack.of(tag.getCompound("Frame"));
        a.battery = ItemStack.of(tag.getCompound("Battery"));
        a.camera = ItemStack.of(tag.getCompound("Camera"));
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (tag.contains("Motor" + i)) a.motors[i] = ItemStack.of(tag.getCompound("Motor" + i));
            if (tag.contains("Prop" + i))  a.props[i]  = ItemStack.of(tag.getCompound("Prop" + i));
        }
        return a;
    }

    // ---------------------------------------------------------------- structure

    public FrameSpec frameSpec() {
        return frame.isEmpty() ? null : FrameSpecs.byItem(frame.getItem());
    }

    /** Complete = frame + battery + camera + a motor AND a prop in every frame slot. */
    public boolean isComplete() {
        FrameSpec spec = frameSpec();
        if (spec == null || battery.isEmpty() || camera.isEmpty()) return false;
        if (!isCompatible(frame, battery) || !isCompatible(frame, camera)) return false;
        for (int i = 0; i < spec.motorCount(); i++) {
            if (stats(motors[i], ComponentType.MOTOR) == null) return false;
            if (stats(props[i], ComponentType.PROPELLER) == null) return false;
            if (!isCompatible(frame, motors[i]) || !isCompatible(frame, props[i])) return false;
        }
        return true;
    }

    public static boolean isCompatible(ItemStack frame, ItemStack component) {
        if (frame.isEmpty() || component.isEmpty()) return false;
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        if (frameStats == null || !(component.getItem() instanceof ComponentItem ci)) return false;
        ComponentStats stats = ci.getStats();
        float frameSize = frameStats.frameSize();
        return switch (stats.type()) {
            case FRAME -> true;
            case MOTOR -> compatibleMotor(frameSize, stats);
            case PROPELLER -> compatibleProp(frameSize, stats);
            case BATTERY -> compatibleBattery(frameSize, stats);
            case CAMERA -> compatibleCamera(frameSize, stats);
            default -> true;
        };
    }

    private static boolean compatibleMotor(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.motorDiameter() <= 12.0f && stats.motorPower() <= 0.35f;
        if (frameSize <= 4.0f) return stats.motorDiameter() >= 11.0f && stats.motorDiameter() <= 16.0f && stats.motorPower() <= 0.6f;
        if (frameSize <= 5.5f) return stats.motorDiameter() >= 20.0f && stats.motorDiameter() <= 30.0f;
        if (frameSize <= 6.5f) return stats.motorDiameter() >= 22.0f && stats.motorDiameter() <= 31.0f && stats.motorKv() <= 1800.0f;
        return stats.motorDiameter() >= 28.0f && stats.motorKv() <= 1500.0f;
    }

    private static boolean compatibleProp(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.propDiameter() <= 2.6f;
        if (frameSize <= 4.0f) return stats.propDiameter() <= 3.6f;
        if (frameSize <= 5.5f) return stats.propDiameter() >= 4.5f && stats.propDiameter() <= 5.2f;
        if (frameSize <= 6.5f) return stats.propDiameter() >= 5.0f && stats.propDiameter() <= 7.2f;
        return stats.propDiameter() >= 5.0f;
    }

    private static boolean compatibleBattery(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.batteryCells() == 1 && stats.batteryCapacity() <= 450;
        if (frameSize <= 4.0f) return stats.batteryCells() >= 3 && stats.batteryCells() <= 4 && stats.batteryCapacity() <= 900;
        if (frameSize <= 5.5f) return stats.batteryCells() >= 4 && stats.batteryCells() <= 6 && stats.batteryCapacity() <= 1500;
        if (frameSize <= 6.5f) return stats.batteryCells() >= 4 && stats.batteryCells() <= 6 && stats.batteryCapacity() >= 900;
        return stats.batteryCells() == 6 && stats.batteryCapacity() >= 900;
    }

    private static boolean compatibleCamera(float frameSize, ComponentStats stats) {
        return frameSize > 2.0f || stats.weight() <= 8.0f;
    }

    private static ComponentStats stats(ItemStack stack, ComponentType type) {
        if (!stack.isEmpty() && stack.getItem() instanceof ComponentItem ci && ci.getComponentType() == type) {
            return ci.getStats();
        }
        return null;
    }

    // ---------------------------------------------------------------- derived stats

    public float totalMassKg() {
        float g = ELECTRONICS_GRAMS;
        g += weight(frame) + weight(battery) + weight(camera);
        for (int i = 0; i < MAX_MOTORS; i++) g += weight(motors[i]) + weight(props[i]);
        return Math.max(0.03f, g / 1000f);
    }

    private static float weight(ItemStack s) {
        return s.getItem() instanceof ComponentItem ci ? ci.getStats().weight() : 0;
    }

    /** Relative thrust factor of motor slot i (motor power × prop geometry/efficiency). */
    public float motorThrustFactor(int i) {
        ComponentStats motor = stats(motors[i], ComponentType.MOTOR);
        ComponentStats prop = stats(props[i], ComponentType.PROPELLER);
        if (motor == null || prop == null) return 0;
        // Prop factor normalized to a 5x4 prop at efficiency 1.0.
        float propFactor = prop.propEfficiency() * (prop.propDiameter() * prop.propPitch()) / 20f;
        return motor.motorPower() * propFactor;
    }

    /** Max total thrust in Newtons. */
    public float totalThrustN() {
        FrameSpec spec = frameSpec();
        if (spec == null) return 1;
        float sum = 0;
        for (int i = 0; i < spec.motorCount(); i++) sum += motorThrustFactor(i);
        return Math.max(1f, sum * THRUST_N_PER_POWER);
    }

    /**
     * Constant attitude trim in °/s at FULL throttle caused by thrust imbalance
     * between motor slots (e.g. one weaker prop). Returns {roll, pitch, yaw}.
     * Sign conventions match DroneEntity: positive roll = right bank, positive
     * pitch rate = nose down, yaw uses stick sign (positive = turn right).
     */
    public float[] computeTrim() {
        FrameSpec spec = frameSpec();
        if (spec == null) return new float[]{0, 0, 0};
        int n = spec.motorCount();
        float avg = 0;
        for (int i = 0; i < n; i++) avg += motorThrustFactor(i);
        avg /= n;
        if (avg <= 0) return new float[]{0, 0, 0};

        float roll = 0, pitch = 0, yaw = 0;
        for (int i = 0; i < n; i++) {
            float rel = motorThrustFactor(i) / avg - 1f; // + = this corner is stronger
            MountPoint m = spec.motorMounts().get(i);
            float nx = m.x() / spec.armLength();
            float nz = m.z() / spec.armLength();
            // Stronger right side (+x) lifts right → banks LEFT (negative roll).
            roll  += -rel * nx;
            // Stronger front (-z) lifts nose → pitches UP (negative pitch rate) —
            // rel * nz is already negative for front mounts.
            pitch += rel * nz;
            // Standard props-out spin layout: even slots CCW, odd CW. A stronger
            // CCW motor adds CW body yaw (turn right = positive stick sense).
            yaw   += rel * (i % 2 == 0 ? 1f : -1f);
        }
        // Full mismatch on one corner of a quad ≈ 0.25 → ~30°/s trim at full throttle.
        float k = 120f;
        return new float[]{roll * k, pitch * k, yaw * k * 0.5f};
    }

    /** Flight characteristics derived from the installed components. */
    public DroneData buildDroneData() {
        FrameSpec spec = frameSpec();
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        ComponentStats batteryStats = stats(battery, ComponentType.BATTERY);
        ComponentStats cameraStats = stats(camera, ComponentType.CAMERA);

        float mass = totalMassKg();
        float thrust = totalThrustN();
        float frameSize = frameStats != null ? frameStats.frameSize() : 5f;
        // Drag area grows with frame size (5-inch ≈ 0.025 like the stock racer).
        float dragCdA = 0.005f * frameSize;
        float camTilt = cameraStats != null ? cameraStats.cameraTiltAngle() : 20f;
        int capacity = batteryStats != null ? batteryStats.batteryCapacity() : 650;

        // Bigger frames rotate slower; responsiveness drops with size too.
        float rate = Math.max(200f, 700f - frameSize * 50f);
        float resp = Math.max(6f, 18f - frameSize * 1.2f);

        return new DroneData(
            DroneData.DroneType.CUSTOM, "Custom Build", 1.0f,
            rate * 0.9f, rate, rate * 0.6f,
            mass, dragCdA, capacity,
            camTilt, thrust, spec != null ? spec.armLength() : 0.5f,
            resp
        );
    }
}
