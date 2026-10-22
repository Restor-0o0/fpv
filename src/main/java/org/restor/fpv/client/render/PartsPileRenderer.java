package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartsPileRenderer implements BlockEntityRenderer<PartsPileBlockEntity, PartsPileRenderer.State> {
    public static class State extends BlockEntityRenderState {
        PartsPileBlockEntity pile;
    }

    private static final float USABLE = 0.9375f;

    private static final float SLAT = 0.5f / 16f;
    private static final float BEARER = 0.45f / 16f;
    private static final float BOARD = 1.1f / 16f;
    private static final float BOARD_PITCH = 1.6f / 16f;
    private static final float LIP = 1.0f / 16f;
    private static final BlockState SLAT_STATE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final net.minecraft.client.renderer.block.model.BlockDisplayContext SLAT_CONTEXT =
        net.minecraft.client.renderer.block.model.BlockDisplayContext.create();
    private static final net.minecraft.client.renderer.block.BlockModelRenderState SLAT_MODEL =
        new net.minecraft.client.renderer.block.BlockModelRenderState();

    private static final Map<Item, float[]> BOUNDS = new HashMap<>();

    public PartsPileRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PartsPileBlockEntity pile, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(pile, state, partialTick, cameraPos, crumbling);
        state.pile = pile;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector buffer, CameraRenderState camera) {
        PartsPileBlockEntity pile = state.pile;
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
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

    private static void renderLegs(PoseStack pose, SubmitNodeCollector buffer, int light, int overlay, float drop) {
        float post = SLAT * 1.4f;
        float lo = 0.5f - USABLE / 2f - post / 2f;
        float hi = 0.5f + USABLE / 2f - post / 2f;
        for (float x : new float[]{lo, hi}) {
            for (float z : new float[]{lo, hi}) slat(pose, buffer, light, overlay, x, -drop, z, post, drop, post);
        }
    }

    private static void renderSection(PartsPileBlockEntity pile, ItemStack stored, float base, boolean coveredAbove,
                                      float facing, PoseStack pose, SubmitNodeCollector buffer, int light, int overlay) {
        ItemStack one = stored.copyWithCount(1);
        ItemStackRenderState model = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(model, one, ItemDisplayContext.NONE, pile.getLevel(), null, 0);
        float[] box = BOUNDS.computeIfAbsent(one.getItem(), item -> measure(model));

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
            model.submit(pose, buffer, light, OverlayTexture.NO_OVERLAY, 0);
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

    private static void renderTray(PoseStack pose, SubmitNodeCollector buffer, int light, int overlay,
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

    private static void slat(PoseStack pose, SubmitNodeCollector buffer, int light, int overlay,
                             float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        if (SLAT_MODEL.isEmpty()) {
            net.minecraft.client.Minecraft.getInstance().getBlockModelResolver().update(SLAT_MODEL, SLAT_STATE, SLAT_CONTEXT);
        }
        SLAT_MODEL.submit(pose, buffer, light, overlay, 0);
        pose.popPose();
    }

    private static float[] measure(ItemStackRenderState model) {
        AABB b = model.getModelBoundingBox();
        if (b.getXsize() <= 0 && b.getYsize() <= 0 && b.getZsize() <= 0) return new float[]{0f, 0f, 0f, 1f, 1f, 1f};
        return new float[]{(float) b.minX + 0.5f, (float) b.minY + 0.5f, (float) b.minZ + 0.5f,
            (float) b.maxX + 0.5f, (float) b.maxY + 0.5f, (float) b.maxZ + 0.5f};
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PartsPileBlockEntity be) {
        return be.getRenderBoundingBox();
    }
}
