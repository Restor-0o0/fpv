package org.restor.fpv.util;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Breaking a parked craft hands it straight back, so it is never lost to a stray click. */
public final class CraftPickup {
    public static void giveTo(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    private CraftPickup() {}
}
