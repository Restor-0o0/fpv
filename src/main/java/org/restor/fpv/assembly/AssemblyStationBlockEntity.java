package org.restor.fpv.assembly;

import net.minecraft.world.Containers;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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

public class AssemblyStationBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer items = new SimpleContainer(AssemblyStationMenu.INPUT_SLOT_COUNT);

    public AssemblyStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_STATION.get(), pos, state);
        items.addListener(c -> setChanged());
    }

    public SimpleContainer getItems() {
        return items;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, items);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.fpv.assembly_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AssemblyStationMenu(id, inv, items, worldPosition);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int i = 0;
        for (ItemStack stack : input.listOrEmpty("Items", ItemStack.OPTIONAL_CODEC)) {
            if (i >= items.getContainerSize()) break;
            items.setItem(i++, stack);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.list("Items", ItemStack.OPTIONAL_CODEC);
        for (int i = 0; i < items.getContainerSize(); i++) list.add(items.getItem(i));
    }
}
