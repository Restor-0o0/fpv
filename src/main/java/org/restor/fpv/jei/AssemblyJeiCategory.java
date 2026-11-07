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
    private static final int WIDTH = 164;
    private static final int HEIGHT = 132;
    private final IDrawable background = new AssemblyStationBackground();

    public AssemblyJeiCategory(IGuiHelper guiHelper) {
        super(AssemblyJeiPlugin.ASSEMBLY, Component.translatable("block.fpv.assembly_station"),
            guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                ModBlocks.ASSEMBLY_STATION.get().asItem().getDefaultInstance()), WIDTH, HEIGHT);
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

        @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AssemblyJeiGuide recipe, IFocusGroup focuses) {
        input(builder, 28, 26, recipe.frame());
        alternatives(builder, 28, 8, recipe.batteries());
        alternatives(builder, 28, 44, recipe.cameras());
        alternatives(builder, 46, 44, recipe.flightControllers());
        alternatives(builder, 10, 44, recipe.videoTransmitters());
        alternatives(builder, 46, 26, recipe.optionalModules(), recipe.preset()
            ? Component.translatable("jei.fpv.assembly.slot.preset")
            : Component.translatable("jei.fpv.assembly.slot.optional"));
        alternatives(builder, 10, 26, recipe.optionalFirmware(), recipe.preset()
            ? Component.translatable("jei.fpv.assembly.slot.preset")
            : Component.translatable("jei.fpv.assembly.slot.optional"));

        alternatives(builder, 10, 62, recipe.motors(),
            Component.translatable("jei.fpv.assembly.slot.motors", recipe.motorCount()));
        alternatives(builder, 46, 62, recipe.propellers(),
            Component.translatable("jei.fpv.assembly.slot.propellers", recipe.motorCount()));

        boolean cardOutput = recipe.output().is(ModItems.DRONE_CARD.get());
        builder.addOutputSlot(cardOutput ? 76 : 28, 86).addItemStack(recipe.output());

        int bayIndex = 0;
        for (ItemStack extra : recipe.extraParts()) {
            if (DroneCardItem.isCard(extra) && bayIndex < 4) {
                input(builder, 4 + bayIndex * 18, 110, extra,
                    Component.translatable("jei.fpv.assembly.slot.preset"));
                bayIndex++;
            } else if (WarheadItem.typeOf(extra) != null) {
                input(builder, 76, 110, extra, Component.translatable("jei.fpv.assembly.warhead"));
            }
        }
    }

    @Override
    public void draw(AssemblyJeiGuide recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        
        var font = Minecraft.getInstance().font;
        int y = 8;
        for (var line : font.split(Component.translatable("screen.fpv.assembly.diagnostics"), 58)) {
            if (y > 17) break;
            graphics.drawString(font, line, 98, y, 0xFFE0E0E8, false);
            y += 9;
        }
        int nameY = 20;
        for (var line : font.split(recipe.displayName(), 58)) {
            if (nameY > 48) break;
            graphics.drawString(font, line, 98, nameY, 0xFF70D8E8, false);
            nameY += 9;
        }
        int countY = 55;
        for (var line : font.split(Component.translatable("jei.fpv.assembly.motor_count", recipe.motorCount()), 58)) {
            if (countY > 68) break;
            graphics.drawString(font, line, 98, countY, 0xFFB8B8C4, false);
            countY += 9;
        }
        int propY = 71;
        for (var line : font.split(Component.translatable("jei.fpv.assembly.propeller_count", recipe.motorCount()), 58)) {
            if (propY > 84) break;
            graphics.drawString(font, line, 98, propY, 0xFFB8B8C4, false);
            propY += 9;
        }
        int hintY = 92;
        for (var line : font.split(Component.translatable("jei.fpv.assembly.station_hint"), 58)) {
            if (hintY > 123) break;
            graphics.drawString(font, line, 98, hintY, 0xFFAFC8D8, false);
            hintY += 9;
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
            graphics.fill(x + 4, y + 4, x + 94, y + HEIGHT - 4, 0xFF2E2E38);
            graphics.fill(x + 98, y + 4, x + WIDTH - 4, y + HEIGHT - 4, 0xFF191921);

            drawSlot(graphics, x + 28, y + 8, 0xFF8B8B99);
            drawSlot(graphics, x + 28, y + 26, 0xFF8B8B99);
            drawSlot(graphics, x + 28, y + 44, 0xFF8B8B99);
            drawSlot(graphics, x + 10, y + 26, 0xFF7BE08A);
            drawSlot(graphics, x + 46, y + 26, 0xFFFFB733);
            drawSlot(graphics, x + 10, y + 44, 0xFF9AC8FF);
            drawSlot(graphics, x + 46, y + 44, 0xFFC7A0FF);
            drawSlot(graphics, x + 10, y + 62, 0xFF8B8B99);
            drawSlot(graphics, x + 46, y + 62, 0xFF8B8B99);
            drawSlot(graphics, x + 28, y + 86, 0xFF66CC66);
            drawSlot(graphics, x + 76, y + 86, 0xFFE8C27A);
            for (int i = 0; i < 4; i++) drawSlot(graphics, x + 4 + i * 18, y + 110, 0xFFCC6666);
            drawSlot(graphics, x + 76, y + 110, 0xFFFFB733);
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
