package org.restor.fpv.client.ui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.restor.fpv.network.SetDroneCodePacket;

// Opened by right-clicking a remote-controllable drone: set its 6-digit link code.
public class DroneCodeScreen extends Screen {
    private final int droneId;
    private final String currentCode;
    private EditBox codeBox;

    public DroneCodeScreen(int droneId, String currentCode) {
        super(Component.translatable("screen.fpv.set_code"));
        this.droneId = droneId;
        this.currentCode = currentCode;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        codeBox = new EditBox(font, cx - 50, cy - 20, 100, 20, Component.empty());
        codeBox.setMaxLength(6);
        codeBox.setFilter(s -> s.matches("\\d*"));
        codeBox.setValue(currentCode);
        addRenderableWidget(codeBox);
        setInitialFocus(codeBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.set_code_save"), b -> save(false))
            .bounds(cx - 75, cy + 8, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.set_code_remember"), b -> save(true))
            .bounds(cx - 75, cy + 32, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.fpv.set_code_clear"), b -> {
                PacketDistributor.sendToServer(new SetDroneCodePacket(droneId, ""));
                onClose();
            })
            .bounds(cx - 75, cy + 56, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(cx + 3, cy + 56, 72, 20).build());
    }

    private void save(boolean remember) {
        String code = codeBox.getValue();
        if (code.length() == 6) {
            PacketDistributor.sendToServer(new SetDroneCodePacket(droneId, code));
            if (remember) {
                // Remembered code is auto-assigned to newly placed remote drones.
                org.restor.fpv.Config.DEFAULT_LINK_CODE.set(code);
                org.restor.fpv.Config.save();
            }
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            save(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 45, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.fpv.set_code_hint"),
            width / 2, height / 2 - 33, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
