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
        input(builder, 86, 40, recipe.frame());
        alternatives(builder, 86, 20, recipe.batteries());
        alternatives(builder, 86, 60, recipe.cameras());
        alternatives(builder, 122, 60, recipe.flightControllers());
        alternatives(builder, 50, 60, recipe.videoTransmitters());
        alternatives(builder, 122, 40, recipe.optionalModules(), recipe.preset()
            ? Component.translatable("jei.fpv.assembly.slot.preset")
            : Component.translatable("jei.fpv.assembly.slot.optional"));
        alternatives(builder, 50, 40, recipe.optionalFirmware(), recipe.preset()
            ? Component.translatable("jei.fpv.assembly.slot.preset")
            : Component.translatable("jei.fpv.assembly.slot.optional"));

        alternatives(builder, 50, 80, recipe.motors(),
            Component.translatable("jei.fpv.assembly.slot.motors", recipe.motorCount()));
        alternatives(builder, 122, 80, recipe.propellers(),
            Component.translatable("jei.fpv.assembly.slot.propellers", recipe.motorCount()));

        boolean cardOutput = recipe.output().is(ModItems.DRONE_CARD.get());
        builder.addOutputSlot(cardOutput ? 122 : 86, 100).addItemStack(recipe.output());

        int bayIndex = 0;
        for (ItemStack extra : recipe.extraParts()) {
            if (DroneCardItem.isCard(extra) && bayIndex < 4) {
                input(builder, 36 + bayIndex * 24, 120, extra,
                    Component.translatable("jei.fpv.assembly.slot.preset"));
                bayIndex++;
            } else if (WarheadItem.typeOf(extra) != null) {
                input(builder, 140, 120, extra, Component.translatable("jei.fpv.assembly.warhead"));
            }
        }
    }

    @Override
    public void draw(AssemblyJeiGuide recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        background.draw(graphics, 0, 0);
        var font = Minecraft.getInstance().font;
        int nameY = 4;
        for (var line : font.split(recipe.displayName(), 174)) {
            if (nameY > 25) break;
            graphics.drawString(font, line, 8, nameY, 0xFF70D8E8, false);
            nameY += 9;
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
