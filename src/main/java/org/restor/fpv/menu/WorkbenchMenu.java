package org.restor.fpv.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.recipe.DroneBuilder;

import java.util.HashMap;
import java.util.Map;

public class WorkbenchMenu extends AbstractContainerMenu {
    private final Container container;

    private static final int SLOT_FRAME = 0;
    private static final int SLOT_MOTOR_1 = 1;
    private static final int SLOT_MOTOR_2 = 2;
    private static final int SLOT_MOTOR_3 = 3;
    private static final int SLOT_MOTOR_4 = 4;
    private static final int SLOT_BATTERY = 5;
    private static final int SLOT_PROPELLER = 6;
    private static final int SLOT_RESULT = 7;

    public WorkbenchMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(8));
    }

    public WorkbenchMenu(int id, Inventory inv, Container container) {
        super(ModMenus.WORKBENCH.get(), id);
        this.container = container;

        addSlot(new ComponentSlot(container, SLOT_FRAME, 17, 17, ComponentType.FRAME));
        addSlot(new ComponentSlot(container, SLOT_MOTOR_1, 71, 17, ComponentType.MOTOR));
        addSlot(new ComponentSlot(container, SLOT_MOTOR_2, 89, 17, ComponentType.MOTOR));
        addSlot(new ComponentSlot(container, SLOT_MOTOR_3, 71, 35, ComponentType.MOTOR));
        addSlot(new ComponentSlot(container, SLOT_MOTOR_4, 89, 35, ComponentType.MOTOR));
        addSlot(new ComponentSlot(container, SLOT_BATTERY, 17, 53, ComponentType.BATTERY));
        addSlot(new ComponentSlot(container, SLOT_PROPELLER, 71, 53, ComponentType.PROPELLER));
        addSlot(new Slot(container, SLOT_RESULT, 143, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public void onTake(Player player, ItemStack stack) { clearComponents(); }
        });

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inv, j, 8 + j * 18, 142));
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        tryAssemble();
    }

    private void tryAssemble() {
        ItemStack frame = container.getItem(SLOT_FRAME);
        ItemStack battery = container.getItem(SLOT_BATTERY);

        if (frame.isEmpty() || battery.isEmpty()) {
            container.setItem(SLOT_RESULT, ItemStack.EMPTY);
            return;
        }

        Map<ComponentType, ComponentStats> parts = new HashMap<>();
        parts.put(ComponentType.FRAME, ((ComponentItem) frame.getItem()).getStats());
        parts.put(ComponentType.BATTERY, ((ComponentItem) battery.getItem()).getStats());

        ItemStack prop = container.getItem(SLOT_PROPELLER);
        if (!prop.isEmpty() && prop.getItem() instanceof ComponentItem ci && ci.getComponentType() == ComponentType.PROPELLER) {
            parts.put(ComponentType.PROPELLER, ci.getStats());
        }

        boolean hasMotor = false;
        for (int i = SLOT_MOTOR_1; i <= SLOT_MOTOR_4; i++) {
            ItemStack motor = container.getItem(i);
            if (!motor.isEmpty() && motor.getItem() instanceof ComponentItem ci && ci.getComponentType() == ComponentType.MOTOR) {
                parts.put(ComponentType.MOTOR, ci.getStats());
                hasMotor = true;
            }
        }

        if (!hasMotor) {
            container.setItem(SLOT_RESULT, ItemStack.EMPTY);
            return;
        }

        ItemStack drone = DroneBuilder.assemble(parts);
        container.setItem(SLOT_RESULT, drone);
    }

    private void clearComponents() {
        for (int i = 0; i < 7; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (!stack.isEmpty()) {
            if (index < 8) {
                if (!moveItemStackTo(stack, 8, 44, true)) return ItemStack.EMPTY;
            } else if (stack.getItem() instanceof ComponentItem ci) {
                int targetSlot = switch (ci.getComponentType()) {
                    case FRAME -> SLOT_FRAME;
                    case BATTERY -> SLOT_BATTERY;
                    case PROPELLER -> SLOT_PROPELLER;
                    case MOTOR -> findEmptyMotorSlot();
                    default -> -1;
                };
                if (targetSlot >= 0 && !moveItemStackTo(stack, targetSlot, targetSlot + 1, false)) return ItemStack.EMPTY;
                slotsChanged(container);
            }
        }
        return ItemStack.EMPTY;
    }

    private int findEmptyMotorSlot() {
        for (int i = SLOT_MOTOR_1; i <= SLOT_MOTOR_4; i++) {
            if (container.getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    public Container getContainer() { return container; }

    private static class ComponentSlot extends Slot {
        private final ComponentType allowedType;
        public ComponentSlot(Container container, int slot, int x, int y, ComponentType type) {
            super(container, slot, x, y);
            this.allowedType = type;
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof ComponentItem ci && ci.getComponentType() == allowedType;
        }
    }
}
