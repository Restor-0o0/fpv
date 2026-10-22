package org.restor.fpv.component;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The casual side of the radio link: no bands, no MHz, just eight numbered channels a
 * player can dial on a VTX or a jammer. A jammer only reaches craft on its own channel,
 * unless it is left on the sweep setting.
 */
public final class RadioChannel {
    public static final int COUNT = 8;
    public static final int SWEEP = 0;
    public static final int DEFAULT = 1;
    private static final String KEY = "Channel";

    private RadioChannel() {}

    public static int clamp(int channel) {
        return channel < DEFAULT || channel > COUNT ? DEFAULT : channel;
    }

    public static int of(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? DEFAULT : clamp(data.copyTag().getIntOr(KEY, 0));
    }

    public static void set(ItemStack stack, int channel) {
        if (!stack.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt(KEY, clamp(channel)));
    }

    public static int next(int channel) {
        return channel >= COUNT ? DEFAULT : clamp(channel) + 1;
    }

    public static int nextWithSweep(int channel) {
        return channel >= COUNT ? SWEEP : channel + 1;
    }

    public static boolean reaches(int jammer, int craft) {
        return jammer == SWEEP || jammer == clamp(craft);
    }

    public static Component name(int channel) {
        return channel == SWEEP
            ? Component.translatable("radio.fpv.channel.sweep")
            : Component.translatable("radio.fpv.channel", clamp(channel));
    }
}
