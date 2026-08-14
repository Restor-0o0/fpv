package org.restor.fpv.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.block.entity.ModBlockEntities;

// Persistent inventory of the assembly station (components survive closing the UI).
public class AssemblyStationBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer items = new SimpleContainer(AssemblyStationMenu.SLOT_COUNT);

    public AssemblyStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_STATION.get(), pos, state);
        items.addListener(c -> setChanged());
    }

    public SimpleContainer getItems() {
        return items;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.fpv.assembly_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AssemblyStationMenu(id, inv, items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        var list = tag.getList("Items", 10);
        for (int i = 0; i < list.size() && i < items.getContainerSize(); i++) {
            items.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < items.getContainerSize(); i++) {
            list.add(items.getItem(i).saveOptional(registries));
        }
        tag.put("Items", list);
    }
}
