package org.restor.fpv.assembly;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ComponentCondition {
    public static final String NBT_KEY = "FpvCondition";
    public static final int MAX = 1000;

    public static int read(CompoundTag tag) {
        if (tag == null || !tag.contains(NBT_KEY, Tag.TAG_ANY_NUMERIC)) return MAX;
        return clamp(tag.getInt(NBT_KEY));
    }

    public static void write(CompoundTag tag, int condition) {
        tag.putInt(NBT_KEY, clamp(condition));
    }

    public static int get(ItemStack stack) {
        if (stack.isEmpty()) return MAX;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? MAX : read(data.copyTag());
    }

    public static void set(ItemStack stack, int condition) {
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> write(tag, condition));
        }
    }

    public static void ensure(ItemStack stack) {
        if (!stack.isEmpty()) set(stack, get(stack));
    }

    public static int damage(ItemStack stack, int amount) {
        if (org.restor.fpv.CommonConfig.DEV_NO_DAMAGE.get()) return get(stack);
        int condition = Math.max(0, get(stack) - Math.max(0, amount));
        set(stack, condition);
        return condition;
    }

    public static int repair(ItemStack stack, int amount) {
        int condition = Math.min(MAX, get(stack) + Math.max(0, amount));
        set(stack, condition);
        return condition;
    }

    public static float ratio(ItemStack stack) {
        return get(stack) / (float) MAX;
    }

    public static int percent(ItemStack stack) {
        return Math.round(ratio(stack) * 100f);
    }

    private static int clamp(int condition) {
        return Math.max(0, Math.min(MAX, condition));
    }

    private ComponentCondition() {}
}
