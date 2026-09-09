package org.restor.fpv.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.Fpv;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.item.ModItems;

public final class ModItemProperties {
    private ModItemProperties() {}

    public static void register() {
        ItemProperties.register(ModItems.MODULAR_DRONE.get(),
            ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "frame"),
            (stack, level, entity, seed) -> frameIndex(stack));
        ItemProperties.register(ModItems.MODULAR_PLANE.get(),
            ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "frame"),
            (stack, level, entity, seed) -> planeFrameIndex(stack));
    }

    private static float planeFrameIndex(ItemStack stack) {
        Item frame = frameItem(stack);
        if (frame == ModComponents.PLANE_FRAME_DELTA.get()) return 1f;
        if (frame == ModComponents.PLANE_FRAME_GLIDER.get()) return 2f;
        if (frame == ModComponents.PLANE_FRAME_STRIKE.get()) return 3f;
        if (frame == ModComponents.PLANE_FRAME_CARGO.get()) return 4f;
        if (frame == ModComponents.PLANE_FRAME_WING.get()) return 5f;
        if (frame == ModComponents.PLANE_FRAME_SCOUT.get()) return 6f;
        if (frame == ModComponents.PLANE_FRAME_KAMIKAZE.get()) return 7f;
        if (frame == ModComponents.PLANE_FRAME_CARRIER.get()) return 8f;
        if (frame == ModComponents.PLANE_FRAME_LOITER.get()) return 9f;
        return 0f;
    }

    private static Item frameItem(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Assembly")) return null;
        return DroneAssembly.load(data.copyTag().getCompound("Assembly")).frame.getItem();
    }

    private static float frameIndex(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Assembly")) return 0f;
        Item frame = DroneAssembly.load(data.copyTag().getCompound("Assembly")).frame.getItem();
        if (frame == ModComponents.FRAME_TINY_WHOOP.get()) return 1f;
        if (frame == ModComponents.FRAME_CINEWHOOP.get()) return 2f;
        if (frame == ModComponents.FRAME_7INCH.get()) return 3f;
        if (frame == ModComponents.FRAME_HEXA_6INCH.get()) return 4f;
        if (frame == ModComponents.FRAME_OCTO_8INCH.get()) return 5f;
        if (frame == ModComponents.FRAME_INTERCEPTOR.get()) return 6f;
        return 0f;
    }
}
