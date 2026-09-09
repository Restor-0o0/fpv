package org.restor.fpv.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class CustomData {
    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag == null ? new CompoundTag() : tag);
    }

    public static CustomData get(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? null : new CustomData(t);
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag == null ? null : tag.copy());
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> action) {
        action.accept(stack.getOrCreateTag());
    }

    public CompoundTag copyTag() {
        return tag.copy();
    }

    public boolean contains(String key) {
        return tag.contains(key);
    }

    public boolean isEmpty() {
        return tag.isEmpty();
    }
}
