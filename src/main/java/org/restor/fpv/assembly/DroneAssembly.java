package org.restor.fpv.assembly;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.component.ComponentCatalog;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;
import org.restor.fpv.component.ModComponents;
import org.restor.fpv.data.DroneData;

/**
 * A modular drone build: frame + battery + camera + per-slot motors and props.
 * Serialized into item/entity NBT; all flight characteristics are derived from the
 * installed components, including per-motor thrust imbalance (mismatched props →
 * constant pitch/roll/yaw trim).
 */
public class DroneAssembly {
    public static final int MAX_MOTORS = 8;
    public static final int BAY_SIZE = 4;
    // Thrust per motor-power unit. A 4×2207+5x4x3 4S build lands at ~TWR 5 — enough that level
    // cruise sits at ~40-45° pitch (not 65°, which TWR ~8+ forced), while low drag still gives a
    // high top speed. More cells (6S) or stronger motors push TWR up for a punchier build.
    private static final float THRUST_N_PER_POWER = 6.6f;
    // Multirotors are calibrated to real static thrust: a 2207 on a 5x4 makes ~11 N per motor.
    private static final float ROTOR_THRUST_N_PER_POWER = 11f;

    public ItemStack frame = ItemStack.EMPTY;
    public ItemStack battery = ItemStack.EMPTY;
    public ItemStack camera = ItemStack.EMPTY;
    public ItemStack module = ItemStack.EMPTY; // optional payload/utility module (FPV_IDEAS §4-5)
    public ItemStack firmware = ItemStack.EMPTY; // optional autonomy chip, independent of the module slot
    public ItemStack vtx = ItemStack.EMPTY;      // video transmitter: sets the link range and the channel
    public ItemStack fc = ItemStack.EMPTY;       // flight controller: OSD and blackbox
    public int channel = org.restor.fpv.component.RadioChannel.DEFAULT;
    public final ItemStack[] motors = new ItemStack[MAX_MOTORS];
    public final ItemStack[] props = new ItemStack[MAX_MOTORS];
    /** Drone cards stowed aboard — only frames whose role opens a bay ever hold any. */
    public final ItemStack[] bay = new ItemStack[BAY_SIZE];

    /** What the frame's extra bench slots hold, if anything. */
    public enum BayKind { NONE, STRIKE, CARRIER }

    public DroneAssembly() {
        java.util.Arrays.fill(motors, ItemStack.EMPTY);
        java.util.Arrays.fill(props, ItemStack.EMPTY);
        java.util.Arrays.fill(bay, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        putStack(tag, "Frame", frame);
        putStack(tag, "Battery", battery);
        putStack(tag, "Camera", camera);
        if (!module.isEmpty()) putStack(tag, "Module", module);
        if (!firmware.isEmpty()) putStack(tag, "Firmware", firmware);
        if (!vtx.isEmpty()) putStack(tag, "Vtx", vtx);
        if (!fc.isEmpty()) putStack(tag, "Fc", fc);
        tag.putInt("Channel", channel);
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (!motors[i].isEmpty()) putStack(tag, "Motor" + i, motors[i]);
            if (!props[i].isEmpty())  putStack(tag, "Prop" + i,  props[i]);
        }
        for (int i = 0; i < BAY_SIZE; i++) {
            if (bay[i].isEmpty()) continue;
            CompoundTag card = new CompoundTag();
            card.putByte("Count", (byte) bay[i].getCount());
            var data = bay[i].get(DataComponents.CUSTOM_DATA);
            if (data != null) card.put("Data", data.copyTag());
            tag.put("Bay" + i, card);
        }
        return tag;
    }

    public void fitStockAvionics() {
        float size = frame.getItem() instanceof ComponentItem ci ? ci.getStats().frameSize() : 5f;
        if (fc.isEmpty()) fc = stock(ComponentCatalog.stockFc(size));
        if (vtx.isEmpty()) vtx = stock(ComponentCatalog.stockVtx(size));
    }

