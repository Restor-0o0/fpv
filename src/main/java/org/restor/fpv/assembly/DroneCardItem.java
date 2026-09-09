package org.restor.fpv.assembly;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.restor.fpv.compat.CustomData;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.WarheadType;

import java.util.List;

/**
 * A whole armed aircraft folded into a card: the bench turns an assembled drone or wing
 * into one of these and back. Two cards of the same build carry byte-identical data, so
 * they stack — which is what makes a magazine of seven-inchers possible.
 */
public class DroneCardItem extends Item {
    public DroneCardItem() {
        super(new Item.Properties().stacksTo(16));
    }

    public static ItemStack of(DroneAssembly assembly, WarheadType warhead) {
        ItemStack card = new ItemStack(ModItems.DRONE_CARD.get());
        CustomData.update(card, t -> {
            t.put("Assembly", assembly.save());
            if (warhead != null) t.putString("Warhead", warhead.id());
        });
        return card;
    }

    public static boolean isCard(ItemStack stack) {
        return stack.getItem() instanceof DroneCardItem && data(stack) != null;
    }

    public static CompoundTag data(ItemStack stack) {
        CustomData d = CustomData.get(stack);
        if (d == null || !d.contains("Assembly")) return null;
        return d.copyTag();
    }

    public static DroneAssembly assembly(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag == null ? null : DroneAssembly.load(tag.getCompound("Assembly"));
    }

    public static WarheadType warhead(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag != null && tag.contains("Warhead") ? WarheadType.byId(tag.getString("Warhead")) : null;
    }

    /** Frame size in inches of the aircraft on the card, or 0 when the card is blank. */
    public static float frameSize(ItemStack stack) {
        DroneAssembly a = assembly(stack);
        if (a == null || !(a.frame.getItem() instanceof ComponentItem ci)) return 0f;
        ComponentStats stats = ci.getStats();
        return stats.type() == ComponentType.FRAME ? stats.frameSize() : 0f;
    }

    /** True when the carded aircraft can itself carry cards — those must not nest. */
    public static boolean hasBay(ItemStack stack) {
        DroneAssembly a = assembly(stack);
        return a != null && a.bayKind() != DroneAssembly.BayKind.NONE;
    }

    public static boolean isWing(ItemStack stack) {
        DroneAssembly a = assembly(stack);
        return a != null && DroneAssembly.isPlaneFrame(a.frame);
    }

    @Override
    public Component getName(ItemStack stack) {
        DroneAssembly a = assembly(stack);
        if (a == null || a.frame.isEmpty()) return super.getName(stack);
        return Component.translatable("item.fpv.drone_card.named", a.frame.getHoverName());
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context,
                                List<Component> tooltip, TooltipFlag flag) {
        DroneAssembly a = assembly(stack);
        if (a == null) {
            tooltip.add(Component.translatable("tooltip.fpv.drone_card.blank")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        WarheadType w = warhead(stack);
        tooltip.add(Component.translatable("tooltip.fpv.drone_card.warhead",
            w == null ? Component.translatable("tooltip.fpv.drone_card.none")
                      : Component.translatable("item.fpv." + w.itemName())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.drone_card.mass",
            String.format(java.util.Locale.ROOT, "%.2f", a.totalMassKg())).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.fpv.drone_card.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
