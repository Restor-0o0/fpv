package org.restor.fpv.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.restor.fpv.Fpv;

/** Punching an aircraft with a transmitter in hand pairs it (or drops the pairing). */
@Mod.EventBusSubscriber(modid = Fpv.MODID)
public final class RcBindingHandler {
    private RcBindingHandler() {}

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        ItemStack stack = RcLink.heldTransmitter(event.getEntity());
        if (stack.isEmpty()) return;
        Entity target = event.getTarget();
        if (!RcLink.isAircraft(target)) return;

        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (RcLink.isBindable(target)) RcLink.toggleBinding(sp, stack, target);
        else RcLink.sayNotReady(sp, target);
    }
}
