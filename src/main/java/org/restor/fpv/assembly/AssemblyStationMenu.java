package org.restor.fpv.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.restor.fpv.compat.CustomData;
import org.restor.fpv.block.ModBlocks;
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
    public static final int SLOT_MODULE = SLOT_PROP_FIRST + DroneAssembly.MAX_MOTORS;      // 19
    public static final int SLOT_FIRMWARE = SLOT_MODULE + 1;                                // 20
    public static final int SLOT_REPAIR_MATERIAL = SLOT_FIRMWARE + 1;                        // 21
    public static final int SLOT_CARD_IN = SLOT_REPAIR_MATERIAL + 1;                         // 22
    public static final int SLOT_BAY_FIRST = SLOT_CARD_IN + 1;                               // 23..26
    public static final int SLOT_RESULT = SLOT_BAY_FIRST + DroneAssembly.BAY_SIZE;           // 27
    public static final int SLOT_CARD_OUT = SLOT_RESULT + 1;                                 // 28
    public static final int SLOT_COUNT = SLOT_CARD_OUT + 1;
    public static final int INPUT_SLOT_COUNT = SLOT_RESULT;
    public static final int BUTTON_REPAIR = 0;
    private static final int REPAIR_PER_ITEM = 250;
    private static final int DATA_ISSUES = 0;
    private static final int DATA_MASS_GRAMS = 1;
    private static final int DATA_THRUST_CENTINEWTONS = 2;
    private static final int DATA_TWR_HUNDREDTHS = 3;
    private static final int DATA_HOVER_PERMILLE = 4;
    private static final int DATA_CONDITION_PERMILLE = 5;
    private static final int DATA_REPAIR_TYPE = 6;
    private static final int DATA_REPAIR_INDEX = 7;
    private static final int DATA_COUNT = 8;

    private final Container container;
    private final SimpleContainer resultContainer = new SimpleContainer(1);
    private final SimpleContainer cardContainer = new SimpleContainer(1);
    private final ContainerLevelAccess access;
    private final ContainerData diagnosticData;
    private final net.minecraft.world.ContainerListener refresh = this::slotsChanged;
    private boolean refreshing;

    public AssemblyStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(INPUT_SLOT_COUNT), buf.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    public AssemblyStationMenu(int id, Inventory inv, Container container, BlockPos pos) {
        this(id, inv, container, pos, new SimpleContainerData(DATA_COUNT));
    }

    private AssemblyStationMenu(int id, Inventory inv, Container container, BlockPos pos,
                                ContainerData diagnosticData) {
        super(ModMenus.ASSEMBLY_STATION.get(), id);
        checkContainerSize(container, INPUT_SLOT_COUNT);
        checkContainerDataCount(diagnosticData, DATA_COUNT);
        this.container = container;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.diagnosticData = diagnosticData;
        addDataSlots(diagnosticData);

        addSlot(new TypedSlot(container, SLOT_FRAME, 80, 55, ComponentType.FRAME, -1));
        addSlot(new TypedSlot(container, SLOT_BATTERY, 80, 17, ComponentType.BATTERY, -1));
        addSlot(new TypedSlot(container, SLOT_CAMERA, 80, 93, ComponentType.CAMERA, -1));

        for (int i = 0; i < DroneAssembly.MAX_MOTORS; i++) {
            boolean right = i % 2 == 0;
            int row = i / 2;
            int y = 17 + row * 25;
            addSlot(new TypedSlot(container, SLOT_MOTOR_FIRST + i, right ? 128 : 32, y, ComponentType.MOTOR, i));
        }
        for (int i = 0; i < DroneAssembly.MAX_MOTORS; i++) {
            boolean right = i % 2 == 0;
            int row = i / 2;
            int y = 17 + row * 25;
            addSlot(new TypedSlot(container, SLOT_PROP_FIRST + i, right ? 152 : 8, y, ComponentType.PROPELLER, i));
        }

        addSlot(new TypedSlot(container, SLOT_MODULE, 104, 55, ComponentType.MODULE, -1));
        addSlot(new TypedSlot(container, SLOT_FIRMWARE, 56, 55, ComponentType.FIRMWARE, -1));
        addSlot(new Slot(container, SLOT_REPAIR_MATERIAL, 184, 183) {
            @Override public boolean mayPlace(ItemStack stack) { return isRepairMaterial(stack); }
        });

        addSlot(new Slot(container, SLOT_CARD_IN, 8, 119) {
            @Override public boolean mayPlace(ItemStack stack) { return isConvertible(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
            addSlot(new BaySlot(container, SLOT_BAY_FIRST + i, 104 + i * 18, 119));
        }

        addSlot(new Slot(resultContainer, 0, 80, 119) {
            @Override public boolean mayPlace(ItemStack stack) {
                return canDisassemble(stack);
            }

            @Override public boolean mayPickup(Player player) { return true; }

            @Override public boolean isActive() { return true; }

            @Override public int getMaxStackSize() { return 1; }

            @Override public void onTake(Player player, ItemStack stack) {
                consumeComponents();
                super.onTake(player, stack);
            }
        });

        addSlot(new Slot(cardContainer, 0, 32, 119) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public boolean isActive() { return true; }

            @Override public void onTake(Player player, ItemStack stack) {
                consumeCardInput();
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

        if (container instanceof SimpleContainer sc) sc.addListener(refresh);
        resultContainer.addListener(refresh);
        refresh();
    }

    @Override
    public void slotsChanged(Container c) {
        super.slotsChanged(c);
        refresh();
    }

    private void refresh() {
        if (refreshing) return;
        refreshing = true;
        try {
            tryDisassemble();
            updateResult();
            updateCardResult();
        } finally {
            refreshing = false;
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (container instanceof SimpleContainer sc) sc.removeListener(refresh);
        resultContainer.removeListener(refresh);
        cardContainer.setItem(0, ItemStack.EMPTY);
        ItemStack left = resultContainer.getItem(0);
        resultContainer.setItem(0, ItemStack.EMPTY);
        // A drone parked in the output was brought in by the player; a generated result is
        // rebuilt from the parts that stay in the bench, so only the former is handed back.
        if (!left.isEmpty() && areComponentSlotsEmpty() && !player.level().isClientSide) {
            if (!player.getInventory().add(left)) player.drop(left, false);
        }
    }

    /** Builds a DroneAssembly from the current slots. */
    public DroneAssembly buildAssembly() {
        DroneAssembly a = new DroneAssembly();
        a.frame = container.getItem(SLOT_FRAME);
        a.battery = container.getItem(SLOT_BATTERY);
        a.camera = container.getItem(SLOT_CAMERA);
        a.module = container.getItem(SLOT_MODULE);
        a.firmware = container.getItem(SLOT_FIRMWARE);
        for (int i = 0; i < DroneAssembly.MAX_MOTORS; i++) {
            a.motors[i] = container.getItem(SLOT_MOTOR_FIRST + i);
            a.props[i] = container.getItem(SLOT_PROP_FIRST + i);
        }
        for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
            a.bay[i] = container.getItem(SLOT_BAY_FIRST + i);
        }
        return a;
    }

    // ------------------------------------------------------- card conversion

    /** An assembled aircraft folds into a card; a card unfolds back into the aircraft. */
    private static boolean isConvertible(ItemStack stack) {
        return !convert(stack).isEmpty();
    }

    private static ItemStack convert(ItemStack in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        if (DroneCardItem.isCard(in)) {
            DroneAssembly a = DroneCardItem.assembly(in);
            if (a == null || !a.isComplete()) return ItemStack.EMPTY;
            CompoundTag src = DroneCardItem.data(in);
            ItemStack out = new ItemStack(DroneAssembly.isPlaneFrame(a.frame)
                ? ModItems.MODULAR_PLANE.get() : ModItems.MODULAR_DRONE.get());
            CustomData.update(out, t -> {
                t.put("Assembly", src.getCompound("Assembly"));
                if (src.contains("Warhead")) t.putString("Warhead", src.getString("Warhead"));
            });
            return out;
        }
        if (in.getItem() != ModItems.MODULAR_DRONE.get() && in.getItem() != ModItems.MODULAR_PLANE.get()) {
            return ItemStack.EMPTY;
        }
        CustomData data = CustomData.get(in);
        if (data == null || !data.contains("Assembly")) return ItemStack.EMPTY;
        CompoundTag tag = data.copyTag();
        DroneAssembly a = DroneAssembly.load(tag.getCompound("Assembly"));
        if (!a.isComplete()) return ItemStack.EMPTY;
        return DroneCardItem.of(a, tag.contains("Warhead")
            ? org.restor.fpv.warhead.WarheadType.byId(tag.getString("Warhead")) : null);
    }

    private void updateCardResult() {
        cardContainer.setItem(0, convert(container.getItem(SLOT_CARD_IN)));
    }

    private void consumeCardInput() {
        refreshing = true;
        try {
            shrinkSlot(SLOT_CARD_IN);
        } finally {
            refreshing = false;
        }
        refresh();
    }

    private void updateResult() {
        if (canDisassemble(resultContainer.getItem(0))) return;
        DroneAssembly a = buildAssembly();
        DroneAssemblyValidator.ValidationResult validation = a.validate();
        updateDiagnosticData(a, validation);
        // A half-built wing still leaves the bench: it sets down as an airframe you cannot
        // fly but can pick back up and keep working on.
        boolean partialWing = DroneAssembly.isPlaneFrame(a.frame) && !a.isBareFrame();
        if (validation.isComplete() || partialWing) {
            ItemStack result = new ItemStack(DroneAssembly.isPlaneFrame(a.frame)
                ? ModItems.MODULAR_PLANE.get() : ModItems.MODULAR_DRONE.get());
            CustomData.update(result, t -> t.put("Assembly", a.save()));
            resultContainer.setItem(0, result);
        } else {
            resultContainer.setItem(0, ItemStack.EMPTY);
        }
    }

    private void updateDiagnosticData(DroneAssembly assembly, DroneAssemblyValidator.ValidationResult validation) {
        DroneAssemblyValidator.Metrics metrics = validation.metrics();
        DroneAssembly.RepairTarget repair = assembly.repairTarget();
        diagnosticData.set(DATA_ISSUES, validation.mask());
        diagnosticData.set(DATA_MASS_GRAMS, Math.round(metrics.massKg() * 1000f));
        diagnosticData.set(DATA_THRUST_CENTINEWTONS, Math.round(metrics.thrustN() * 100f));
        diagnosticData.set(DATA_TWR_HUNDREDTHS, Math.round(metrics.twr() * 100f));
        diagnosticData.set(DATA_HOVER_PERMILLE, Math.round(metrics.hoverThrottle() * 1000f));
        diagnosticData.set(DATA_CONDITION_PERMILLE, assembly.minimumCondition());
        diagnosticData.set(DATA_REPAIR_TYPE, repair == null ? 0 : repair.type().ordinal() + 1);
        diagnosticData.set(DATA_REPAIR_INDEX, repair == null ? -1 : repair.index());
    }

    /** Every issue the current build has, in IssueCode declaration order. */
    public java.util.List<DroneAssemblyValidator.IssueCode> issues() {
        int mask = diagnosticData.get(DATA_ISSUES);
        java.util.List<DroneAssemblyValidator.IssueCode> list = new java.util.ArrayList<>();
        for (var code : DroneAssemblyValidator.IssueCode.values()) {
            if ((mask & code.mask()) != 0) list.add(code);
        }
        return list;
    }

    public float massKg() {
        return diagnosticData.get(DATA_MASS_GRAMS) / 1000f;
    }

    public float thrustN() {
        return diagnosticData.get(DATA_THRUST_CENTINEWTONS) / 100f;
    }

    public float twr() {
        return diagnosticData.get(DATA_TWR_HUNDREDTHS) / 100f;
    }

    public float hoverThrottle() {
        return diagnosticData.get(DATA_HOVER_PERMILLE) / 1000f;
    }

    public int conditionPercent() {
        return Math.round(diagnosticData.get(DATA_CONDITION_PERMILLE) / 10f);
    }

    public ComponentType repairTargetType() {
        int id = diagnosticData.get(DATA_REPAIR_TYPE) - 1;
        return id >= 0 && id < ComponentType.values().length ? ComponentType.values()[id] : null;
    }

    public int repairTargetIndex() {
        return diagnosticData.get(DATA_REPAIR_INDEX);
    }

    public Item repairMaterial() {
        return repairMaterialFor(repairTargetType());
    }

    public boolean canRepair() {
        Item material = repairMaterial();
        return material != null && container.getItem(SLOT_REPAIR_MATERIAL).is(material);
    }

    private boolean canDisassemble(ItemStack stack) {
        CustomData data = CustomData.get(stack);
        return (stack.getItem() == ModItems.MODULAR_DRONE.get()
                || stack.getItem() == ModItems.MODULAR_PLANE.get())
            && data != null
            && data.contains("Assembly")
            && areComponentSlotsEmpty();
    }

    private boolean tryDisassemble() {
        ItemStack stack = resultContainer.getItem(0);
        if (!canDisassemble(stack)) return false;
        CompoundTag tag = CustomData.get(stack).copyTag();
        DroneAssembly a = DroneAssembly.load(tag.getCompound("Assembly"));
        FrameSpec spec = a.frameSpec();
        if (spec == null) return false;
        container.setItem(SLOT_FRAME, a.frame.copy());
        container.setItem(SLOT_BATTERY, a.battery.copy());
        container.setItem(SLOT_CAMERA, a.camera.copy());
        container.setItem(SLOT_MODULE, a.module.copy());
        container.setItem(SLOT_FIRMWARE, a.firmware.copy());
        for (int i = 0; i < spec.motorCount(); i++) {
            container.setItem(SLOT_MOTOR_FIRST + i, a.motors[i].copy());
            container.setItem(SLOT_PROP_FIRST + i, a.props[i].copy());
        }
        for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
            container.setItem(SLOT_BAY_FIRST + i, a.bay[i].copy());
        }
        resultContainer.setItem(0, ItemStack.EMPTY);
        return true;
    }

    private boolean areComponentSlotsEmpty() {
        for (int i = SLOT_FRAME; i <= SLOT_FIRMWARE; i++) {
            if (!container.getItem(i).isEmpty()) return false;
        }
        for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
            if (!container.getItem(SLOT_BAY_FIRST + i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_REPAIR) return false;
        if (player.level().isClientSide) return true;
        DroneAssembly.RepairTarget target = buildAssembly().repairTarget();
        if (target == null) return false;
        Item required = repairMaterialFor(target.type());
        ItemStack material = container.getItem(SLOT_REPAIR_MATERIAL);
        if (required == null || !material.is(required)) return false;

        ComponentCondition.repair(target.stack(), REPAIR_PER_ITEM);
        material.shrink(1);
        container.setItem(SLOT_REPAIR_MATERIAL, material);
        container.setChanged();
        slotsChanged(container);
        broadcastChanges();
        return true;
    }

    public static Item repairMaterialFor(ComponentType type) {
        if (type == null) return null;
        return switch (type) {
            case FRAME, MODULE -> Items.IRON_INGOT;
            case FIRMWARE -> Items.REDSTONE;
            case MOTOR -> Items.COPPER_INGOT;
            case PROPELLER -> Items.IRON_NUGGET;
            case BATTERY, CAMERA -> Items.REDSTONE;
            default -> null;
        };
    }

    private static boolean isRepairMaterial(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT)
            || stack.is(Items.IRON_NUGGET) || stack.is(Items.REDSTONE);
    }

    private void consumeComponents() {
        FrameSpec spec = buildAssembly().frameSpec();
        int n = spec != null ? spec.motorCount() : DroneAssembly.MAX_MOTORS;
        refreshing = true;
        try {
            // The container listener rebuilds a result the moment the taken one leaves the
            // slot; clearing it here stops that copy being disassembled back into the bench.
            resultContainer.setItem(0, ItemStack.EMPTY);
            shrinkSlot(SLOT_FRAME);
            shrinkSlot(SLOT_BATTERY);
            shrinkSlot(SLOT_CAMERA);
            shrinkSlot(SLOT_MODULE);
            shrinkSlot(SLOT_FIRMWARE);
            for (int i = 0; i < n; i++) {
                shrinkSlot(SLOT_MOTOR_FIRST + i);
                shrinkSlot(SLOT_PROP_FIRST + i);
            }
            for (int i = 0; i < DroneAssembly.BAY_SIZE; i++) {
                container.setItem(SLOT_BAY_FIRST + i, ItemStack.EMPTY);
            }
        } finally {
            refreshing = false;
        }
        refresh();
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
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        boolean resultChanged = index == SLOT_RESULT;

        if (index < SLOT_COUNT) {
            if (!moveItemStackTo(stack, SLOT_COUNT, SLOT_COUNT + 36, true)) return ItemStack.EMPTY;
        } else if (canDisassemble(stack)) {
            if (!moveItemStackTo(stack, SLOT_RESULT, SLOT_RESULT + 1, false)) return ItemStack.EMPTY;
            resultChanged = true;
        } else if (stack.getItem() instanceof ComponentItem ci) {
            int from, to;
            switch (ci.getComponentType()) {
                case FRAME -> { from = SLOT_FRAME; to = SLOT_FRAME + 1; }
                case BATTERY -> { from = SLOT_BATTERY; to = SLOT_BATTERY + 1; }
                case CAMERA -> { from = SLOT_CAMERA; to = SLOT_CAMERA + 1; }
                case MOTOR -> { from = SLOT_MOTOR_FIRST; to = SLOT_MOTOR_FIRST + DroneAssembly.MAX_MOTORS; }
                case PROPELLER -> { from = SLOT_PROP_FIRST; to = SLOT_PROP_FIRST + DroneAssembly.MAX_MOTORS; }
                case MODULE -> { from = SLOT_MODULE; to = SLOT_MODULE + 1; }
                case FIRMWARE -> { from = SLOT_FIRMWARE; to = SLOT_FIRMWARE + 1; }
                default -> { return ItemStack.EMPTY; }
            }
            if (!moveItemStackTo(stack, from, to, false)) return ItemStack.EMPTY;
        } else if (DroneCardItem.isCard(stack)
                   && moveItemStackTo(stack, SLOT_BAY_FIRST, SLOT_BAY_FIRST + DroneAssembly.BAY_SIZE, false)) {
            // handled by the bay
        } else if (isConvertible(stack)) {
            if (!moveItemStackTo(stack, SLOT_CARD_IN, SLOT_CARD_IN + 1, false)) return ItemStack.EMPTY;
        } else if (isRepairMaterial(stack)) {
            if (!moveItemStackTo(stack, SLOT_REPAIR_MATERIAL, SLOT_REPAIR_MATERIAL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        slotsChanged(resultChanged ? resultContainer : container);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ASSEMBLY_STATION.get());
    }

    private int visibleMotorCount() {
        FrameSpec spec = buildAssembly().frameSpec();
        return spec != null ? spec.motorCount() : 4;
    }

    private class BaySlot extends Slot {
        BaySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return buildAssembly().bayAccepts(stack);
        }

        @Override
        public boolean isActive() {
            return hasItem() || buildAssembly().bayKind() != DroneAssembly.BayKind.NONE;
        }
    }

    private class TypedSlot extends Slot {
        private final ComponentType type;
        private final int motorIndex;

        TypedSlot(Container container, int slot, int x, int y, ComponentType type, int motorIndex) {
            super(container, slot, x, y);
            this.type = type;
            this.motorIndex = motorIndex;
        }

        // Any part of the right type goes anywhere. A part that doesn't suit the frame is
        // reported as a warning and paid for in the air, not blocked here.
        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof ComponentItem ci) || ci.getComponentType() != type) return false;
            return motorIndex < 0 || motorIndex < visibleMotorCount();
        }

        @Override
        public boolean isActive() {
            if (type == ComponentType.FRAME || hasItem()) return true;
            return motorIndex < 0 || motorIndex < visibleMotorCount();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
