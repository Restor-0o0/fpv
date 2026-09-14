package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.restor.fpv.block.PartsPileBlock;
import org.restor.fpv.block.entity.PartsPileBlockEntity;
import org.restor.fpv.block.entity.PartsPileBlockEntity.Layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartsPileRenderer implements BlockEntityRenderer<PartsPileBlockEntity> {
    private static final float USABLE = 0.9375f;

    private static final Map<BakedModel, float[]> BOUNDS = new HashMap<>();

    public PartsPileRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public AABB getRenderBoundingBox(PartsPileBlockEntity pile) {
        return new AABB(pile.getBlockPos()).expandTowards(0, 1, 0);
    }

    @Override
    public void render(PartsPileBlockEntity pile, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        ItemStack stored = pile.stored();
        if (stored.isEmpty()) return;

        ItemStack one = stored.copyWithCount(1);
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(one, pile.getLevel(), null, 0);
        float[] box = BOUNDS.computeIfAbsent(model, PartsPileRenderer::measure);

        Layout layout = pile.layout();
        float width = box[3] - box[0];
        float height = box[4] - box[1];
        float depth = box[5] - box[2];
        // Plane props are modelled upright on their motor, so they get tipped onto their face.
        boolean tipped = depth < height * 0.55f && depth < width * 0.55f;
        float footprint = Math.max(width, tipped ? height : depth);
        float scale = footprint < 1.0e-4f ? 1f : layout.sizePx() / 16f / footprint;
        float cellX = USABLE / layout.cols();
        float cellZ = USABLE / layout.rows();
        float facing = pile.getBlockState().getValue(PartsPileBlock.FACING).toYRot();
        float centerX = 0.5f * (box[0] + box[3]);
        float centerY = 0.5f * (box[1] + box[4]);
        float centerZ = 0.5f * (box[2] + box[5]);

        int count = Math.min(stored.getCount(), layout.capacity());
        int layers = (count + layout.perLayer() - 1) / layout.perLayer();
        float step = Math.max(layout.layerHeight(), (tipped ? depth : height) * scale * 1.04f);
        if (layers * step > 0.98f) {
            float shrink = 0.98f / (layers * step);
            scale *= shrink;
            step *= shrink;
        }

        for (int i = 0; i < count; i++) {
            int slot = i % layout.perLayer();
            int layer = i / layout.perLayer();
            float x = 0.5f + (slot % layout.cols() - (layout.cols() - 1) * 0.5f) * cellX;
            float z = 0.5f + (slot / layout.cols() - (layout.rows() - 1) * 0.5f) * cellZ;
            float yaw = i * layout.yawStep() + layer * layout.layerYaw();

            pose.pushPose();
            pose.translate(0.5f, 0.002f + layer * step, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(facing));
            pose.translate(x - 0.5f, 0f, z - 0.5f);
            if (yaw != 0f) pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.scale(scale, scale, scale);
            if (tipped) {
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                pose.translate(0.5f - centerX, 0.5f - centerY, 0.5f - box[5]);
            } else {
                pose.translate(0.5f - centerX, 0.5f - box[1], 0.5f - centerZ);
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(one, ItemDisplayContext.NONE,
                light, OverlayTexture.NO_OVERLAY, pose, buffer, pile.getLevel(), 0);
            pose.popPose();
        }
    }

    /** Model-space AABB read straight off the baked quads, so every part centres and sits flat. */
    private static float[] measure(BakedModel model) {
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random));
        for (Direction dir : Direction.values()) quads.addAll(model.getQuads(null, dir, random));
        if (quads.isEmpty()) return new float[]{0f, 0f, 0f, 1f, 1f, 1f};

        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                for (int axis = 0; axis < 3; axis++) {
                    float value = Float.intBitsToFloat(data[vertex * stride + axis]);
                    box[axis] = Math.min(box[axis], value);
                    box[axis + 3] = Math.max(box[axis + 3], value);
                }
            }
        }
        return box;
    }
}
