package org.restor.fpv.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SubmitBuffers {
    private static final PoseStack IDENTITY = new PoseStack();

    private final SubmitNodeCollector collector;
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    public SubmitBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    public VertexConsumer getBuffer(RenderType type) {
        return recorders.computeIfAbsent(type, t -> new Recorder());
    }

    public void item(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, int light, Level level) {
        if (stack.isEmpty()) return;
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, 0);
        state.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    public void item(ItemStack stack, PoseStack poseStack, int light) {
        item(stack, ItemDisplayContext.NONE, poseStack, light, null);
    }

    public void flush() {
        for (Map.Entry<RenderType, Recorder> e : recorders.entrySet()) {
            Recorder rec = e.getValue();
            if (rec.vertexCount() == 0) continue;
            collector.submitCustomGeometry(IDENTITY, e.getKey(), (pose, consumer) -> rec.replay(consumer));
        }
        recorders.clear();
    }

    private static final class Recorder implements VertexConsumer {
        private final FloatArrayList floats = new FloatArrayList();
        private final IntArrayList ints = new IntArrayList();
        private int count;

        int vertexCount() { return count; }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            count++;
            floats.add(x); floats.add(y); floats.add(z);
            floats.add(0); floats.add(0);
            floats.add(0); floats.add(1); floats.add(0);
            ints.add(-1); ints.add(0); ints.add(0);
            return this;
        }

        private int fBase() { return (count - 1) * 8; }
        private int iBase() { return (count - 1) * 3; }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            ints.set(iBase(), argb);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            floats.set(fBase() + 3, u);
            floats.set(fBase() + 4, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            ints.set(iBase() + 1, (u & 0xFFFF) | (v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            ints.set(iBase() + 2, (u & 0xFFFF) | (v & 0xFFFF) << 16);
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            floats.set(fBase() + 5, x);
            floats.set(fBase() + 6, y);
            floats.set(fBase() + 7, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        void replay(VertexConsumer out) {
            for (int i = 0; i < count; i++) {
                int f = i * 8, n = i * 3;
                int overlay = ints.getInt(n + 1), light = ints.getInt(n + 2);
                out.addVertex(floats.getFloat(f), floats.getFloat(f + 1), floats.getFloat(f + 2))
                    .setColor(ints.getInt(n))
                    .setUv(floats.getFloat(f + 3), floats.getFloat(f + 4))
                    .setUv1(overlay & 0xFFFF, overlay >>> 16)
                    .setUv2(light & 0xFFFF, light >>> 16)
                    .setNormal(floats.getFloat(f + 5), floats.getFloat(f + 6), floats.getFloat(f + 7));
            }
        }
    }
}
