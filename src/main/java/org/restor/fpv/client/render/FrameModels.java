package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.restor.fpv.Config;
import org.restor.fpv.Fpv;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = Fpv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrameModels {
    private static final Map<ResourceLocation, ResourceLocation> SLIM = Stream.of(
            "frame_tiny_whoop", "frame_cinewhoop", "frame_5inch", "frame_7inch")
        .collect(Collectors.toMap(n -> new ResourceLocation(Fpv.MODID, n),
            n -> new ResourceLocation(Fpv.MODID, "drone/" + n + "_slim")));

    private FrameModels() {}

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        SLIM.values().forEach(event::register);
    }

    public static void render(ItemStack frame, PoseStack poseStack, MultiBufferSource buffer, int light, float scale) {
        if (frame.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        BakedModel slim = slimModel(frame);
        if (slim != null) {
            mc.getItemRenderer().render(frame, ItemDisplayContext.NONE, false, poseStack, buffer, light,
                OverlayTexture.NO_OVERLAY, slim);
        } else {
            mc.getItemRenderer().renderStatic(frame, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, null, 0);
        }
        poseStack.popPose();
    }

    private static BakedModel slimModel(ItemStack frame) {
        if (!Config.SLIM_FRAMES.get()) return null;
        ResourceLocation id = SLIM.get(ForgeRegistries.ITEMS.getKey(frame.getItem()));
        if (id == null) return null;
        var models = Minecraft.getInstance().getModelManager();
        BakedModel baked = models.getModel(id);
        return baked == models.getMissingModel() ? null : baked;
    }
}
