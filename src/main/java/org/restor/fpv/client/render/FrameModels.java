package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;

public final class FrameModels {
    private FrameModels() {}

    public static void render(ItemStack frame, PoseStack poseStack, SubmitBuffers buffer, int light, float scale) {
        if (frame.isEmpty()) return;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        buffer.item(frame, poseStack, light);
        poseStack.popPose();
    }
}
