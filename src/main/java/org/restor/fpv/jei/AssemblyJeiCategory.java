package org.restor.fpv.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.assembly.DroneCardItem;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.WarheadItem;

/** JEI view laid out to match the real Drone Assembly Station screen. */
public final class AssemblyJeiCategory extends AbstractRecipeCategory<AssemblyJeiGuide> {
    private static final int WIDTH = 190;
    private static final int HEIGHT = 144;
    private final IDrawable background = new AssemblyStationBackground();

    public AssemblyJeiCategory(IGuiHelper guiHelper) {
        super(AssemblyJeiPlugin.ASSEMBLY, Component.translatable("block.fpv.assembly_station"),
            guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                ModBlocks.ASSEMBLY_STATION.get().asItem().getDefaultInstance()), WIDTH, HEIGHT);
    }

            @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyJeiGuide recipe, IFocusGroup focuses) {
        input(builder, 86, 40, recipe.frame(), Component.translatable("screen.fpv.assembly.part.frame"));
        alternatives(builder, 86, 20, recipe.batteries(), Component.translatable("screen.fpv.assembly.part.battery"));
        alternatives(builder, 86, 60, recipe.cameras(), Component.translatable("screen.fpv.assembly.part.camera"));
        alternatives(builder, 122, 60, recipe.flightControllers(), Component.translatable("screen.fpv.assembly.hint.controller"));
        alternatives(builder, 50, 60, recipe.videoTransmitters(), Component.translatable("screen.fpv.assembly.hint.video_transmitter"));
        alternatives(builder, 122, 40, recipe.optionalModules(), Component.translatable("screen.fpv.assembly.hint.module"));
        alternatives(builder, 50, 40, recipe.optionalFirmware(), Component.translatable("screen.fpv.assembly.hint.firmware"));

        alternatives(builder, 50, 80, recipe.motors(),
            Component.translatable("screen.fpv.assembly.part.motor", recipe.motorCount()));
        alternatives(builder, 122, 80, recipe.propellers(),
            Component.translatable("screen.fpv.assembly.part.propeller", recipe.motorCount()));

        boolean cardOutput = recipe.output().is(ModItems.DRONE_CARD.get());
        builder.addOutputSlot(cardOutput ? 122 : 86, 100).addItemStack(recipe.output())
            .addRichTooltipCallback((slot, lines) -> lines.add(Component.translatable(
                cardOutput ? "screen.fpv.assembly.hint.card" : "screen.fpv.assembly.part.result")));
        if (!cardOutput) {
            builder.addOutputSlot(122, 100)
                .addRichTooltipCallback((slot, lines) -> lines.add(
                    Component.translatable("screen.fpv.assembly.hint.card")));
        }

        int bayIndex = 0;
        boolean hasWarhead = false;
        for (ItemStack extra : recipe.extraParts()) {
            if (DroneCardItem.isCard(extra) && bayIndex < 4) {
                input(builder, 36 + bayIndex * 24, 120, extra,
                    Component.translatable("screen.fpv.assembly.part.bay", bayIndex + 1));
                bayIndex++;
            } else if (WarheadItem.typeOf(extra) != null) {
                input(builder, 140, 120, extra, Component.translatable("jei.fpv.assembly.warhead.short"));
                hasWarhead = true;
            }
        }
        for (int i = bayIndex; i < 4; i++) {
            alternatives(builder, 36 + i * 24, 120, java.util.List.of(),
                Component.translatable("screen.fpv.assembly.part.bay", i + 1));
        }
        if (!hasWarhead) {
            alternatives(builder, 140, 120, java.util.List.of(),
                Component.translatable("jei.fpv.assembly.warhead.short"));
        }
    }

    @Override
    public void draw(AssemblyJeiGuide recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
        var font = Minecraft.getInstance().font;
        int nameY = 4;
        for (var line : font.split(recipe.displayName(), 174)) {
            if (nameY > 25) break;
            graphics.text(font, line, 8, nameY, 0xFF70D8E8, false);
            nameY += 9;
        }
    }
    @Override
    public void getTooltip(ITooltipBuilder tooltip, AssemblyJeiGuide recipe,
                           mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        if (mouseX >= 122 && mouseX < 138 && mouseY >= 100 && mouseY < 116
            && !recipe.output().is(ModItems.DRONE_CARD.get())) {
            tooltip.add(Component.translatable("screen.fpv.assembly.hint.card"));
            return;
        }
        if (emptySlotTooltip(tooltip, mouseX, mouseY, 86, 20, recipe.batteries().isEmpty(),
                Component.translatable("screen.fpv.assembly.part.battery"))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 86, 60, recipe.cameras().isEmpty(),
                Component.translatable("screen.fpv.assembly.part.camera"))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 122, 60, recipe.flightControllers().isEmpty(),
                Component.translatable("screen.fpv.assembly.hint.controller"))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 50, 60, recipe.videoTransmitters().isEmpty(),
                Component.translatable("screen.fpv.assembly.hint.video_transmitter"))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 122, 40, recipe.optionalModules().isEmpty(),
                Component.translatable("screen.fpv.assembly.hint.module"))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 50, 40, recipe.optionalFirmware().isEmpty(),
                Component.translatable("screen.fpv.assembly.hint.firmware"))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 50, 80, recipe.motors().isEmpty(),
                Component.translatable("screen.fpv.assembly.part.motor", recipe.motorCount()))
            || emptySlotTooltip(tooltip, mouseX, mouseY, 122, 80, recipe.propellers().isEmpty(),
                Component.translatable("screen.fpv.assembly.part.propeller", recipe.motorCount()))) {
            return;
        }
        int cardCount = 0;
        for (ItemStack extra : recipe.extraParts()) {
            if (DroneCardItem.isCard(extra)) cardCount++;
        }
        for (int i = cardCount; i < 4; i++) {
            int x = 36 + i * 24;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= 120 && mouseY < 136) {
                tooltip.add(Component.translatable("screen.fpv.assembly.part.bay", i + 1));
                return;
            }
        }
        boolean hasWarhead = recipe.extraParts().stream().anyMatch(extra -> WarheadItem.typeOf(extra) != null);
        emptySlotTooltip(tooltip, mouseX, mouseY, 140, 120, !hasWarhead,
            Component.translatable("jei.fpv.assembly.warhead.short"));
    }

    private static boolean emptySlotTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY,
                                            int x, int y, boolean empty, Component label) {
        if (empty && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            tooltip.add(label);
            return true;
        }
        return false;
    }
    private static void input(IRecipeLayoutBuilder builder, int x, int y, ItemStack stack) {
        input(builder, x, y, stack, stack.getHoverName());
    }

    private static void input(IRecipeLayoutBuilder builder, int x, int y, ItemStack stack, Component tooltip) {
        builder.addInputSlot(x, y).addItemStack(stack)
            .addRichTooltipCallback((slot, lines) -> lines.add(tooltip));
    }

    private static void alternatives(IRecipeLayoutBuilder builder, int x, int y,
                                    java.util.List<ItemStack> stacks) {
        alternatives(builder, x, y, stacks, stacks.get(0).getHoverName());
    }

    private static void alternatives(IRecipeLayoutBuilder builder, int x, int y,
                                     java.util.List<ItemStack> stacks, Component tooltip) {
        var slot = builder.addInputSlot(x, y);
        if (!stacks.isEmpty()) slot.addItemStacks(stacks);
        slot.addRichTooltipCallback((view, lines) -> lines.add(tooltip));
    }

    private static final class AssemblyStationBackground implements IDrawable {
        @Override public int getWidth() { return WIDTH; }
        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void draw(GuiGraphicsExtractor graphics, int x, int y) {
            graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2B2B33);
            graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF3C3C46);
            graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, 0xFF23232B);
            graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, 0xFF202028);

            drawSlot(graphics, x + 86, y + 20, 0xFF8B8B99);
            drawSlot(graphics, x + 86, y + 40, 0xFF8B8B99);
            drawSlot(graphics, x + 86, y + 60, 0xFF8B8B99);
            drawSlot(graphics, x + 50, y + 40, 0xFF7BE08A);
            drawSlot(graphics, x + 122, y + 40, 0xFFFFB733);
            drawSlot(graphics, x + 50, y + 60, 0xFF9AC8FF);
            drawSlot(graphics, x + 122, y + 60, 0xFFC7A0FF);
            drawSlot(graphics, x + 50, y + 80, 0xFF8B8B99);
            drawSlot(graphics, x + 122, y + 80, 0xFF8B8B99);
            drawSlot(graphics, x + 86, y + 100, 0xFF66CC66);
            drawSlot(graphics, x + 122, y + 100, 0xFFE8C27A);
            for (int i = 0; i < 4; i++) drawSlot(graphics, x + 36 + i * 24, y + 120, 0xFFCC6666);
            drawSlot(graphics, x + 140, y + 120, 0xFFFFB733);
        }
        private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, int accent) {
            graphics.fill(x - 1, y - 1, x + 17, y + 17, dim(accent));
            graphics.fill(x, y, x + 16, y + 16, 0xFF101015);
        }

        private static int dim(int accent) {
            int red = (accent >> 16 & 0xFF) * 2 / 5;
            int green = (accent >> 8 & 0xFF) * 2 / 5;
            int blue = (accent & 0xFF) * 2 / 5;
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }
}