    private static ItemStack stock(ComponentStats stats) {
        var item = ModComponents.itemFor(stats);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    private static void putStack(CompoundTag tag, String key, ItemStack stack) {
        CompoundTag component = new CompoundTag();
        component.putString("Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        ComponentCondition.write(component, ComponentCondition.get(stack));
        tag.put(key, component);
    }

    public static DroneAssembly load(CompoundTag tag) {
        DroneAssembly a = new DroneAssembly();
        a.frame = readStack(tag, "Frame");
        a.battery = readStack(tag, "Battery");
        a.camera = readStack(tag, "Camera");
        if (tag.contains("Module")) a.module = readStack(tag, "Module");
        if (tag.contains("Firmware")) a.firmware = readStack(tag, "Firmware");
        if (tag.contains("Vtx")) a.vtx = readStack(tag, "Vtx");
        if (tag.contains("Fc")) a.fc = readStack(tag, "Fc");
        // Builds saved before avionics were required carry no channel either; give them stock parts.
        if (!tag.contains("Channel") && !a.frame.isEmpty() && !a.isBareFrame()) a.fitStockAvionics();
        a.channel = org.restor.fpv.component.RadioChannel.clamp(tag.getIntOr("Channel", 0));
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (tag.contains("Motor" + i)) a.motors[i] = readStack(tag, "Motor" + i);
            if (tag.contains("Prop" + i))  a.props[i]  = readStack(tag, "Prop" + i);
        }
        for (int i = 0; i < BAY_SIZE; i++) {
            CompoundTag card = tag.getCompound("Bay" + i).orElse(null);
            if (card == null) continue;
            ItemStack stack = new ItemStack(org.restor.fpv.item.ModItems.DRONE_CARD.get(),
                Math.max(1, card.getByteOr("Count", (byte) 0)));
            if (card.contains("Data")) {
                CustomData.set(DataComponents.CUSTOM_DATA, stack, card.getCompoundOrEmpty("Data"));
            }
            a.bay[i] = stack;
        }
        return a;
    }

    private static ItemStack readStack(CompoundTag tag, String key) {
        if (!tag.contains(key)) return ItemStack.EMPTY;
        CompoundTag component = tag.getCompound(key).orElse(null);
        Identifier id = Identifier.tryParse(component == null ? tag.getStringOr(key, "") : component.getStringOr("Id", ""));
        Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (component != null) ComponentCondition.set(stack, ComponentCondition.read(component));
        return stack;
    }

    // ---------------------------------------------------------------- structure

    public FrameSpec frameSpec() {
        return frame.isEmpty() ? null : FrameSpecs.byItem(frame.getItem());
    }

    /** Complete = frame + battery + camera + a motor AND a prop in every frame slot. */
    public boolean isComplete() {
        return validate().isComplete();
    }

    /** Nothing bolted on yet: an airframe that can be set down but never flown. */
    public boolean isBareFrame() {
        if (frame.isEmpty()) return false;
        if (!battery.isEmpty() || !camera.isEmpty() || !module.isEmpty() || !firmware.isEmpty()) return false;
        if (!vtx.isEmpty() || !fc.isEmpty()) return false;
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (!motors[i].isEmpty() || !props[i].isEmpty()) return false;
        }
        for (int i = 0; i < BAY_SIZE; i++) {
            if (!bay[i].isEmpty()) return false;
        }
        return true;
    }

    public DroneAssemblyValidator.ValidationResult validate() {
        FrameSpec spec = frameSpec();
        return parts().validate(spec, frameComponentsCompatible(spec));
    }

