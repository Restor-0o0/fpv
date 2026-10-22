package org.restor.fpv.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.assembly.ModularDroneItem;
import org.restor.fpv.component.BatteryItem;

// Reads/writes the pack charge stored on an item stack. Drone items keep a full Battery
// compound (capacity + remaining mAh); loose battery packs only need a 0..1 fraction.
public final class BatteryCharge {
    private BatteryCharge() {}

    public static boolean isChargeable(ItemStack stack) {
        return stack.getItem() instanceof BatteryItem
            || stack.getItem() instanceof DroneItem
            || stack.getItem() instanceof ModularDroneItem;
    }

    /** 0..1 charge, or -1 when the stack does not hold a pack. */
    public static float get(ItemStack stack) {
        if (!isChargeable(stack)) return -1f;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? null : data.copyTag();
        if (stack.getItem() instanceof BatteryItem) {
            return tag != null && tag.contains("Charge") ? clamp(tag.getFloatOr("Charge", 0f)) : 1f;
        }
        if (tag == null || !tag.contains("Battery")) return 1f;
        CompoundTag battery = tag.getCompoundOrEmpty("Battery");
        float capacity = battery.getFloatOr("capacity", 0f);
        if (capacity <= 0) return 1f;
        return clamp(battery.getFloatOr("currentCapacity", 0f) / capacity);
    }

    /** Adds charge; returns true when something actually changed. */
    public static boolean add(ItemStack stack, float delta) {
        float current = get(stack);
        if (current < 0 || current >= 1f) return false;
        float next = clamp(current + delta);
        if (next <= current) return false;
        set(stack, next);
        return true;
    }

    public static void set(ItemStack stack, float fraction) {
        float value = clamp(fraction);
        if (stack.getItem() instanceof BatteryItem battery) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putFloat("Charge", value);
                tag.putInt("Capacity", battery.getCapacity());
            });
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag pack = tag.getCompoundOrEmpty("Battery");
            float capacity = pack.getFloatOr("capacity", 0f);
            if (capacity <= 0) {
                capacity = 1300f;
                pack.putFloat("capacity", capacity);
            }
            pack.putFloat("currentCapacity", capacity * value);
            pack.putFloat("voltage", 4.2f * value);
            tag.put("Battery", pack);
        });
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
