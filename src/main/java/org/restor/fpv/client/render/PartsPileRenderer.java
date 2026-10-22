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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.fpv.assembly.DroneVisual;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.FrameSpecs;
import org.restor.fpv.block.PartsPileBlock;
import org.restor.fpv.block.entity.PartsPileBlockEntity;
import org.restor.fpv.block.entity.PartsPileBlockEntity.Layout;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartsPileRenderer implements BlockEntityRenderer<PartsPileBlockEntity> {
    private static final float USABLE = 0.9375f;

    private static final float SLAT = 0.5f / 16f;
    private static final float BEARER = 0.45f / 16f;
    private static final float BOARD = 1.1f / 16f;
    private static final float BOARD_PITCH = 1.6f / 16f;
    private static final float LIP = 1.0f / 16f;
    private static final BlockState SLAT_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();

    private static final Map<BakedModel, float[]> BOUNDS = new HashMap<>();

    public PartsPileRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PartsPileBlockEntity pile, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        List<ItemStack> sections = pile.sections();
        float facing = pile.getBlockState().getValue(PartsPileBlock.FACING).toYRot();
        float base = 0f;
        for (int i = 0; i < sections.size(); i++) {
            ItemStack section = sections.get(i);
            renderSection(pile, section, base, i < sections.size() - 1, facing, pose, buffer, light, overlay);
            base += PartsPileBlockEntity.heightOf(section);
        }
        if (!sections.isEmpty() && pile.getLevel() != null
            && pile.getLevel().getBlockEntity(pile.getBlockPos().below()) instanceof PartsPileBlockEntity below
            && !below.isEmpty()) {
            float drop = 1f - below.topFloor();
            if (drop > 0.01f) renderLegs(pose, buffer, light, overlay, drop);
        }
    }

    private static void renderLegs(PoseStack pose, MultiBufferSource buffer, int light, int overlay, float drop) {
        float post = SLAT * 1.4f;
        float lo = 0.5f - USABLE / 2f - post / 2f;
        float hi = 0.5f + USABLE / 2f - post / 2f;
        for (float x : new float[]{lo, hi}) {
            for (float z : new float[]{lo, hi}) slat(pose, buffer, light, overlay, x, -drop, z, post, drop, post);
        }
    }

    private static void renderSection(PartsPileBlockEntity pile, ItemStack stored, float base, boolean coveredAbove,
                                      float facing, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        ItemStack one = stored.copyWithCount(1);
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(one, pile.getLevel(), null, 0);
        float[] box = BOUNDS.computeIfAbsent(model, PartsPileRenderer::measure);

        Layout layout = PartsPileBlockEntity.layoutFor(stored);
        float width = box[3] - box[0];
        float height = box[4] - box[1];
        float depth = box[5] - box[2];
        // Plane props are modelled upright on their motor, so they get tipped onto their face.
        boolean tipped = depth < height * 0.55f && depth < width * 0.55f;
        float footprint = Math.max(width, tipped ? height : depth);
        float thickness = tipped ? depth : height;
        float scale = footprint < 1.0e-4f ? 1f : layout.sizePx() / 16f / footprint;
        if (thickness * scale > layout.itemHeight()) scale = layout.itemHeight() / thickness;
        float real = worldScale(stored);
        if (real > 0f) scale = Math.min(scale, real);
        float cellX = USABLE / layout.cols();
        float cellZ = USABLE / layout.rows();
        float centerX = 0.5f * (box[0] + box[3]);
        float centerY = 0.5f * (box[1] + box[4]);
        float centerZ = 0.5f * (box[2] + box[5]);

        int count = stored.getCount();
        int layers = PartsPileBlockEntity.layersOf(stored);
        float step = layout.step();

        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(facing));
        pose.translate(-0.5f, 0f, -0.5f);
        for (int layer = 0; layer < layers; layer++) {
            renderTray(pose, buffer, light, overlay, layout, base + layer * step, coveredAbove || layer < layers - 1);
        }
        pose.popPose();

        for (int i = 0; i < count; i++) {
            int slot = i % layout.perLayer();
            int layer = i / layout.perLayer();
            float x = 0.5f + (slot % layout.cols() - (layout.cols() - 1) * 0.5f) * cellX;
            float z = 0.5f + (slot / layout.cols() - (layout.rows() - 1) * 0.5f) * cellZ;
            float yaw = i * layout.yawStep() + layer * layout.layerYaw();

            pose.pushPose();
            pose.translate(0.5f, base + layer * step + Layout.RACK_HEIGHT + 0.002f, 0.5f);
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

    /** The scale a frame is drawn at on a flying drone, so a pile of 5" frames matches the real thing. */
    private static float worldScale(ItemStack stack) {
        if (!(stack.getItem() instanceof ComponentItem item) || item.getComponentType() != ComponentType.FRAME) return 0f;
        FrameSpec spec = FrameSpecs.byItem(item);
        if (spec == null || spec.isFixedWing()) return 0f;
        DroneVisual v = DroneVisual.of(spec);
        return v.scale() * v.frameScale();
    }

    private static void renderTray(PoseStack pose, MultiBufferSource buffer, int light, int overlay,
                                   Layout layout, float y, boolean supportsNext) {
        float lo = 0.5f - USABLE / 2f;
        float deck = y + BEARER;
        float floor = y + Layout.RACK_HEIGHT;
        float lip = Math.min(LIP, layout.itemHeight() * 0.45f);

        for (int c = 0; c <= layout.cols(); c++) {
            float x = lo + USABLE * c / layout.cols();
            slat(pose, buffer, light, overlay, x - SLAT / 2f, y, lo, SLAT, BEARER, USABLE);
            slat(pose, buffer, light, overlay, x - SLAT / 2f, floor, lo, SLAT, lip, USABLE);
        }
        int boards = Math.round(USABLE / BOARD_PITCH);
        float pitch = USABLE / boards;
        for (int b = 0; b < boards; b++) {
            float z = lo + pitch * b + (pitch - BOARD) / 2f;
            slat(pose, buffer, light, overlay, lo, deck, z, USABLE, Layout.RACK_HEIGHT - BEARER, BOARD);
        }
        for (int r = 0; r <= layout.rows(); r++) {
            float z = lo + USABLE * r / layout.rows();
            slat(pose, buffer, light, overlay, lo, floor, z - SLAT / 2f, USABLE, lip, SLAT);
        }
        if (!supportsNext) return;
        float post = SLAT * 1.4f;
        float height = layout.itemHeight() + BEARER;
        for (int c = 0; c <= layout.cols(); c++) {
            float x = lo + USABLE * c / layout.cols() - post / 2f;
            for (int r = 0; r <= layout.rows(); r++) {
                float z = lo + USABLE * r / layout.rows() - post / 2f;
                slat(pose, buffer, light, overlay, x, floor, z, post, height, post);
            }
        }
    }

    private static void slat(PoseStack pose, MultiBufferSource buffer, int light, int overlay,
                             float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(SLAT_STATE, pose, buffer, light, overlay);
        pose.popPose();
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
