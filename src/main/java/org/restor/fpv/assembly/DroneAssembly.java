package org.restor.fpv.assembly;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.restor.fpv.compat.CustomData;
import org.restor.fpv.component.ComponentItem;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.ComponentType;
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
            var data = CustomData.get(bay[i]);
            if (data != null) card.put("Data", data.copyTag());
            tag.put("Bay" + i, card);
        }
        return tag;
    }

    public void fitStockAvionics() {
        float size = frame.getItem() instanceof ComponentItem ci ? ci.getStats().frameSize() : 5f;
        if (fc.isEmpty()) {
            fc = new ItemStack((size >= 6f ? org.restor.fpv.component.ModComponents.FC_H7
                : org.restor.fpv.component.ModComponents.FC_F411).get());
        }
        if (vtx.isEmpty()) {
            vtx = new ItemStack((size >= 6f ? org.restor.fpv.component.ModComponents.VTX_800MW
                : size >= 3f ? org.restor.fpv.component.ModComponents.VTX_200MW
                : org.restor.fpv.component.ModComponents.VTX_25MW).get());
        }
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
        a.channel = org.restor.fpv.component.RadioChannel.clamp(tag.getInt("Channel"));
        for (int i = 0; i < MAX_MOTORS; i++) {
            if (tag.contains("Motor" + i)) a.motors[i] = readStack(tag, "Motor" + i);
            if (tag.contains("Prop" + i))  a.props[i]  = readStack(tag, "Prop" + i);
        }
        for (int i = 0; i < BAY_SIZE; i++) {
            if (!tag.contains("Bay" + i, Tag.TAG_COMPOUND)) continue;
            CompoundTag card = tag.getCompound("Bay" + i);
            ItemStack stack = new ItemStack(org.restor.fpv.item.ModItems.DRONE_CARD.get(),
                Math.max(1, card.getByte("Count")));
            if (card.contains("Data", Tag.TAG_COMPOUND)) {
                CustomData.set(stack, card.getCompound("Data"));
            }
            a.bay[i] = stack;
        }
        return a;
    }

    private static ItemStack readStack(CompoundTag tag, String key) {
        if (!tag.contains(key)) return ItemStack.EMPTY;
        CompoundTag component = tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
        ResourceLocation id = ResourceLocation.tryParse(component == null ? tag.getString(key) : component.getString("Id"));
        Item item = id != null ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
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
        int motorCount = spec != null ? spec.motorCount() : 0;
        var motorSpecs = new DroneAssemblyValidator.MotorSpec[MAX_MOTORS];
        var propSpecs = new DroneAssemblyValidator.PropSpec[MAX_MOTORS];
        float[] thrustFactors = new float[MAX_MOTORS];
        boolean frameCompatible = spec != null;

        frameCompatible &= compatibleIfPresent(battery);
        frameCompatible &= compatibleIfPresent(camera);
        frameCompatible &= compatibleIfPresent(module);
        frameCompatible &= compatibleIfPresent(firmware);
        frameCompatible &= compatibleIfPresent(vtx);
        frameCompatible &= compatibleIfPresent(fc);
        for (int i = 0; i < MAX_MOTORS; i++) {
            ComponentStats motor = stats(motors[i], ComponentType.MOTOR);
            ComponentStats prop = stats(props[i], ComponentType.PROPELLER);
            if (motor != null) {
                motorSpecs[i] = new DroneAssemblyValidator.MotorSpec(
                    motor.motorKv(), motor.motorPower(), motor.motorDiameter());
            }
            if (prop != null) propSpecs[i] = new DroneAssemblyValidator.PropSpec(prop.propDiameter());
            thrustFactors[i] = motorThrustFactor(i);
            if (i < motorCount) {
                frameCompatible &= compatibleIfPresent(motors[i]);
                frameCompatible &= compatibleIfPresent(props[i]);
            } else if (!motors[i].isEmpty() || !props[i].isEmpty()) {
                frameCompatible = false;
            }
        }

        ComponentStats batteryStats = stats(battery, ComponentType.BATTERY);
        var batterySpec = batteryStats == null ? null
            : new DroneAssemblyValidator.BatterySpec(batteryStats.batteryCells());
        return DroneAssemblyValidator.validate(new DroneAssemblyValidator.Input(
            motorCount,
            spec != null,
            stats(camera, ComponentType.CAMERA) != null,
            frameCompatible,
            motorSpecs,
            propSpecs,
            batterySpec,
            totalMassKg(),
            totalThrustN(),
            thrustFactors,
            ComponentCondition.ratio(camera),
            isPlaneFrame(frame),
            stats(fc, ComponentType.FLIGHT_CONTROLLER) != null,
            stats(vtx, ComponentType.VTX) != null
        ));
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
            case MOTOR -> wing ? compatiblePlaneMotor(frameSize, stats) : compatibleMotor(frameSize, stats);
            case PROPELLER -> wing ? compatiblePlaneProp(frameSize, stats) : compatibleProp(frameSize, stats);
            case BATTERY -> compatibleBattery(frame, stats);
            case CAMERA -> compatibleCamera(frameSize, stats);
            case MODULE -> compatibleModule(frameSize, wing, component);
            case FIRMWARE -> component.getItem() instanceof org.restor.fpv.component.FirmwareItem fi
                && frameSize >= fi.getFirmwareType().minFrameSize();
            default -> true;
        };
    }

    private static boolean compatibleMotor(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.motorDiameter() <= 12.0f && stats.motorPower() <= 0.35f;
        if (frameSize <= 4.0f) return stats.motorDiameter() >= 11.0f && stats.motorDiameter() <= 16.0f && stats.motorPower() <= 0.6f;
        if (frameSize <= 5.5f) return stats.motorDiameter() >= 20.0f && stats.motorDiameter() <= 30.0f;
        if (frameSize <= 6.5f) return stats.motorDiameter() >= 22.0f && stats.motorDiameter() <= 31.0f && stats.motorKv() <= 1800.0f;
        return stats.motorDiameter() >= 28.0f && stats.motorKv() <= 1500.0f;
    }

    private static boolean compatibleProp(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.propDiameter() <= 2.6f;
        if (frameSize <= 4.0f) return stats.propDiameter() <= 3.6f;
        if (frameSize <= 5.5f) return stats.propDiameter() >= 4.5f && stats.propDiameter() <= 5.2f;
        if (frameSize <= 6.5f) return stats.propDiameter() >= 5.0f && stats.propDiameter() <= 7.2f;
        return stats.propDiameter() >= 5.0f;
    }

    public static boolean isPlaneFrame(ItemStack frame) {
        return frame.getItem() instanceof org.restor.fpv.component.PlaneFrameItem;
    }

    private static boolean compatiblePlaneMotor(float frameSize, ComponentStats stats) {
        if (frameSize <= 7.0f) return stats.motorDiameter() <= 35.0f;
        if (frameSize <= 9.0f) return stats.motorDiameter() >= 28.0f;
        return stats.motorDiameter() >= 35.0f;
    }

    private static boolean compatiblePlaneProp(float frameSize, ComponentStats stats) {
        if (frameSize <= 7.0f) return stats.propDiameter() <= 10.0f;
        if (frameSize <= 9.0f) return stats.propDiameter() >= 8.0f && stats.propDiameter() <= 12.0f;
        return stats.propDiameter() >= 10.0f;
    }

    private static boolean compatiblePlaneBattery(float frameSize, ComponentStats stats) {
        if (stats.batteryCells() < 4) return false;
        return frameSize <= 8.0f ? stats.batteryCapacity() >= 650 : stats.batteryCapacity() >= 1300;
    }

    private static boolean compatibleBattery(ItemStack frame, ComponentStats stats) {
        ComponentStats fs = stats(frame, ComponentType.FRAME);
        float size = fs != null ? fs.frameSize() : 5f;
        return isPlaneFrame(frame) ? compatiblePlaneBattery(size, stats) : compatibleBattery(size, stats);
    }

    private static boolean compatibleBattery(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.batteryCells() == 1 && stats.batteryCapacity() <= 450;
        if (frameSize <= 4.0f) return stats.batteryCells() >= 3 && stats.batteryCells() <= 4 && stats.batteryCapacity() <= 900;
        if (frameSize <= 5.5f) return stats.batteryCells() >= 4 && stats.batteryCells() <= 6 && stats.batteryCapacity() <= 1500;
        if (frameSize <= 6.5f) return stats.batteryCells() >= 4 && stats.batteryCells() <= 6 && stats.batteryCapacity() >= 900;
        return stats.batteryCells() == 6 && stats.batteryCapacity() >= 900;
    }

    private static boolean compatibleCamera(float frameSize, ComponentStats stats) {
        return frameSize > 2.0f || stats.weight() <= 8.0f;
    }

    // Which modules fit which frames (FPV_IDEAS §4 "тяжелое оружие доступно только большим
    // рамам"). Tiny whoops (≤2") carry no modules at all.
    private static boolean compatibleModule(float frameSize, boolean wing, ItemStack module) {
        if (!(module.getItem() instanceof org.restor.fpv.component.ModuleItem mi)) return false;
        // An airframe has no belly rack and nothing to aim, so only the three modules a plane
        // can actually fly with fit the slot — the rest used to install and then do nothing.
        if (wing) {
            return switch (mi.getModuleType()) {
                case WARHEAD, STORAGE -> true;
                case TARGETING -> frameSize >= 8.0f;
                default -> false;
            };
        }
        return switch (mi.getModuleType()) {
            case PAYLOAD_BAY -> frameSize >= 3.5f;  // cinewhoop and up
            case WARHEAD -> frameSize >= 5.0f;      // 5" and up — it's 150g of explosives
            case DART_GUN -> frameSize >= 5.0f;     // recoil needs a 5"+ frame
            case STORAGE -> frameSize >= 3.5f;      // cargo box needs some belly room
            case TARGETING -> frameSize >= 8.0f;    // needs a big airframe to haul the optics
            case ROCKET_POD -> frameSize >= 5.0f;   // the rails and blast need a 5"+ frame
        };
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

    /** Link range factor of the installed video transmitter; a build with no VTX flies stock range. */
    private static final float NO_VTX_RANGE = 0.4f;

    public float vtxRangeMultiplier() {
        ComponentStats stats = stats(vtx, ComponentType.VTX);
        return stats != null ? stats.vtxRangeMultiplier() : NO_VTX_RANGE;
    }

    public boolean hasOsd() {
        ComponentStats stats = stats(fc, ComponentType.FLIGHT_CONTROLLER);
        return stats != null && stats.fcHasOsd();
    }

    /** Frame size in inches (prop class), or 5 when the frame slot is empty. */
    public float frameSizeInches() {
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        return frameStats != null ? frameStats.frameSize() : 5f;
    }

    private static ComponentStats stats(ItemStack stack, ComponentType type) {
        if (!stack.isEmpty() && stack.getItem() instanceof ComponentItem ci && ci.getComponentType() == type) {
            return ci.getStats();
        }
        return null;
    }

    // ---------------------------------------------------------------- derived stats

    // An unloading prop spins up toward its no-load RPM, and its airfoil still lifts slightly past
    // the geometric pitch, so thrust only dies at about 1.25x (no-load RPM x pitch).
    private static final float ZERO_THRUST_PITCH_RATIO = 1.25f;

    /** Axial airspeed in m/s at which the props stop making thrust. */
    public float propPitchSpeed() {
        float pitch = 0f, kv = 0f;
        int n = 0;
        for (int i = 0; i < MAX_MOTORS; i++) {
            ComponentStats prop = stats(props[i], ComponentType.PROPELLER);
            ComponentStats motor = stats(motors[i], ComponentType.MOTOR);
            if (prop == null || motor == null) continue;
            pitch += prop.propPitch();
            kv += motor.motorKv();
            n++;
        }
        if (n == 0) return 40f;
        ComponentStats b = stats(battery, ComponentType.BATTERY);
        float volts = (b != null && b.batteryCells() > 0 ? b.batteryCells() : 4) * 3.7f;
        float rpm = kv / n * volts * ZERO_THRUST_PITCH_RATIO;
        return Math.max(5f, rpm / 60f * (pitch / n) * 0.0254f);
    }

    /** Average prop diameter in inches; falls back to the frame size when no props are fitted. */
    public float propDiameterInches() {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < MAX_MOTORS; i++) {
            ComponentStats prop = stats(props[i], ComponentType.PROPELLER);
            if (prop == null) continue;
            sum += prop.propDiameter();
            n++;
        }
        if (n > 0) return sum / n;
        ComponentStats fs = stats(frame, ComponentType.FRAME);
        return fs != null ? fs.frameSize() : 5f;
    }

    public float totalMassKg() {
        ComponentStats fs = stats(frame, ComponentType.FRAME);
        float frameSize = fs != null ? fs.frameSize() : 5f;
        // Electronics (FC, ESC, RX, wiring, VTX, mounts) scale with frame size — a whoop carries
        // a few grams, a 7" a full stack. A flat baseline made small builds absurdly heavy.
        float g = isPlaneFrame(frame) ? 8f + frameSize * 14f : 3.12f * frameSize * frameSize;
        g += weight(frame) + weight(battery) + weight(camera) + weight(module) + weight(firmware);
        g += weight(vtx) + weight(fc);
        for (int i = 0; i < MAX_MOTORS; i++) g += weight(motors[i]) + weight(props[i]);
        return Math.max(0.02f, g / 1000f);
    }

    private static float weight(ItemStack s) {
        return s.getItem() instanceof ComponentItem ci ? ci.getStats().weight() : 0;
    }

    /** Relative thrust factor of motor slot i (motor power × prop geometry/efficiency). */
    public float motorThrustFactor(int i) {
        ComponentStats motor = stats(motors[i], ComponentType.MOTOR);
        ComponentStats prop = stats(props[i], ComponentType.PROPELLER);
        if (motor == null || prop == null) return 0;
        // Momentum theory: static thrust goes as (shaft power × disc diameter)^(2/3), so a small
        // prop loses far less thrust than its area suggests — it just spins much faster. Scaling
        // thrust linearly with diameter on top of the already-small motorPower double-counted the
        // size penalty and left whoops and cinewhoops unable to lift themselves. Normalized so a
        // 2207 on a 5x4x3 still reads 1.0.
        // A multirotor prop is thrust-limited by its disc, not its motor: a 65mm whoop prop makes
        // about 1/20 of a 5" prop. Planes keep the gentler power-limited curve.
        double diameterExp = isPlaneFrame(frame) ? 2.0 / 3.0 : 2.0;
        float propFactor = prop.propEfficiency()
            * (float) Math.pow(prop.propDiameter() / 5f, diameterExp)
            * (float) Math.pow(Math.max(0.1f, prop.propPitch()) / 4f, 0.3);
        return (float) Math.pow(motor.motorPower(), 2.0 / 3.0) * propFactor * slotEfficiency(i)
            * ComponentCondition.ratio(motors[i]) * ComponentCondition.ratio(props[i]);
    }

    /**
     * How well the parts in motor slot i work together: 1.0 when everything fits, lower when
     * the prop is wrong for the motor or either part is wrong for the frame. Nothing is
     * forbidden — a mismatched corner simply pulls less, which computeTrim turns into drift.
     */
    public float slotEfficiency(int i) {
        ComponentStats motor = stats(motors[i], ComponentType.MOTOR);
        ComponentStats prop = stats(props[i], ComponentType.PROPELLER);
        if (motor == null || prop == null) return 0;
        float efficiency = 1f;
        var motorSpec = new DroneAssemblyValidator.MotorSpec(
            motor.motorKv(), motor.motorPower(), motor.motorDiameter());
        if (!DroneAssemblyValidator.isMotorPropCompatible(motorSpec,
                new DroneAssemblyValidator.PropSpec(prop.propDiameter()), isPlaneFrame(frame))) {
            efficiency *= 0.6f;
        }
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        if (frameStats != null) {
            boolean wing = isPlaneFrame(frame);
            float size = frameStats.frameSize();
            if (!(wing ? compatiblePlaneMotor(size, motor) : compatibleMotor(size, motor))) efficiency *= 0.82f;
            if (!(wing ? compatiblePlaneProp(size, prop) : compatibleProp(size, prop))) efficiency *= 0.82f;
        }
        return efficiency;
    }

    /**
     * How hard the build runs outside its spec. 1.0 = everything within limits; higher values
     * wear motors and props out over the course of a flight (ModularDroneEntity).
     */
    public float stressFactor() {
        FrameSpec spec = frameSpec();
        int n = spec != null ? spec.motorCount() : 0;
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        ComponentStats batteryStats = stats(battery, ComponentType.BATTERY);
        float stress = 1f;
        if (frameStats != null && batteryStats != null && !compatibleBattery(frame, batteryStats)) {
            stress += 0.4f;
        }
        int mismatched = 0;
        for (int i = 0; i < n; i++) {
            ComponentStats motor = stats(motors[i], ComponentType.MOTOR);
            if (motor == null) continue;
            if (batteryStats != null && !DroneAssemblyValidator.isMotorBatteryCompatible(
                    new DroneAssemblyValidator.MotorSpec(
                        motor.motorKv(), motor.motorPower(), motor.motorDiameter()),
                    new DroneAssemblyValidator.BatterySpec(batteryStats.batteryCells()))) {
                mismatched++;
            }
            if (slotEfficiency(i) < 1f) mismatched++;
        }
        return n == 0 ? stress : stress + 0.6f * mismatched / n;
    }

    /** Max total thrust in Newtons. */
    public float totalThrustN() {
        FrameSpec spec = frameSpec();
        if (spec == null) return 1;
        float sum = 0;
        for (int i = 0; i < spec.motorCount(); i++) sum += motorThrustFactor(i);
        float frameFactor = 0.5f + 0.5f * ComponentCondition.ratio(frame);
        float perPower = isPlaneFrame(frame) ? THRUST_N_PER_POWER : ROTOR_THRUST_N_PER_POWER;
        return Math.max(1f, sum * perPower * batteryThrustFactor() * frameFactor);
    }

    // More battery cells = more voltage = more motor RPM = more thrust. Normalized to the frame's
    // nominal cell count (a whoop on 1S, a 5" on 4S, a 7"/octo on 6S all read 1.0), so it only
    // rewards over-volting / penalizes under-volting — otherwise a heavier 6S pack only ADDED
    // mass and a tiny 1S read the HIGHEST TWR, which was backwards.
    private float batteryThrustFactor() {
        ComponentStats b = stats(battery, ComponentType.BATTERY);
        if (b == null || b.batteryCells() <= 0) return 1f;
        ComponentStats fr = stats(frame, ComponentType.FRAME);
        float size = fr != null ? fr.frameSize() : 5f;
        float refCells = size <= 2f ? 1f : size <= 6.5f ? 4f : 6f;
        float voltageFactor = (float) Math.pow(b.batteryCells() / refCells, 0.7);
        float fitFactor = fr != null && !compatibleBattery(frame, b) ? 0.9f : 1f;
        return voltageFactor * fitFactor * (0.5f + 0.5f * ComponentCondition.ratio(battery));
    }

    /**
     * Constant attitude trim in °/s at FULL throttle caused by thrust imbalance
     * between motor slots (e.g. one weaker prop). Returns {roll, pitch, yaw}.
     * Sign conventions match DroneEntity: positive roll = right bank, positive
     * pitch rate = nose down, yaw uses stick sign (positive = turn right).
     */
    public float[] computeTrim() {
        FrameSpec spec = frameSpec();
        if (spec == null) return new float[]{0, 0, 0};
        int n = spec.motorCount();
        float avg = 0;
        for (int i = 0; i < n; i++) avg += motorThrustFactor(i);
        avg /= n;
        if (avg <= 0) return new float[]{0, 0, 0};

        float roll = 0, pitch = 0, yaw = 0;
        for (int i = 0; i < n; i++) {
            float rel = motorThrustFactor(i) / avg - 1f; // + = this corner is stronger
            MountPoint m = spec.motorMounts().get(i);
            float nx = m.x() / spec.armLength();
            float nz = m.z() / spec.armLength();
            // Stronger right side (+x) lifts right → banks LEFT (negative roll).
            roll  += -rel * nx;
            // Stronger front (-z) lifts nose → pitches UP (negative pitch rate) —
            // rel * nz is already negative for front mounts.
            pitch += rel * nz;
            // Standard props-out spin layout: even slots CCW, odd CW. A stronger
            // CCW motor adds CW body yaw (turn right = positive stick sense).
            yaw   += rel * (i % 2 == 0 ? 1f : -1f);
        }
        // Full mismatch on one corner of a quad ≈ 0.25 → ~30°/s trim at full throttle.
        float k = 120f;
        return new float[]{roll * k, pitch * k, yaw * k * 0.5f};
    }

    /** Flight characteristics derived from the installed components. */
    public DroneData buildDroneData() {
        FrameSpec spec = frameSpec();
        ComponentStats frameStats = stats(frame, ComponentType.FRAME);
        ComponentStats batteryStats = stats(battery, ComponentType.BATTERY);
        ComponentStats cameraStats = stats(camera, ComponentType.CAMERA);

        float mass = totalMassKg();
        float thrust = totalThrustN();
        float frameSize = frameStats != null ? frameStats.frameSize() : 5f;
        // Drag area grows with frame size. Was 0.005·size (5" ≈ 0.025) which capped top speed
        // ~2× too low vs a real 5"; 0.0032·size (5" ≈ 0.016) gives ~170 km/h headroom.
        FrameTuning tuning = spec != null ? spec.tuning() : FrameTuning.DEFAULT;
        float dragCdA = 0.0032f * frameSize * tuning.dragFactor();
        float camTilt = cameraStats != null ? cameraStats.cameraTiltAngle() : 20f;
        int capacity = batteryStats != null
            ? Math.max(1, Math.round(batteryStats.batteryCapacity()
                * (0.5f + 0.5f * ComponentCondition.ratio(battery))))
            : 650;

        // Bigger frames rotate slower; responsiveness drops with size too.
        float rate = Math.max(200f, 700f - frameSize * 50f) * tuning.agility();
        float resp = Math.max(10f, 26f - frameSize * 1.5f) * tuning.responsiveness();

        if (spec != null && spec.isHeavyLift()) {
            camTilt = -45f;
            rate *= 0.55f;
            resp *= 0.75f;
        }
        // The tailsitter's camera looks straight out of the nose, i.e. along the thrust axis.
        if (spec != null && spec.isTailsitter()) {
            camTilt = 90f;
        }

        return new DroneData(
            DroneData.DroneType.CUSTOM, "Custom Build", 1.0f,
            rate * 0.9f, rate, rate * 0.6f,
            mass, dragCdA, capacity,
            camTilt, thrust, spec != null ? spec.armLength() : 0.5f,
            resp, propPitchSpeed()
        );
    }
}
