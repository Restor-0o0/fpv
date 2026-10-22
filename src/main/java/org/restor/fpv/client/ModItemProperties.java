package org.restor.fpv.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.Config;
import org.restor.fpv.Fpv;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.item.ModItems;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class ModItemProperties {
    private ModItemProperties() {}

    @SubscribeEvent
    public static void onRegisterRangeSelect(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Fpv.MODID, "frame"), FrameProperty.CODEC);
    }

    @SubscribeEvent
    public static void onRegisterConditional(RegisterConditionalItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(Fpv.MODID, "slim_frame"), SlimFrameProperty.CODEC);
    }

    public record FrameProperty() implements RangeSelectItemModelProperty {
        static final MapCodec<FrameProperty> CODEC = MapCodec.unit(new FrameProperty());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            if (stack.is(ModItems.MODULAR_PLANE.get())) return planeFrameIndex(stack);
            return frameIndex(stack);
        }

        @Override
        public MapCodec<FrameProperty> type() {
            return CODEC;
        }
    }

    public record SlimFrameProperty() implements ConditionalItemModelProperty {
        static final MapCodec<SlimFrameProperty> CODEC = MapCodec.unit(new SlimFrameProperty());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed,
                           ItemDisplayContext context) {
            return context == ItemDisplayContext.NONE && Config.SLIM_FRAMES.get();
        }

        @Override
        public MapCodec<SlimFrameProperty> type() {
            return CODEC;
        }
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
        return DroneAssembly.load(data.copyTag().getCompoundOrEmpty("Assembly")).frame.getItem();
    }

    private static float frameIndex(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Assembly")) return 0f;
        Item frame = DroneAssembly.load(data.copyTag().getCompoundOrEmpty("Assembly")).frame.getItem();
        if (frame == ModComponents.FRAME_TINY_WHOOP.get()) return 1f;
        if (frame == ModComponents.FRAME_CINEWHOOP.get()) return 2f;
        if (frame == ModComponents.FRAME_7INCH.get()) return 3f;
        if (frame == ModComponents.FRAME_HEXA_6INCH.get()) return 4f;
        if (frame == ModComponents.FRAME_OCTO_8INCH.get()) return 5f;
        if (frame == ModComponents.FRAME_INTERCEPTOR.get()) return 6f;
        return 0f;
    }
}
