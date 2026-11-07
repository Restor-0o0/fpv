package org.restor.fpv.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.assembly.DroneCardItem;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.WarheadItem;

/** JEI view laid out to match the real Drone Assembly Station screen. */
public final class AssemblyJeiCategory extends AbstractRecipeCategory<AssemblyJeiGuide> {
    private static final int WIDTH = 250;
    private static final int HEIGHT = 150;
    private final IDrawable background = new AssemblyStationBackground();

    public AssemblyJeiCategory(IGuiHelper guiHelper) {
        super(AssemblyJeiPlugin.ASSEMBLY, Component.translatable("block.fpv.assembly_station"),
            guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                ModBlocks.ASSEMBLY_STATION.get().asItem().getDefaultInstance()), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyJeiGuide recipe, IFocusGroup focuses) {
        input(builder, 80, 55, recipe.frame());
        alternatives(builder, 80, 17, recipe.batteries());
        alternatives(builder, 80, 93, recipe.cameras());
        alternatives(builder, 104, 93, recipe.flightControllers());
        alternatives(builder, 56, 93, recipe.videoTransmitters());
        alternatives(builder, 104, 55, recipe.optionalModules(), recipe.preset()
            ? Component.translatable("jei.fpv.assembly.slot.preset")
            : Component.translatable("jei.fpv.assembly.slot.optional"));
        alternatives(builder, 56, 55, recipe.optionalFirmware(), recipe.preset()
            ? Component.translatable("jei.fpv.assembly.slot.preset")
            : Component.translatable("jei.fpv.assembly.slot.optional"));

        for (int i = 0; i < recipe.motorCount(); i++) {
            int row = i / 2;
            int motorX = i % 2 == 0 ? 128 : 32;
            int propX = i % 2 == 0 ? 152 : 8;
            int y = 17 + row * 25;
            alternatives(builder, motorX, y, recipe.motors(),
                Component.translatable("jei.fpv.assembly.slot.motors", recipe.motorCount()));
            alternatives(builder, propX, y, recipe.propellers(),
                Component.translatable("jei.fpv.assembly.slot.propellers", recipe.motorCount()));
        }

        boolean cardOutput = recipe.output().is(ModItems.DRONE_CARD.get());
        builder.addOutputSlot(cardOutput ? 32 : 80, 119).addItemStack(recipe.output());

        int bayIndex = 0;
        for (ItemStack extra : recipe.extraParts()) {
            if (DroneCardItem.isCard(extra) && bayIndex < 4) {
                input(builder, 104 + bayIndex * 18, 119, extra,
                    Component.translatable("jei.fpv.assembly.slot.preset"));
                bayIndex++;
            } else if (WarheadItem.typeOf(extra) != null) {
                input(builder, 226, 128, extra, Component.translatable("jei.fpv.assembly.warhead"));
            }
        }
    }

    @Override
    public void draw(AssemblyJeiGuide recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
        boolean hasWarhead = false;
        for (ItemStack extra : recipe.extraParts()) {
            if (WarheadItem.typeOf(extra) != null) {
                hasWarhead = true;
            }
        }

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("screen.fpv.assembly.diagnostics"),
            174, 8, 0xFFE0E0E8, false);
        int nameY = 23;
        for (var line : font.split(recipe.displayName(), 68)) {
            if (nameY > 43) break;
            graphics.drawString(font, line, 174, nameY, 0xFF70D8E8, false);
            nameY += 10;
        }
        graphics.drawString(font, Component.translatable("jei.fpv.assembly.motor_count", recipe.motorCount()),
            174, 60, 0xFFB8B8C4, false);
        graphics.drawString(font, Component.translatable("jei.fpv.assembly.propeller_count", recipe.motorCount()),
            174, 72, 0xFFB8B8C4, false);
        int hintY = 92;
        for (var line : font.split(Component.translatable("jei.fpv.assembly.station_hint"), 68)) {
            if (hintY > 122) break;
            graphics.drawString(font, line, 174, hintY, 0xFFAFC8D8, false);
            hintY += 10;
        }
        if (hasWarhead) {
            graphics.drawString(font, Component.translatable("jei.fpv.assembly.warhead.short"),
                174, 132, 0xFFFFC45C, false);
        }
    }

    private static void input(IRecipeLayoutBuilder builder, int x, int y, ItemStack stack) {
        input(builder, x, y, stack, Component.translatable("jei.fpv.assembly.slot.required"));
    }

    private static void input(IRecipeLayoutBuilder builder, int x, int y, ItemStack stack, Component tooltip) {
        builder.addInputSlot(x, y).addItemStack(stack)
            .addRichTooltipCallback((slot, lines) -> lines.add(tooltip));
    }

    private static void alternatives(IRecipeLayoutBuilder builder, int x, int y,
                                    java.util.List<ItemStack> stacks) {
        alternatives(builder, x, y, stacks, Component.translatable("jei.fpv.assembly.slot.required"));
    }

    private static void alternatives(IRecipeLayoutBuilder builder, int x, int y,
                                     java.util.List<ItemStack> stacks, Component tooltip) {
        if (!stacks.isEmpty()) builder.addInputSlot(x, y).addItemStacks(stacks)
            .addRichTooltipCallback((slot, lines) -> lines.add(tooltip));
    }

    private static final class AssemblyStationBackground implements IDrawable {
        @Override public int getWidth() { return WIDTH; }
        @Override public int getHeight() { return HEIGHT; }

        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2B2B33);
            graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF3C3C46);
            graphics.fill(x + 3, y + 3, x + WIDTH - 3, y + HEIGHT - 3, 0xFF23232B);
            graphics.fill(x + 4, y + 4, x + 172, y + HEIGHT - 4, 0xFF2E2E38);
            graphics.fill(x + 176, y + 4, x + WIDTH - 4, y + HEIGHT - 4, 0xFF191921);

            drawSlot(graphics, x + 80, y + 17, 0xFF8B8B99);
            drawSlot(graphics, x + 80, y + 55, 0xFF8B8B99);
            drawSlot(graphics, x + 80, y + 93, 0xFF8B8B99);
            drawSlot(graphics, x + 56, y + 55, 0xFF7BE08A);
            drawSlot(graphics, x + 104, y + 55, 0xFFFFB733);
            drawSlot(graphics, x + 56, y + 93, 0xFF9AC8FF);
            drawSlot(graphics, x + 104, y + 93, 0xFFC7A0FF);
            for (int i = 0; i < 8; i++) {
                int row = i / 2;
                int slotY = y + 17 + row * 25;
                drawSlot(graphics, x + (i % 2 == 0 ? 128 : 32), slotY, 0xFF8B8B99);
                drawSlot(graphics, x + (i % 2 == 0 ? 152 : 8), slotY, 0xFF8B8B99);
            }
            drawSlot(graphics, x + 32, y + 119, 0xFFE8C27A);
            drawSlot(graphics, x + 80, y + 119, 0xFF66CC66);
            for (int i = 0; i < 4; i++) drawSlot(graphics, x + 104 + i * 18, y + 119, 0xFFCC6666);
            drawSlot(graphics, x + 226, y + 128, 0xFFFFB733);
        }

        private static void drawSlot(GuiGraphics graphics, int x, int y, int accent) {
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
