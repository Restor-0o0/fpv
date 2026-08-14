package org.restor.fpv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

// Stores the station's saved drone link codes (add/remove from the station screen).
public class ControlStationBlockEntity extends BlockEntity {
    public static final int MAX_CODES = 10;
    private final List<String> codes = new ArrayList<>();

    public ControlStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_STATION.get(), pos, state);
    }

    public List<String> getCodes() {
        return List.copyOf(codes);
    }

    public boolean addCode(String code) {
        if (codes.contains(code) || codes.size() >= MAX_CODES) return false;
        codes.add(code);
        sync();
        return true;
    }

    public void removeCode(String code) {
        if (codes.remove(code)) sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        codes.clear();
        ListTag list = tag.getList("Codes", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) codes.add(list.getString(i));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (String c : codes) list.add(StringTag.valueOf(c));
        tag.put("Codes", list);
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