    private boolean frameComponentsCompatible(FrameSpec spec) {
        int motorCount = spec != null ? spec.motorCount() : 0;
        boolean compatible = spec != null;
        compatible &= compatibleIfPresent(battery);
        compatible &= compatibleIfPresent(camera);
        compatible &= compatibleIfPresent(module);
        compatible &= compatibleIfPresent(firmware);
        compatible &= compatibleIfPresent(vtx);
        compatible &= compatibleIfPresent(fc);
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (i < motorCount) {
                compatible &= compatibleIfPresent(motors[i]);
                compatible &= compatibleIfPresent(props[i]);
            } else if (!motors[i].isEmpty() || !props[i].isEmpty()) {
                compatible = false;
            }
        }
        return compatible;
    }

    private boolean compatibleIfPresent(ItemStack component) {
        return component.isEmpty() || isCompatible(frame, component);
    }

    public static boolean isCompatible(ItemStack frame, ItemStack component) {
        if (frame.isEmpty() || component.isEmpty()) return false;
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        if (frameStats == null || !(component.getItem() instanceof ComponentItem ci)) return false;
        ComponentStats stats = ci.getStats();
        float frameSize = frameStats.frameSize();
        boolean wing = isPlaneFrame(frame);
        boolean planePart = component.getItem() instanceof org.restor.fpv.component.PlaneMotorItem
            || component.getItem() instanceof org.restor.fpv.component.PlanePropellerItem;
        if (planePart != wing && (stats.type() == ComponentType.MOTOR || stats.type() == ComponentType.PROPELLER)) {
            return false;
        }
        return switch (stats.type()) {
            case FRAME -> true;
            case MOTOR -> ComponentFit.motor(frameSize, wing, stats);
            case PROPELLER -> ComponentFit.propeller(frameSize, wing, stats);
            case BATTERY -> ComponentFit.battery(frameSize, wing, stats);
            case CAMERA -> ComponentFit.camera(frameSize, stats);
            case MODULE -> component.getItem() instanceof org.restor.fpv.component.ModuleItem mi
                && ComponentFit.module(frameSize, wing, mi.getModuleType());
            case FIRMWARE -> component.getItem() instanceof org.restor.fpv.component.FirmwareItem fi
                && ComponentFit.firmware(frameSize, fi.getFirmwareType());
            default -> true;
        };
    }

    public static boolean isPlaneFrame(ItemStack frame) {
        return frame.getItem() instanceof org.restor.fpv.component.PlaneFrameItem;
    }

    private static boolean compatibleBattery(ItemStack frame, ComponentStats stats) {
        ComponentStats fs = stats(frame, ComponentType.FRAME);
        float size = fs != null ? fs.frameSize() : 5f;
        return ComponentFit.battery(size, isPlaneFrame(frame), stats);
    }

    // ------------------------------------------------------------------ bay

    public BayKind bayKind() {
        FrameSpec spec = frameSpec();
        if (spec == null) return BayKind.NONE;
        if (spec.role() == FrameRole.CARRIER) return BayKind.CARRIER;
        if (spec.role() == FrameRole.SPOTTER
            && moduleType() == org.restor.fpv.component.ModuleType.TARGETING) return BayKind.STRIKE;
        return BayKind.NONE;
    }

    public boolean bayAccepts(ItemStack stack) {
        if (!DroneCardItem.isCard(stack)) return false;
        return switch (bayKind()) {
            // A card that itself owns a bay would nest aircraft inside aircraft.
            case STRIKE -> !DroneCardItem.hasBay(stack);
            case CARRIER -> !DroneCardItem.isWing(stack) && DroneCardItem.frameSize(stack) <= 7.0f;
            case NONE -> false;
        };
    }

    public boolean bayEmpty() {
        for (ItemStack s : bay) if (!s.isEmpty()) return false;
        return true;
    }

    public int bayCount() {
        int n = 0;
        for (ItemStack s : bay) n += s.getCount();
        return n;
    }

    /** Per-frame flight character (agility, drag, toughness, lift capacity). */
    public FrameTuning tuning() {
        FrameSpec spec = frameSpec();
        return spec != null ? spec.tuning() : FrameTuning.DEFAULT;
    }

    public FrameKind frameKind() {
        FrameSpec spec = frameSpec();
        return spec != null ? spec.kind() : FrameKind.MULTIROTOR;
    }

    /** Installed module's type, or null when the slot is empty. */
    public org.restor.fpv.component.ModuleType moduleType() {
        return module.getItem() instanceof org.restor.fpv.component.ModuleItem mi ? mi.getModuleType() : null;
    }

    /** Installed firmware's type, or null when the firmware slot is empty. */
    public org.restor.fpv.component.FirmwareType firmwareType() {
        return firmware.getItem() instanceof org.restor.fpv.component.FirmwareItem fi ? fi.getFirmwareType() : null;
    }

    public record RepairTarget(ComponentType type, int index, ItemStack stack, int condition) {}

    public RepairTarget repairTarget() {
        RepairTarget target = null;
        target = damagedBefore(target, ComponentType.FRAME, -1, frame);
        target = damagedBefore(target, ComponentType.BATTERY, -1, battery);
        target = damagedBefore(target, ComponentType.CAMERA, -1, camera);
        target = damagedBefore(target, ComponentType.MODULE, -1, module);
        target = damagedBefore(target, ComponentType.FIRMWARE, -1, firmware);
        for (int i = 0; i < MAX_MOTORS; i++) {
            target = damagedBefore(target, ComponentType.MOTOR, i, motors[i]);
            target = damagedBefore(target, ComponentType.PROPELLER, i, props[i]);
        }
        return target;
    }

    public int minimumCondition() {
        RepairTarget target = repairTarget();
        return target == null ? ComponentCondition.MAX : target.condition();
    }

    private static RepairTarget damagedBefore(RepairTarget current, ComponentType type, int index, ItemStack stack) {
        if (stack.isEmpty()) return current;
        int condition = ComponentCondition.get(stack);
        if (condition >= ComponentCondition.MAX || current != null && condition >= current.condition()) return current;
        return new RepairTarget(type, index, stack, condition);
    }

    private static ComponentStats stats(ItemStack stack, ComponentType type) {
        if (!stack.isEmpty() && stack.getItem() instanceof ComponentItem ci && ci.getComponentType() == type) {
            return ci.getStats();
        }
        return null;
    }

    // ---------------------------------------------------------------- derived stats

    /** The build as plain numbers — everything below is worked out from these. */
    public AssemblyParts parts() {
        ComponentStats[] motorStats = new ComponentStats[MAX_MOTORS];
        ComponentStats[] propStats = new ComponentStats[MAX_MOTORS];
        float[] motorConditions = new float[MAX_MOTORS];
        float[] propConditions = new float[MAX_MOTORS];
        for (int i = 0; i < MAX_MOTORS; i++) {
            motorStats[i] = stats(motors[i], ComponentType.MOTOR);
            propStats[i] = stats(props[i], ComponentType.PROPELLER);
            motorConditions[i] = ComponentCondition.ratio(motors[i]);
            propConditions[i] = ComponentCondition.ratio(props[i]);
        }
        return new AssemblyParts(
            stats(frame, ComponentType.FRAME), ComponentCondition.ratio(frame),
            stats(battery, ComponentType.BATTERY), ComponentCondition.ratio(battery),
            stats(camera, ComponentType.CAMERA), ComponentCondition.ratio(camera),
            stats(module, ComponentType.MODULE),
            stats(firmware, ComponentType.FIRMWARE),
            stats(vtx, ComponentType.VTX),
            stats(fc, ComponentType.FLIGHT_CONTROLLER),
            motorStats, motorConditions, propStats, propConditions,
            isPlaneFrame(frame));
    }

    public float vtxRangeMultiplier() {
        return parts().vtxRangeMultiplier();
    }

    public boolean hasOsd() {
        return parts().hasOsd();
    }

    /** Frame size in inches (prop class), or 5 when the frame slot is empty. */
    public float frameSizeInches() {
        return parts().frameSize();
    }

    /** Axial airspeed in m/s at which the props stop making thrust. */
    public float propPitchSpeed() {
        return parts().propPitchSpeed();
    }

    /** Average prop diameter in inches; falls back to the frame size when no props are fitted. */
    public float propDiameterInches() {
        return parts().propDiameterInches();
    }

    public float totalMassKg() {
        return parts().totalMassKg();
    }

    /** Relative thrust factor of motor slot i (motor power x prop geometry/efficiency). */
    public float motorThrustFactor(int i) {
        return parts().motorThrustFactor(i);
    }

    /** How well the parts in motor slot i work together; 1.0 when everything fits. */
    public float slotEfficiency(int i) {
        return parts().slotEfficiency(i);
    }

    /** How hard the build runs outside its spec; above 1.0 it wears parts out in flight. */
    public float stressFactor() {
        FrameSpec spec = frameSpec();
        return parts().stressFactor(spec != null ? spec.motorCount() : 0);
    }

    /** Max total thrust in Newtons. */
    public float totalThrustN() {
        FrameSpec spec = frameSpec();
        return spec == null ? 1 : parts().totalThrustN(spec.motorCount());
    }

    /** Constant attitude trim in deg/s at full throttle caused by thrust imbalance. */
    public float[] computeTrim() {
        return parts().computeTrim(frameSpec());
    }

    /** Flight characteristics derived from the installed components. */
    public DroneData buildDroneData() {
        return parts().buildDroneData(frameSpec());
    }
}
