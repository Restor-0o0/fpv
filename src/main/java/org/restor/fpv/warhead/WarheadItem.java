package org.restor.fpv.warhead;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;


public class WarheadItem extends Item {
    private final WarheadType type;

    public WarheadItem(Item.Properties props, WarheadType type) {
        super(props.stacksTo(16));
        this.type = type;
    }

    public WarheadType getType() { return type; }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        return org.restor.fpv.block.PartsPiles.placeOn(ctx);
    }

    public static WarheadType typeOf(ItemStack stack) {
        return stack.getItem() instanceof WarheadItem w ? w.type : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.fpv.warhead.power", String.format("%.1f", type.power()))
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.ordnance.terrain", String.format("%.1f", type.blockPower()))
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.ordnance.mob", String.format("%.1f", type.mobDamageMul()))
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.warhead.mass", String.format("%.1f", type.mass()))
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (type.submunitions() > 0) {
            tooltip.accept(Component.translatable("tooltip.fpv.warhead.cluster", type.submunitions())
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (type.incendiary()) {
            tooltip.accept(Component.translatable("tooltip.fpv.warhead.incendiary")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (type.penetration() > 1.5f) {
            tooltip.accept(Component.translatable("tooltip.fpv.warhead.shaped")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.fpv.warhead.mount")
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.warhead.carriers", carriers())
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.fpv.component_place"));
    }

    private static final String[][] CARRIERS = {
        {"2.5", "entity.fpv.kamikaze_drone"},
        {"3.0", "entity.fpv.plane_interceptor"},
        {"4.0", "entity.fpv.plane_kamikaze"},
        {"8.0", "entity.fpv.plane_longrange"},
        {"16.0", "entity.fpv.plane_loiter"},
    };

    private Component carriers() {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (String[] entry : CARRIERS) {
            if (type.mass() > Float.parseFloat(entry[0])) continue;
            if (!first) list.append(", ");
            list.append(Component.translatable(entry[1]));
            first = false;
        }
        if (first) return Component.translatable("tooltip.fpv.warhead.carriers.none");
        return list;
    }
}
