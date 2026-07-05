package org.restor.fpv.assembly;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.item.ModItems;
import org.restor.fpv.menu.ModMenus;

/**
 * Slot layout mirrors the physical build: frame in the center, battery above it,
 * camera below, motor slots at the corners (up to 8 for hexa/octo frames) and a
 * prop slot OUTSIDE next to each motor. The result appears when the build is
 * complete; taking it consumes the components.
 *
 * Motor slot i ↔ FrameSpec mount i: even i = right column (FR, RR, MR, …),
 * odd i = left column (FL, RL, ML, …), rows top→bottom.
 */
public class AssemblyStationMenu extends AbstractContainerMenu {
    public static final int SLOT_FRAME = 0;
    public static final int SLOT_BATTERY = 1;
    public static final int SLOT_CAMERA = 2;
    public static final int SLOT_MOTOR_FIRST = 3;                                // 3..10
    public static final int SLOT_PROP_FIRST = SLOT_MOTOR_FIRST + DroneAssembly.MAX_MOTORS; // 11..18
    public static final int SLOT_RESULT = SLOT_PROP_FIRST + DroneAssembly.MAX_MOTORS;      // 19
    public static final int SLOT_COUNT = SLOT_RESULT + 1;

    private final Container container;

    public AssemblyStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(SLOT_COUNT));
    }

    public AssemblyStationMenu(int id, Inventory inv, Container container) {
        super(ModMenus.ASSEMBLY_STATION.get(), id);
        this.container = container;

        addSlot(new TypedSlot(container, SLOT_FRAME, 80, 55, ComponentType.FRAME, -1));
        addSlot(new TypedSlot(container, SLOT_BATTERY, 80, 17, ComponentType.BATTERY, -1));
        addSlot(new TypedSlot(container, SLOT_CAMERA, 80, 93, ComponentType.CAMERA, -1));

        for (int i = 0; i < DroneAssembly.MAX_MOTORS; i++) {
            boolean right = i % 2 == 0;
            int row = i / 2;
            int y = 17 + row * 25;
            addSlot(new TypedSlot(container, SLOT_MOTOR_FIRST + i, right ? 128 : 32, y, ComponentType.MOTOR, i));
            addSlot(new TypedSlot(container, SLOT_PROP_FIRST + i, right ? 152 : 8, y, ComponentType.PROPELLER, i));
        }

        addSlot(new Slot(container, SLOT_RESULT, 80, 119) {
            @Override public boolean mayPlace(ItemStack stack) {
                return canDisassemble(stack);
            }

            @Override public boolean isActive() { return true; }

            @Override public void onTake(Player player, ItemStack stack) {
                consumeComponents();
                super.onTake(player, stack);
            }
        });

        // Player inventory
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 143 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 201));
        }

        updateResult();
    }

    @Override
    public void slotsChanged(Container c) {
        super.slotsChanged(c);
        if (tryDisassemble()) return;
        updateResult();
    }

    /** Builds a DroneAssembly from the current slots. */
    public DroneAssembly buildAssembly() {
        DroneAssembly a = new DroneAssembly();
        a.frame = container.getItem(SLOT_FRAME);
        a.battery = container.getItem(SLOT_BATTERY);
        a.camera = container.getItem(SLOT_CAMERA);
        for (int i = 0; i < DroneAssembly.MAX_MOTORS; i++) {
            a.motors[i] = container.getItem(SLOT_MOTOR_FIRST + i);
            a.props[i] = container.getItem(SLOT_PROP_FIRST + i);
        }
        return a;
    }

    private void updateResult() {
        if (canDisassemble(container.getItem(SLOT_RESULT))) return;
        DroneAssembly a = buildAssembly();
        if (a.isComplete()) {
            ItemStack result = new ItemStack(ModItems.MODULAR_DRONE.get());
            result.getOrCreateTag().put("Assembly", a.save());
            container.setItem(SLOT_RESULT, result);
        } else {
            container.setItem(SLOT_RESULT, ItemStack.EMPTY);
        }
    }

    private boolean canDisassemble(ItemStack stack) {
        return stack.getItem() == ModItems.MODULAR_DRONE.get()
            && stack.hasTag()
            && stack.getTag().contains("Assembly")
            && isStationEmptyExceptResult();
    }

    private boolean tryDisassemble() {
        ItemStack stack = container.getItem(SLOT_RESULT);
        if (!canDisassemble(stack)) return false;
        DroneAssembly a = DroneAssembly.load(stack.getTag().getCompound("Assembly"));
        FrameSpec spec = a.frameSpec();
        if (spec == null) return false;
        container.setItem(SLOT_FRAME, a.frame.copy());
        container.setItem(SLOT_BATTERY, a.battery.copy());
        container.setItem(SLOT_CAMERA, a.camera.copy());
        for (int i = 0; i < spec.motorCount(); i++) {
            container.setItem(SLOT_MOTOR_FIRST + i, a.motors[i].copy());
            container.setItem(SLOT_PROP_FIRST + i, a.props[i].copy());
        }
        container.setItem(SLOT_RESULT, ItemStack.EMPTY);
        return true;
    }

    private boolean isStationEmptyExceptResult() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i != SLOT_RESULT && !container.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private void consumeComponents() {
        FrameSpec spec = buildAssembly().frameSpec();
        int n = spec != null ? spec.motorCount() : DroneAssembly.MAX_MOTORS;
        shrinkSlot(SLOT_FRAME);
        shrinkSlot(SLOT_BATTERY);
        shrinkSlot(SLOT_CAMERA);
        for (int i = 0; i < n; i++) {
            shrinkSlot(SLOT_MOTOR_FIRST + i);
            shrinkSlot(SLOT_PROP_FIRST + i);
        }
        updateResult();
    }

    private void shrinkSlot(int slot) {
        ItemStack s = container.getItem(slot);
        if (!s.isEmpty()) {
            s.shrink(1);
            container.setItem(slot, s);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (index < SLOT_COUNT) {
            ItemStack copy = stack.copy();
            if (!moveItemStackTo(stack, SLOT_COUNT, SLOT_COUNT + 36, true)) return ItemStack.EMPTY;
            if (index == SLOT_RESULT) consumeComponents();
            slotsChanged(container);
            return copy;
        }
        if (stack.getItem() instanceof ComponentItem ci) {
            int from, to;
            switch (ci.getComponentType()) {
                case FRAME -> { from = SLOT_FRAME; to = SLOT_FRAME + 1; }
                case BATTERY -> { from = SLOT_BATTERY; to = SLOT_BATTERY + 1; }
                case CAMERA -> { from = SLOT_CAMERA; to = SLOT_CAMERA + 1; }
                case MOTOR -> { from = SLOT_MOTOR_FIRST; to = SLOT_MOTOR_FIRST + DroneAssembly.MAX_MOTORS; }
                case PROPELLER -> { from = SLOT_PROP_FIRST; to = SLOT_PROP_FIRST + DroneAssembly.MAX_MOTORS; }
                default -> { return ItemStack.EMPTY; }
            }
            moveItemStackTo(stack, from, to, false);
            slotsChanged(container);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    private class TypedSlot extends Slot {
        private final ComponentType type;
        private final int motorIndex;

        TypedSlot(Container container, int slot, int x, int y, ComponentType type, int motorIndex) {
            super(container, slot, x, y);
            this.type = type;
            this.motorIndex = motorIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof ComponentItem ci) || ci.getComponentType() != type) return false;
            if (type == ComponentType.FRAME) return true;
            ItemStack frame = container.getItem(SLOT_FRAME);
            if (frame.isEmpty() || !DroneAssembly.isCompatible(frame, stack)) return false;
            FrameSpec spec = buildAssembly().frameSpec();
            return motorIndex < 0 || (spec != null && motorIndex < spec.motorCount());
        }

        @Override
        public boolean isActive() {
            if (type == ComponentType.FRAME || hasItem()) return true;
            FrameSpec spec = buildAssembly().frameSpec();
            if (spec == null) return false;
            return motorIndex < 0 || motorIndex < spec.motorCount();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
