package org.restor.fpv.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.Fpv;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.DroneCardItem;
import org.restor.fpv.assembly.FrameSpecs;
import org.restor.fpv.assembly.DronePresets;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.warhead.WarheadType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@JeiPlugin
public final class AssemblyJeiPlugin implements IModPlugin {
    public static final RecipeType<AssemblyJeiGuide> ASSEMBLY = new RecipeType<>(
        Identifier.fromNamespaceAndPath(Fpv.MODID, "assembly"), AssemblyJeiGuide.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Fpv.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AssemblyJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        ISubtypeInterpreter<ItemStack> assemblySubtype = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack ingredient, UidContext context) {
                CustomData data = ingredient.get(DataComponents.CUSTOM_DATA);
                return data != null && data.contains("Assembly") ? data.copyTag().toString() : null;
            }

            };
        registration.registerSubtypeInterpreter(ModItems.MODULAR_DRONE.get(), assemblySubtype);
        registration.registerSubtypeInterpreter(ModItems.MODULAR_PLANE.get(), assemblySubtype);
        registration.registerSubtypeInterpreter(ModItems.DRONE_CARD.get(), assemblySubtype);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Item> frames = items(ModComponents.FRAME_5INCH, ModComponents.FRAME_7INCH,
            ModComponents.FRAME_TINY_WHOOP, ModComponents.FRAME_CINEWHOOP, ModComponents.FRAME_INTERCEPTOR,
            ModComponents.FRAME_HEXA_6INCH, ModComponents.FRAME_OCTO_8INCH,
            ModComponents.PLANE_FRAME_TRAINER, ModComponents.PLANE_FRAME_DELTA,
            ModComponents.PLANE_FRAME_GLIDER, ModComponents.PLANE_FRAME_STRIKE,
            ModComponents.PLANE_FRAME_CARGO, ModComponents.PLANE_FRAME_WING,
            ModComponents.PLANE_FRAME_SCOUT, ModComponents.PLANE_FRAME_KAMIKAZE,
            ModComponents.PLANE_FRAME_CARRIER, ModComponents.PLANE_FRAME_LOITER);
        List<Item> batteries = items(ModComponents.BATTERY_1S_300, ModComponents.BATTERY_4S_1300,
            ModComponents.BATTERY_6S_1100, ModComponents.BATTERY_4S_650, ModComponents.BATTERY_6S_5000);
        List<Item> cameras = items(ModComponents.CAMERA_STANDARD, ModComponents.CAMERA_WIDE);
        List<Item> quadMotors = items(ModComponents.MOTOR_1103_8000, ModComponents.MOTOR_1404_4500,
            ModComponents.MOTOR_2207_2450, ModComponents.MOTOR_2306_1700,
            ModComponents.MOTOR_2806_1300, ModComponents.MOTOR_3115_900);
        List<Item> planeMotors = items(ModComponents.MOTOR_2814_1100,
            ModComponents.MOTOR_3548_900, ModComponents.MOTOR_4250_650);
        List<Item> quadProps = items(ModComponents.PROP_3X2X3, ModComponents.PROP_5X4X2,
            ModComponents.PROP_5X4X3, ModComponents.PROP_65MM);
        List<Item> planeProps = items(ModComponents.PROP_8X6, ModComponents.PROP_10X5, ModComponents.PROP_12X6);
        List<Item> modules = items(ModComponents.MODULE_PAYLOAD_BAY, ModComponents.MODULE_WARHEAD,
            ModComponents.MODULE_DART_GUN, ModComponents.MODULE_STORAGE,
            ModComponents.MODULE_TARGETING, ModComponents.MODULE_ROCKET_POD);
        List<Item> firmware = items(ModComponents.FIRMWARE_COMPANION, ModComponents.FIRMWARE_SCOUT,
            ModComponents.FIRMWARE_GUARDIAN, ModComponents.FIRMWARE_TRADER);
        List<Item> controllers = items(ModComponents.FC_F411, ModComponents.FC_H7);
        List<Item> transmitters = items(ModComponents.VTX_25MW, ModComponents.VTX_200MW, ModComponents.VTX_800MW);

        List<AssemblyJeiGuide> guides = new ArrayList<>();
        for (Item frameItem : frames) {
            ItemStack frame = new ItemStack(frameItem);
            var spec = FrameSpecs.byItem(frameItem);
            if (spec == null) continue;
            boolean plane = DroneAssembly.isPlaneFrame(frame);
            guides.add(new AssemblyJeiGuide(frame,
                new ItemStack(plane ? ModItems.MODULAR_PLANE.get() : ModItems.MODULAR_DRONE.get()),
                compatible(frame, batteries), compatible(frame, cameras), stacks(controllers), stacks(transmitters),
                compatible(frame, plane ? planeMotors : quadMotors),
                compatible(frame, plane ? planeProps : quadProps),
                compatible(frame, modules), compatible(frame, firmware), spec.motorCount()));
        }
        registration.addRecipes(ASSEMBLY, guides);

        List<AssemblyJeiGuide> presets = new ArrayList<>();
        for (ItemStack preset : DronePresets.all()) {
            CustomData customData = preset.get(DataComponents.CUSTOM_DATA);
            if (customData == null || !customData.contains("Assembly")) continue;
            CompoundTag tag = customData.copyTag();
            DroneAssembly assembly = DroneAssembly.load(tag.getCompound("Assembly").orElse(new CompoundTag()));
            var spec = assembly.frameSpec();
            if (spec == null) continue;

            List<ItemStack> extras = new ArrayList<>();
            if (tag.contains("Warhead")) {
                WarheadType warhead = WarheadType.byId(tag.getString("Warhead").orElse(""));
                var warheadItem = ModItems.WARHEADS.get(warhead);
                if (warheadItem != null) extras.add(new ItemStack(warheadItem.get()));
            }
            for (ItemStack card : assembly.bay) {
                if (!card.isEmpty()) extras.add(card.copy());
            }

            presets.add(new AssemblyJeiGuide(assembly.frame.copy(), preset.copy(),
                one(assembly.battery), one(assembly.camera), one(assembly.fc), one(assembly.vtx),
                one(first(assembly.motors)), one(first(assembly.props)), one(assembly.module),
                one(assembly.firmware), spec.motorCount(), extras, preset.getHoverName(), true));
        }
        registration.addRecipes(ASSEMBLY, presets);

        List<AssemblyJeiGuide> cards = new ArrayList<>();
        for (ItemStack card : DronePresets.cards()) {
            CompoundTag tag = DroneCardItem.data(card);
            if (tag == null) continue;
            DroneAssembly assembly = DroneAssembly.load(tag.getCompound("Assembly").orElse(new CompoundTag()));
            var spec = assembly.frameSpec();
            if (spec == null) continue;

            List<ItemStack> extras = new ArrayList<>();
            if (tag.contains("Warhead")) {
                WarheadType warhead = WarheadType.byId(tag.getString("Warhead").orElse(""));
                var warheadItem = ModItems.WARHEADS.get(warhead);
                if (warheadItem != null) extras.add(new ItemStack(warheadItem.get()));
            }
            for (ItemStack bayCard : assembly.bay) {
                if (!bayCard.isEmpty()) extras.add(bayCard.copy());
            }

            cards.add(new AssemblyJeiGuide(assembly.frame.copy(), card.copy(),
                one(assembly.battery), one(assembly.camera), one(assembly.fc), one(assembly.vtx),
                one(first(assembly.motors)), one(first(assembly.props)), one(assembly.module),
                one(assembly.firmware), spec.motorCount(), extras, card.getHoverName(), true));
        }
        registration.addRecipes(ASSEMBLY, cards);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.ASSEMBLY_STATION.get().asItem().getDefaultInstance(), ASSEMBLY);
    }

    @SafeVarargs
    private static List<Item> items(Supplier<? extends Item>... suppliers) {
        return java.util.Arrays.stream(suppliers).map(supplier -> (Item) supplier.get()).toList();
    }

    private static List<ItemStack> compatible(ItemStack frame, List<Item> items) {
        return items.stream().filter(item -> DroneAssembly.isCompatible(frame, new ItemStack(item)))
            .map(ItemStack::new).toList();
    }

    private static List<ItemStack> stacks(List<Item> items) {
        return items.stream().map(ItemStack::new).toList();
    }

    private static ItemStack first(ItemStack[] stacks) {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) return stack;
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> one(ItemStack stack) {
        return stack.isEmpty() ? List.of() : List.of(stack.copy());
    }
}
