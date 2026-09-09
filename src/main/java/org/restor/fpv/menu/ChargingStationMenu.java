package org.restor.fpv.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.block.entity.ChargingStationBlockEntity;
import org.restor.fpv.item.BatteryCharge;

public class ChargingStationMenu extends AbstractContainerMenu {
    private static final int SLOTS = ChargingStationBlockEntity.SLOTS;

    private final Container container;

    public ChargingStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(SLOTS));
    }

    public ChargingStationMenu(int id, Inventory inv, Container container) {
        super(ModMenus.CHARGING_STATION.get(), id);
        this.container = container;

        for (int i = 0; i < SLOTS; i++) {
            addSlot(new Slot(container, i, 26 + (i % 3) * 44, 30 + (i / 3) * 26) {
                @Override public boolean mayPlace(ItemStack stack) { return BatteryCharge.isChargeable(stack); }
                @Override public int getMaxStackSize() { return 16; }
            });
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 96 + i * 18));
            }
        }
        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inv, j, 8 + j * 18, 154));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, SLOTS + 36, true)) return ItemStack.EMPTY;
        } else if (BatteryCharge.isChargeable(stack)) {
            if (!moveItemStackTo(stack, 0, SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slots.get(index).setByPlayer(ItemStack.EMPTY);
        else slots.get(index).setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slots.get(index).onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
