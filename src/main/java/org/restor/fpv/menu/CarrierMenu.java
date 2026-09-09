package org.restor.fpv.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.entity.CarrierPlaneEntity;

public class CarrierMenu extends AbstractContainerMenu {
    private static final int SLOTS = CarrierPlaneEntity.HARDPOINTS;

    private final Container container;
    private final CarrierPlaneEntity carrier;

    public CarrierMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, resolve(inv, buf.readInt()));
    }

    private static CarrierPlaneEntity resolve(Inventory inv, int entityId) {
        return inv.player.level().getEntity(entityId) instanceof CarrierPlaneEntity c ? c : null;
    }

    public CarrierMenu(int id, Inventory inv, CarrierPlaneEntity carrier) {
        super(ModMenus.CARRIER.get(), id);
        this.carrier = carrier;
        this.container = carrier != null ? carrier.hardpoints() : new SimpleContainer(SLOTS);

        for (int i = 0; i < SLOTS; i++) {
            final int index = i;
            addSlot(new Slot(container, i, 26 + (i % 3) * 44, 30 + (i / 3) * 26) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (!CarrierPlaneEntity.isValidPayload(stack)) return false;
                    return carrier == null || carrier.fitsWeight(stack, index);
                }
                @Override public int getMaxStackSize() { return 4; }
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

    public float loadedMass() { return carrier != null ? carrier.loadedMass() : 0f; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, SLOTS + 36, true)) return ItemStack.EMPTY;
        } else if (CarrierPlaneEntity.isValidPayload(stack)) {
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
        return carrier != null && carrier.isAlive() && player.distanceToSqr(carrier) <= 100.0;
    }
}
