package org.restor.fpv.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.item.ModItems;

import java.util.Map;

public class DroneBuilder {
    public static final String TAG_COMPONENTS = "DroneComponents";
    public static final String TAG_FRAME = "Frame";
    public static final String TAG_MOTOR = "Motor";
    public static final String TAG_BATTERY = "Battery";
    public static final String TAG_PROPELLER = "Propeller";

    public static ItemStack assemble(Map<ComponentType, ComponentStats> parts) {
        ItemStack stack = new ItemStack(ModItems.ASSEMBLED_DRONE.get());
        CompoundTag tag = new CompoundTag();
        CompoundTag components = new CompoundTag();

        for (var entry : parts.entrySet()) {
            switch (entry.getKey()) {
                case FRAME -> saveComponent(components, TAG_FRAME, entry.getValue());
                case MOTOR -> saveComponent(components, TAG_MOTOR, entry.getValue());
                case BATTERY -> saveComponent(components, TAG_BATTERY, entry.getValue());
                case PROPELLER -> saveComponent(components, TAG_PROPELLER, entry.getValue());
            }
        }

        tag.put(TAG_COMPONENTS, components);

        // Compute derived stats for display
        var frame = findStats(parts, ComponentType.FRAME);
        var battery = findStats(parts, ComponentType.BATTERY);
        var motor = findStats(parts, ComponentType.MOTOR);
        var prop = findStats(parts, ComponentType.PROPELLER);

        if (frame != null) tag.putFloat("FrameSize", frame.frameSize());
        if (battery != null) {
            tag.putInt("BatteryCells", battery.batteryCells());
            tag.putInt("BatteryCapacity", battery.batteryCapacity());
        }
        if (motor != null) tag.putFloat("MotorPower", motor.motorPower());
        if (prop != null) tag.putFloat("PropEfficiency", prop.propEfficiency());

        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));

        return stack;
    }

    private static void saveComponent(CompoundTag components, String key, ComponentStats stats) {
        CompoundTag c = new CompoundTag();
        c.putString("Name", stats.name());
        c.putString("Type", stats.type().name());
        c.putFloat("Weight", stats.weight());
        c.putFloat("FrameSize", stats.frameSize());
        c.putFloat("FrameDurability", stats.frameDurability());
        c.putFloat("MotorKv", stats.motorKv());
        c.putFloat("MotorPower", stats.motorPower());
        c.putFloat("MotorDiameter", stats.motorDiameter());
        c.putInt("BatteryCells", stats.batteryCells());
        c.putInt("BatteryCapacity", stats.batteryCapacity());
        c.putFloat("BatteryMaxDischarge", stats.batteryMaxDischarge());
        c.putFloat("PropDiameter", stats.propDiameter());
        c.putFloat("PropPitch", stats.propPitch());
        c.putInt("PropBlades", stats.propBlades());
        c.putFloat("PropEfficiency", stats.propEfficiency());
        c.putInt("VtxPowerMw", stats.vtxPowerMw());
        c.putFloat("VtxRangeMultiplier", stats.vtxRangeMultiplier());
        c.putFloat("CameraFov", stats.cameraFov());
        c.putFloat("CameraTiltAngle", stats.cameraTiltAngle());
        c.putBoolean("FcHasOsd", stats.fcHasOsd());
        c.putBoolean("FcHasBlackbox", stats.fcHasBlackbox());
        components.put(key, c);
    }

    public static ComponentStats loadComponent(CompoundTag components, String key) {
        if (!components.contains(key)) return null;
        CompoundTag c = components.getCompound(key);
        return new ComponentStats(
            ComponentType.valueOf(c.getString("Type")),
            c.getString("Name"),
            c.getFloat("Weight"),
            c.getFloat("FrameSize"),
            c.getFloat("FrameDurability"),
            c.getFloat("MotorKv"),
            c.getFloat("MotorPower"),
            c.getFloat("MotorDiameter"),
            c.getInt("BatteryCells"),
            c.getInt("BatteryCapacity"),
            c.getFloat("BatteryMaxDischarge"),
            c.getFloat("PropDiameter"),
            c.getFloat("PropPitch"),
            c.getInt("PropBlades"),
            c.getFloat("PropEfficiency"),
            c.getInt("VtxPowerMw"),
            c.getFloat("VtxRangeMultiplier"),
            c.getFloat("CameraFov"),
            c.getFloat("CameraTiltAngle"),
            c.getBoolean("FcHasOsd"),
            c.getBoolean("FcHasBlackbox")
        );
    }

    private static ComponentStats findStats(Map<ComponentType, ComponentStats> parts, ComponentType type) {
        return parts.get(type);
    }
}
