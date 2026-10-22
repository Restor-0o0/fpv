package org.restor.fpv.block.entity;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.restor.fpv.block.ChargingStationBlock;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.item.BatteryCharge;
import org.restor.fpv.menu.ChargingStationMenu;

public class ChargingStationBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 6;
    private static final float RATE = 1f / 700f;
    private static final double PAD_RADIUS = 3.5;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int padCooldown;

    public ChargingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGING_STATION.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargingStationBlockEntity be) {
        be.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        boolean working = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            if (BatteryCharge.add(stack, RATE * stack.getCount())) working = true;
        }

        if (++padCooldown >= 20) {
            padCooldown = 0;
            AABB box = new AABB(pos).inflate(PAD_RADIUS);
            for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, box, DroneEntity::isAlive)) {
                if (drone.battery == null || drone.battery.getPercentRemaining() >= 0.999f) continue;
                drone.battery.charge(RATE * 20f);
                working = true;
            }
        }

        if (working) setChanged();
        if (state.getValue(ChargingStationBlock.CHARGING) != working) {
            level.setBlock(pos, state.setValue(ChargingStationBlock.CHARGING, working), 3);
            if (working) {
                level.playSound(null, pos, org.restor.fpv.sound.ModSounds.STATION_CHARGE.get(), SoundSource.BLOCKS, 0.25f, 1.8f);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return ContainerHelper.removeItem(items, slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return BatteryCharge.isChargeable(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override public void clearContent() { items.clear(); }

    @Override public Component getDisplayName() { return Component.translatable("block.fpv.charging_station"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ChargingStationMenu(id, inv, this);
    }

    public void openFor(ServerPlayer player) {
player.openMenu(this, buf -> buf.writeBlockPos(worldPosition));
    }
}
