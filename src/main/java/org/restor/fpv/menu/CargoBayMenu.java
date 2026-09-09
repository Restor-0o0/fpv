package org.restor.fpv.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.entity.CargoPlaneEntity;

public class CargoBayMenu extends AbstractContainerMenu {
    private static final int SLOTS = CargoPlaneEntity.BAY_SLOTS;

    private final Container container;
    private final CargoPlaneEntity plane;

    public CargoBayMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getEntity(buf.readInt()) instanceof CargoPlaneEntity c ? c : null);
    }

    public CargoBayMenu(int id, Inventory inv, CargoPlaneEntity plane) {
        super(ModMenus.CARGO_BAY.get(), id);
        this.plane = plane;
        this.container = plane != null ? plane.bay() : new SimpleContainer(SLOTS);

        for (int i = 0; i < SLOTS; i++) {
            addSlot(new Slot(container, i, 44 + (i % 5) * 18, 26 + (i / 5) * 18) {
                @Override public int getMaxStackSize() { return 16; }
            });
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 100 + i * 18));
            }
        }
        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inv, j, 8 + j * 18, 158));
        }
    }

    public float cargoMass() { return plane != null ? plane.cargoMass() : 0f; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, SLOTS + 36, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
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
        return plane != null && plane.isAlive() && player.distanceToSqr(plane) <= 100.0;
    }
}
