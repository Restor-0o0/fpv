package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import org.restor.fpv.assembly.DroneVisual;
import org.restor.fpv.assembly.FrameSpec;
import org.restor.fpv.assembly.FrameSpecs;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PartsPileBlockEntity extends BlockEntity {
    public static final int MAX_CAPACITY = 64;
    private static final float SPAN_PX = 15f;

    private final List<ItemStack> sections = new ArrayList<>();

    public PartsPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTS_PILE.get(), pos, state);
    }

    public static boolean isStorable(ItemStack stack) {
        return stack.getItem() instanceof ComponentItem
            || stack.getItem() instanceof org.restor.fpv.warhead.WarheadItem
            || stack.getItem() instanceof org.restor.fpv.item.DropChargeItem
            || stack.getItem() instanceof org.restor.fpv.item.MiniRocketItem;
    }

    /** Bottom to top; each section is one kind of part on its own racks. */
    public List<ItemStack> sections() {
        return Collections.unmodifiableList(sections);
    }

    public int count() {
        int total = 0;
        for (ItemStack s : sections) total += s.getCount();
        return total;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public static int layersOf(ItemStack section) {
        int perLayer = layoutFor(section).perLayer();
        return Math.max(1, (section.getCount() + perLayer - 1) / perLayer);
    }

    public static float heightOf(ItemStack section) {
        return layersOf(section) * layoutFor(section).step();
    }

    private float heightOf(int from, int to) {
        float h = 0f;
        for (int i = from; i < to; i++) h += heightOf(sections.get(i));
        return h;
    }

    public float pileHeight() {
        return Math.min(1f, heightOf(0, sections.size()));
    }

    /** Height of the floor the topmost layer lies on. */
    public float topFloor() {
        if (sections.isEmpty()) return 0f;
        return heightOf(0, sections.size()) - layoutFor(top()).step() + Layout.RACK_HEIGHT;
    }

    private ItemStack top() {
        return sections.isEmpty() ? ItemStack.EMPTY : sections.get(sections.size() - 1);
    }

    /**
     * How one component kind lies on the block. {@code sizePx} is the footprint the part is
     * drawn at in model pixels and {@code itemHeight} the room one layer of it takes, in blocks.
     * Every layer rests on its own rack of slats, and layers keep stacking until the block is full.
     */
    public record Layout(int cols, int rows, float sizePx, float itemHeight, float yawStep, float layerYaw) {
        public static final float RACK_HEIGHT = 0.75f / 16f;

        public int perLayer() {
            return cols * rows;
        }

        public float step() {
            return itemHeight + RACK_HEIGHT;
        }
    }

    public static Layout layoutFor(ItemStack stack) {
        if (stack.getItem() instanceof org.restor.fpv.warhead.WarheadItem w) {
            return grid(Mth.clamp(3.0f + w.getType().mass() * 0.45f, 3.4f, 7.4f), 0.62f, 0f, 0f);
        }
        if (stack.getItem() instanceof org.restor.fpv.item.DropChargeItem) return grid(4.0f, 1.85f, 0f, 0f);
        if (stack.getItem() instanceof org.restor.fpv.item.MiniRocketItem) return grid(3.0f, 0.5f, 22f, 0f);
        if (!(stack.getItem() instanceof ComponentItem item)) return grid(6f, 0.5f, 0f, 0f);
        ComponentStats s = item.getStats();
        return switch (s.type()) {
            case FRAME -> grid(frameSpanPx(item), frameThickness(item), 0f, 0f);
            case MOTOR -> grid(1.5f + s.motorDiameter() * 0.075f, 0.9f, 0f, 0f);
            case BATTERY -> grid(1.8f + s.batteryCells() * 0.4f + s.batteryCapacity() * 0.0004f, 0.55f, 0f, 0f);
            case PROPELLER -> grid(1.6f + s.propDiameter() * 0.75f, 0.2f, 31f, 0f);
            case CAMERA -> grid(2.8f, 0.6f, 0f, 0f);
            case VTX -> grid(3.0f, 0.36f, 0f, 0f);
            case FLIGHT_CONTROLLER -> grid(2.8f, 0.42f, 0f, 0f);
            case FIRMWARE -> grid(2.4f, 0.14f, 0f, 0f);
            case MODULE -> grid(2.6f + s.weight() * 0.02f, 0.7f, 0f, 0f);
        };
    }

    private static float frameSpanPx(ComponentItem item) {
        FrameSpec spec = FrameSpecs.byItem(item);
        float span = spec != null ? DroneVisual.of(spec).hitboxWidth() * 16f * 0.85f : item.getStats().frameSize() * 1.3f;
        return Mth.clamp(span, 3.4f, 15f);
    }

    private static float frameThickness(ComponentItem item) {
        FrameSpec spec = FrameSpecs.byItem(item);
        if (spec == null) return 0.3f;
        if (spec.isFixedWing()) return 0.45f;
        if (spec == FrameSpecs.FRAME_INTERCEPTOR) return 1.0f;
        return spec.motorCount() > 4 ? 0.45f : 0.3f;
    }

    private static Layout grid(float sizePx, float thickness, float yawStep, float layerYaw) {
        int cols = Mth.clamp((int) (SPAN_PX / (sizePx * 1.10f)), 1, 4);
        return new Layout(cols, cols, sizePx, sizePx * thickness / 16f, yawStep, layerYaw);
    }

    /** Room in section {@code index}, or in a new section on top when {@code index} is the section count. */
    private int roomIn(int index, ItemStack stack) {
        int free = MAX_CAPACITY - count();
        if (free <= 0) return 0;
        boolean existing = index < sections.size();
        float others = heightOf(0, sections.size()) - (existing ? heightOf(sections.get(index)) : 0f);
        Layout l = layoutFor(stack);
        int layers = (int) ((1f - others + 1.0e-4f) / l.step());
        if (sections.isEmpty()) layers = Math.max(1, layers);
        int already = existing ? sections.get(index).getCount() : 0;
        return Math.max(0, Math.min(free, layers * l.perLayer() - already));
    }

    private int target(ItemStack stack) {
        if (!isStorable(stack)) return -1;
        for (int i = 0; i < sections.size(); i++) {
            if (ItemStack.isSameItemSameComponents(sections.get(i), stack) && roomIn(i, stack) > 0) return i;
        }
        return roomIn(sections.size(), stack) > 0 ? sections.size() : -1;
    }

    public boolean accepts(ItemStack stack) {
        return target(stack) >= 0;
    }

    public int insert(ItemStack from) {
        return insert(from, from.getCount());
    }

    public int insert(ItemStack from, int max) {
        int index = target(from);
        if (index < 0) return 0;
        int moved = Math.min(Math.min(roomIn(index, from), from.getCount()), max);
        if (moved <= 0) return 0;
        if (index < sections.size()) sections.get(index).grow(moved);
        else sections.add(from.copyWithCount(moved));
        from.shrink(moved);
        sync();
        return moved;
    }

    public ItemStack extract(int max) {
        ItemStack top = top();
        if (top.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = top.split(Math.min(max, top.getCount()));
        if (top.isEmpty()) sections.remove(sections.size() - 1);
        sync();
        return out;
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>(sections);
        sections.clear();
        sync();
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sections.clear();
        if (tag.contains("Item")) sections.add(ItemStack.parseOptional(registries, tag.getCompound("Item")));
        for (Tag entry : tag.getList("Items", Tag.TAG_COMPOUND)) sections.add(ItemStack.parseOptional(registries, (CompoundTag) entry));
        sections.removeIf(ItemStack::isEmpty);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (ItemStack s : sections) list.add(s.saveOptional(registries));
        if (!list.isEmpty()) tag.put("Items", list);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(0, 1, 0);
    }
}
