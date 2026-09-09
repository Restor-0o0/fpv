package org.restor.fpv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.restor.fpv.assembly.AssemblyStationMenu;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.item.ModItems;

import java.util.List;
import java.util.Locale;

// Programmatic background (no texture needed): dark panel, slot frames, hint lines
// from the frame slot to its satellites.
public class AssemblyStationScreen extends AbstractContainerScreen<AssemblyStationMenu> {
    private Button repairButton;

    public AssemblyStationScreen(AssemblyStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 276;
        imageHeight = 225;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        repairButton = addRenderableWidget(Button.builder(
            Component.translatable("screen.fpv.assembly.repair"),
            button -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AssemblyStationMenu.BUTTON_REPAIR);
                }
            }).bounds(leftPos + 207, topPos + 181, 61, 20).build());
        repairButton.active = menu.canRepair();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        repairButton.active = menu.canRepair();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // Panel
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2B33);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF3C3C46);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF23232B);
        // Inventory zone
        g.fill(x + 4, y + 138, x + 172, y + imageHeight - 4, 0xFF2E2E38);
        g.fill(x + 176, y + 4, x + 272, y + imageHeight - 4, 0xFF191921);

        // Slot frames for every menu slot in the station area
        for (int i = 0; i < AssemblyStationMenu.SLOT_COUNT; i++) {
            var slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            int accent = switch (i) {
                case AssemblyStationMenu.SLOT_MODULE -> 0xFFFFB733;
                case AssemblyStationMenu.SLOT_FIRMWARE -> 0xFF7BE08A;
                case AssemblyStationMenu.SLOT_REPAIR_MATERIAL -> 0xFF5FA8D3;
                case AssemblyStationMenu.SLOT_RESULT -> 0xFF66CC66;
                case AssemblyStationMenu.SLOT_CARD_IN -> 0xFFD9A05B;
                case AssemblyStationMenu.SLOT_CARD_OUT -> 0xFFE8C27A;
                default -> i >= AssemblyStationMenu.SLOT_BAY_FIRST
                    && i < AssemblyStationMenu.SLOT_BAY_FIRST + DroneAssembly.BAY_SIZE
                    ? 0xFFCC6666 : 0xFF8B8B99;
            };
            drawSlot(g, sx, sy, accent, slot.hasItem());
            if (!slot.hasItem()) drawGhost(g, ghostFor(i), sx + 1, sy + 1);
        }
        // Player inventory slots
        for (int i = AssemblyStationMenu.SLOT_COUNT; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            drawSlot(g, x + slot.x - 1, y + slot.y - 1, 0xFF8B8B99, slot.hasItem());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("screen.fpv.assembly.diagnostics"), 182, 8, 0xFFE0E0E8, false);

        var issues = menu.issues();
        int lineY = 23;
        if (issues.isEmpty()) {
            g.drawString(font, Component.translatable("assembly.validation.ready"), 182, lineY, 0xFF66CC66, false);
        } else {
            int lines = 0;
            for (var issue : issues) {
                int color = issue.isError() ? 0xFFFF7777 : 0xFFFFC45C;
                for (var line : font.split(Component.translatable(issue.localizationKey()), 84)) {
                    if (lines++ >= 5) break;
                    g.drawString(font, line, 182, lineY, color, false);
                    lineY += 10;
                }
                if (lines >= 5) break;
            }
        }

        g.drawString(font, metric("screen.fpv.assembly.mass", menu.massKg(), "kg", 3), 182, 78, 0xFFB8B8C4, false);
        g.drawString(font, metric("screen.fpv.assembly.thrust", menu.thrustN(), "N", 2), 182, 90, 0xFFB8B8C4, false);
        g.drawString(font, metric("screen.fpv.assembly.twr", menu.twr(), "", 2), 182, 102, 0xFFB8B8C4, false);
        g.drawString(font, metric("screen.fpv.assembly.hover", menu.hoverThrottle() * 100f, "%", 0), 182, 114, 0xFFB8B8C4, false);
        int conditionColor = menu.conditionPercent() < 50 ? 0xFFFF7777
            : menu.conditionPercent() < 100 ? 0xFFFFC45C : 0xFF66CC66;
        g.drawString(font, Component.translatable("screen.fpv.assembly.condition", menu.conditionPercent()),
            182, 126, conditionColor, false);

        ComponentType repairType = menu.repairTargetType();
        Component repairStatus;
        if (repairType == null) {
            repairStatus = Component.translatable("screen.fpv.assembly.no_repair");
        } else {
            Component target = repairTargetName(repairType, menu.repairTargetIndex());
            repairStatus = Component.translatable("screen.fpv.assembly.repair_cost", target,
                menu.repairMaterial().getDescription());
        }
        int repairY = 140;
        for (var line : font.split(repairStatus, 84)) {
            g.drawString(font, line, 182, repairY, 0xFFAFC8D8, false);
            repairY += 10;
        }
    }

    private Component repairTargetName(ComponentType type, int index) {
        return switch (type) {
            case MOTOR, PROPELLER -> Component.translatable(
                "screen.fpv.assembly.part." + type.name().toLowerCase(Locale.ROOT), index + 1);
            default -> Component.translatable(
                "screen.fpv.assembly.part." + type.name().toLowerCase(Locale.ROOT));
        };
    }

    private Component metric(String key, float value, String unit, int decimals) {
        return Component.translatable(key, String.format(Locale.ROOT, "%." + decimals + "f%s", value, unit));
    }

    // An occupied slot gets a lit frame and a pale tray; an empty one stays a dark recess.
    // Dark parts (props especially) were unreadable when both looked the same.
    private void drawSlot(GuiGraphics g, int x, int y, int accent, boolean filled) {
        g.fill(x, y, x + 18, y + 18, filled ? accent : dim(accent));
        g.fill(x + 1, y + 1, x + 17, y + 17, filled ? 0xFF55555F : 0xFF101015);
        if (filled) {
            g.fill(x + 1, y + 1, x + 17, y + 2, 0xFF6E6E7A);
            g.fill(x + 1, y + 16, x + 17, y + 17, 0xFF3A3A44);
        }
    }

    private static int dim(int argb) {
        int r = (argb >> 16 & 0xFF) * 2 / 5;
        int gr = (argb >> 8 & 0xFF) * 2 / 5;
        int b = (argb & 0xFF) * 2 / 5;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    // A darkened stand-in of the part that belongs here, so an empty bench still reads
    // as a wiring diagram. The repair square shows whatever the current build actually needs.
    private ItemStack ghostFor(int slotIndex) {
        if (slotIndex >= AssemblyStationMenu.SLOT_MOTOR_FIRST
            && slotIndex < AssemblyStationMenu.SLOT_PROP_FIRST) {
            return new ItemStack(ModComponents.MOTOR_2207_2450.get());
        }
        if (slotIndex >= AssemblyStationMenu.SLOT_PROP_FIRST
            && slotIndex < AssemblyStationMenu.SLOT_MODULE) {
            return new ItemStack(ModComponents.PROP_5X4X3.get());
        }
        if (slotIndex >= AssemblyStationMenu.SLOT_BAY_FIRST
            && slotIndex < AssemblyStationMenu.SLOT_BAY_FIRST + DroneAssembly.BAY_SIZE) {
            return new ItemStack(ModItems.DRONE_CARD.get());
        }
        return switch (slotIndex) {
            case AssemblyStationMenu.SLOT_FRAME -> new ItemStack(ModComponents.FRAME_5INCH.get());
            case AssemblyStationMenu.SLOT_BATTERY -> new ItemStack(ModComponents.BATTERY_4S_1300.get());
            case AssemblyStationMenu.SLOT_CAMERA -> new ItemStack(ModComponents.CAMERA_STANDARD.get());
            case AssemblyStationMenu.SLOT_MODULE -> new ItemStack(ModComponents.MODULE_PAYLOAD_BAY.get());
            case AssemblyStationMenu.SLOT_FIRMWARE -> new ItemStack(ModComponents.FIRMWARE_COMPANION.get());
            case AssemblyStationMenu.SLOT_REPAIR_MATERIAL -> {
                Item material = menu.repairMaterial();
                yield new ItemStack(material != null ? material : Items.IRON_INGOT);
            }
            case AssemblyStationMenu.SLOT_RESULT -> new ItemStack(ModItems.MODULAR_DRONE.get());
            case AssemblyStationMenu.SLOT_CARD_IN -> new ItemStack(ModItems.MODULAR_DRONE.get());
            case AssemblyStationMenu.SLOT_CARD_OUT -> new ItemStack(ModItems.DRONE_CARD.get());
            default -> ItemStack.EMPTY;
        };
    }

    private void drawGhost(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.setColor(0.26f, 0.26f, 0.30f, 1.0f);
        g.renderFakeItem(stack, x, y);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private Component slotName(int slotIndex) {
        if (slotIndex >= AssemblyStationMenu.SLOT_MOTOR_FIRST
            && slotIndex < AssemblyStationMenu.SLOT_PROP_FIRST) {
            return Component.translatable("screen.fpv.assembly.part.motor",
                slotIndex - AssemblyStationMenu.SLOT_MOTOR_FIRST + 1);
        }
        if (slotIndex >= AssemblyStationMenu.SLOT_PROP_FIRST
            && slotIndex < AssemblyStationMenu.SLOT_MODULE) {
            return Component.translatable("screen.fpv.assembly.part.propeller",
                slotIndex - AssemblyStationMenu.SLOT_PROP_FIRST + 1);
        }
        if (slotIndex >= AssemblyStationMenu.SLOT_BAY_FIRST
            && slotIndex < AssemblyStationMenu.SLOT_BAY_FIRST + DroneAssembly.BAY_SIZE) {
            return Component.translatable("screen.fpv.assembly.part.bay",
                slotIndex - AssemblyStationMenu.SLOT_BAY_FIRST + 1);
        }
        return switch (slotIndex) {
            case AssemblyStationMenu.SLOT_FRAME -> Component.translatable("screen.fpv.assembly.part.frame");
            case AssemblyStationMenu.SLOT_BATTERY -> Component.translatable("screen.fpv.assembly.part.battery");
            case AssemblyStationMenu.SLOT_CAMERA -> Component.translatable("screen.fpv.assembly.part.camera");
            case AssemblyStationMenu.SLOT_MODULE -> Component.translatable("screen.fpv.payload_slot");
            case AssemblyStationMenu.SLOT_FIRMWARE -> Component.translatable("screen.fpv.firmware_slot");
            case AssemblyStationMenu.SLOT_REPAIR_MATERIAL ->
                Component.translatable("screen.fpv.assembly.repair_slot");
            case AssemblyStationMenu.SLOT_CARD_IN -> Component.translatable("screen.fpv.assembly.part.card_in");
            case AssemblyStationMenu.SLOT_CARD_OUT -> Component.translatable("screen.fpv.assembly.part.card_out");
            case AssemblyStationMenu.SLOT_RESULT -> Component.translatable("screen.fpv.assembly.part.result");
            default -> null;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < AssemblyStationMenu.SLOT_COUNT) {
            Component name = slotName(hoveredSlot.index);
            if (name != null) {
                ItemStack ghost = ghostFor(hoveredSlot.index);
                g.renderComponentTooltip(font, ghost.isEmpty() ? List.of(name) : List.of(name,
                    Component.translatable("screen.fpv.assembly.slot_example", ghost.getHoverName())
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }
}
