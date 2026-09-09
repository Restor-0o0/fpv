package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;

public class PartsPileBlockEntity extends BlockEntity {
    public static final int MAX_CAPACITY = 16;
    private static final float SPAN_PX = 15f;

    private ItemStack stored = ItemStack.EMPTY;

    public PartsPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTS_PILE.get(), pos, state);
    }

    public static boolean isStorable(ItemStack stack) {
        return stack.getItem() instanceof ComponentItem;
    }

    public ItemStack stored() {
        return stored;
    }

    public int count() {
        return stored.getCount();
    }

    public boolean isEmpty() {
        return stored.isEmpty();
    }

    public Layout layout() {
        return layoutFor(stored);
    }

    public int capacity() {
        return layout().capacity();
    }

    public int layers() {
        Layout l = layout();
        return Math.max(1, (Math.min(stored.getCount(), l.capacity()) + l.perLayer() - 1) / l.perLayer());
    }

    public float pileHeight() {
        return Math.min(0.98f, layers() * layout().layerHeight());
    }

    /**
     * How one component kind lies on the block. {@code sizePx} is the footprint the part is
     * drawn at in model pixels, so a seven-inch frame really is twice a cinewhoop; how many
     * fit per layer, and therefore how many the pile takes at all, follows from it.
     */
    public record Layout(int cols, int rows, int layers, float sizePx, float layerHeight,
                         float yawStep, float layerYaw) {
        public int perLayer() {
            return cols * rows;
        }

        public int capacity() {
            return Math.min(MAX_CAPACITY, perLayer() * layers);
        }
    }

    public static Layout layoutFor(ItemStack stack) {
        if (!(stack.getItem() instanceof ComponentItem item)) return grid(6f, 2, 0.5f, 0f, 0f);
        ComponentStats s = item.getStats();
        return switch (s.type()) {
            case FRAME -> grid(Mth.clamp(s.frameSize() * 1.85f, 3.4f, 15f), 4, 0.22f, 0f, 45f);
            case MOTOR -> grid(1.5f + s.motorDiameter() * 0.075f, 2, 0.92f, 0f, 0f);
            case BATTERY -> grid(1.8f + s.batteryCells() * 0.4f + s.batteryCapacity() * 0.0004f,
                3, 0.62f, 0f, 0f);
            case PROPELLER -> grid(1.6f + s.propDiameter() * 0.75f, 5, 0.12f, 31f, 0f);
            case CAMERA -> grid(2.8f, 2, 0.72f, 0f, 0f);
            case VTX -> grid(3.0f, 3, 0.36f, 0f, 0f);
            case FLIGHT_CONTROLLER -> grid(2.8f, 3, 0.34f, 0f, 0f);
            case FIRMWARE -> grid(2.4f, 5, 0.14f, 0f, 0f);
            case MODULE -> grid(2.6f + s.weight() * 0.02f, 2, 0.6f, 0f, 0f);
        };
    }

    private static Layout grid(float sizePx, int layers, float thickness, float yawStep, float layerYaw) {
        int cols = Mth.clamp((int) (SPAN_PX / (sizePx * 1.10f)), 1, 4);
        return new Layout(cols, cols, layers, sizePx, sizePx * thickness / 16f, yawStep, layerYaw);
    }

    public ComponentType storedType() {
        return stored.getItem() instanceof ComponentItem c ? c.getComponentType() : null;
    }

    public boolean accepts(ItemStack stack) {
        if (!isStorable(stack)) return false;
        if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, stack)) return false;
        return stored.getCount() < layoutFor(stored.isEmpty() ? stack : stored).capacity();
    }

    public int insert(ItemStack from) {
        return insert(from, from.getCount());
    }

    public int insert(ItemStack from, int max) {
        if (!accepts(from)) return 0;
        int room = layoutFor(stored.isEmpty() ? from : stored).capacity() - stored.getCount();
        int moved = Math.min(Math.min(room, from.getCount()), max);
        if (moved <= 0) return 0;
        if (stored.isEmpty()) stored = from.copyWithCount(moved);
        else stored.grow(moved);
        from.shrink(moved);
        sync();
        return moved;
    }

    public ItemStack extract(int max) {
        if (stored.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = stored.copyWithCount(Math.min(max, stored.getCount()));
        stored.shrink(out.getCount());
        if (stored.isEmpty()) stored = ItemStack.EMPTY;
        sync();
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.contains("Item")
            ? ItemStack.parse(registries, tag.getCompound("Item")).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!stored.isEmpty()) tag.put("Item", stored.save(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
