package org.restor.fpv.client;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.restor.fpv.Fpv;

/** Adds a one-line "what is this for" hint to any mod item that declares tooltip.fpv.hint.<id>. */
@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class BlockHintTooltips {
    private BlockHintTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Identifier id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!Fpv.MODID.equals(id.getNamespace())) return;
        String key = "tooltip.fpv.hint." + id.getPath();
        if (I18n.exists(key)) {
            event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (I18n.exists(key + ".2")) {
            event.getToolTip().add(Component.translatable(key + ".2").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
