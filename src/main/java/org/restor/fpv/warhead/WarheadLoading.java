package org.restor.fpv.warhead;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.item.ModItems;

import java.util.function.Consumer;

public final class WarheadLoading {
    private WarheadLoading() {}

    public static boolean isWarhead(ItemStack stack) {
        return WarheadItem.typeOf(stack) != null;
    }

    public static boolean swap(Player player, ItemStack held, WarheadType current, Consumer<WarheadType> apply) {
        return swap(player, held, current, Float.MAX_VALUE, apply);
    }

    public static boolean swap(Player player, ItemStack held, WarheadType current, float capacityKg,
                               Consumer<WarheadType> apply) {
        WarheadType type = WarheadItem.typeOf(held);
        if (type == null) return false;
        if (type.mass() > capacityKg) {
            player.sendOverlayMessage(Component.translatable("msg.fpv.warhead_too_heavy",
                Component.translatable("item.fpv." + type.itemName()),
                String.format("%.1f", type.mass()), String.format("%.1f", capacityKg)));
            return true;
        }
        if (type != current) {
            apply.accept(type);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
                ItemStack removed = new ItemStack(ModItems.WARHEADS.get(current).get());
                if (!player.addItem(removed)) player.drop(removed, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        player.sendOverlayMessage(Component.translatable("msg.fpv.warhead_loaded",
            Component.translatable("item.fpv." + type.itemName())));
        return true;
    }
}
