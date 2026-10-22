package org.restor.fpv.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

import java.util.List;

public class RcTransmitterItem extends Item {
    private final boolean longRange;

    public RcTransmitterItem(boolean longRange) {
        super(new Item.Properties().stacksTo(16));
        this.longRange = longRange;
    }

    public boolean longRange() { return longRange; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (sp.isShiftKeyDown()) {
            Entity aimed = RcLink.aircraftInSight(sp, RcLink.range(stack));
            if (aimed == null) say(sp, "msg.fpv.tx_bind_no_target");
            else if (!RcLink.isBindable(aimed)) RcLink.sayNotReady(sp, aimed);
            else RcLink.toggleBinding(sp, stack, aimed);
            return InteractionResultHolder.success(stack);
        }

        Entity target = RcLink.resolve(sp.serverLevel(), stack);
        if (target == null || target.isRemoved()) {
            say(sp, "msg.fpv.tx_unbound");
            return InteractionResultHolder.success(stack);
        }
        if (target.distanceTo(sp) > RcLink.range(stack)) {
            say(sp, "msg.fpv.tx_out_of_range");
            return InteractionResultHolder.success(stack);
        }

        boolean fpv = RcLink.hasGoggles(sp);
        if (target instanceof DroneEntity drone) {
            // A drone flying itself is not something you take the sticks of; the bound
            // transmitter switches its firmware mode instead, from any distance in range.
            if (drone.isFollowing()) {
                drone.cycleMode(sp);
            } else if (drone.hasAnyPilot()) {
                say(sp, "msg.fpv.tx_busy");
            } else {
                drone.beginRemoteControl(sp, fpv);
                say(sp, fpv ? "msg.fpv.tx_link_fpv" : "msg.fpv.tx_link_los");
            }
        } else if (target instanceof PlaneEntity plane) {
            if (plane.hasAnyPilot()) {
                say(sp, "msg.fpv.tx_busy");
            } else if (plane.mountPilot(sp)) {
                say(sp, "msg.fpv.tx_link_fpv");
            } else {
                say(sp, "msg.fpv.need_goggles");
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        String bound = RcLink.boundName(stack);
        return bound.isEmpty() ? super.getName(stack)
            : Component.translatable("item.fpv.tx_bound_name",
                super.getName(stack), Component.translatable(bound));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = RcLink.boundName(stack);
        tooltip.add(name.isEmpty()
            ? Component.translatable("tooltip.fpv.tx.unbound").withStyle(ChatFormatting.DARK_GRAY)
            : Component.translatable("tooltip.fpv.tx.bound", Component.translatable(name))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.fpv.tx.range",
            (int) (double) (longRange ? org.restor.fpv.CommonConfig.TX_LR_RANGE.get()
                                      : org.restor.fpv.CommonConfig.TX_RANGE.get()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.tx.usage").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
