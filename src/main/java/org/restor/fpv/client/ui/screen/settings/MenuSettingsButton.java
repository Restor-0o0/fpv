package org.restor.fpv.client.ui.screen.settings;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.restor.fpv.Fpv;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class MenuSettingsButton extends AbstractWidget {
    private static final int SIZE = 20;

    private final Screen owner;

    private MenuSettingsButton(int x, int y, Screen owner) {
        super(x, y, SIZE, SIZE, Component.translatable("screen.fpv.settings"));
        this.owner = owner;
        setTooltip(Tooltip.create(Component.translatable("screen.fpv.settings")));
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        String anchorKey;
        if (screen instanceof TitleScreen) anchorKey = "menu.singleplayer";
        else if (screen instanceof PauseScreen) anchorKey = "menu.returnToGame";
        else return;

        for (var listener : event.getListenersList()) {
            if (listener instanceof Button button && isKey(button.getMessage(), anchorKey)) {
                event.addListener(new MenuSettingsButton(button.getX() - SIZE - 4, button.getY(), screen));
                return;
            }
        }
    }

    private static boolean isKey(Component message, String key) {
        return message.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        Minecraft.getInstance().gui.setScreen(new FpvSettingsScreen(owner));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused();
        SettingsUi.rect(g, getX(), getY(), SIZE, SIZE, hot ? 0xE0262932 : 0xC0181A20);
        SettingsUi.outline(g, getX(), getY(), SIZE, SIZE, hot ? SettingsUi.ACCENT : 0xFF000000);
        SettingsUi.droneIcon(g, getX() + 4, getY() + 4, hot ? SettingsUi.ACCENT : SettingsUi.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getMessage());
    }
}
