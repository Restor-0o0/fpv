package org.restor.fpv.assembly;

import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.FirmwareType;
import org.restor.fpv.component.ModuleType;

/**
 * Which part fits which airframe, by the numbers alone. A misfit is never forbidden —
 * DroneAssembly only turns it into a warning, less thrust on that corner and faster wear —
 * but every rule that decides "this belongs on that frame" lives here.
 */
public final class ComponentFit {
    public static boolean motor(float frameSize, boolean wing, ComponentStats stats) {
        return wing ? planeMotor(frameSize, stats) : rotorMotor(frameSize, stats);
    }

    public static boolean propeller(float frameSize, boolean wing, ComponentStats stats) {
        return wing ? planeProp(frameSize, stats) : rotorProp(frameSize, stats);
    }

    public static boolean battery(float frameSize, boolean wing, ComponentStats stats) {
        return wing ? planeBattery(frameSize, stats) : rotorBattery(frameSize, stats);
    }

    public static boolean camera(float frameSize, ComponentStats stats) {
        return frameSize > 2.0f || stats.weight() <= 8.0f;
    }

    public static boolean firmware(float frameSize, FirmwareType type) {
        return frameSize >= type.minFrameSize();
    }

    // Which modules fit which frames (FPV_IDEAS §4 "тяжелое оружие доступно только большим
    // рамам"). Tiny whoops (≤2") carry no modules at all.
    public static boolean module(float frameSize, boolean wing, ModuleType type) {
        // An airframe has no belly rack and nothing to aim, so only the three modules a plane
        // can actually fly with fit the slot — the rest used to install and then do nothing.
        if (wing) {
            return switch (type) {
                case WARHEAD, STORAGE -> true;
                case TARGETING -> frameSize >= 8.0f;
                default -> false;
            };
        }
        return switch (type) {
            case PAYLOAD_BAY -> frameSize >= 3.5f;  // cinewhoop and up
            case WARHEAD -> frameSize >= 5.0f;      // 5" and up — it's 150g of explosives
            case DART_GUN -> frameSize >= 5.0f;     // recoil needs a 5"+ frame
            case STORAGE -> frameSize >= 3.5f;      // cargo box needs some belly room
            case TARGETING -> frameSize >= 8.0f;    // needs a big airframe to haul the optics
            case ROCKET_POD -> frameSize >= 5.0f;   // the rails and blast need a 5"+ frame
        };
    }

    private static boolean rotorMotor(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.motorDiameter() <= 12.0f && stats.motorPower() <= 0.35f;
        if (frameSize <= 4.0f) return stats.motorDiameter() >= 11.0f && stats.motorDiameter() <= 16.0f && stats.motorPower() <= 0.6f;
        if (frameSize <= 5.5f) return stats.motorDiameter() >= 20.0f && stats.motorDiameter() <= 30.0f;
        if (frameSize <= 6.5f) return stats.motorDiameter() >= 22.0f && stats.motorDiameter() <= 31.0f && stats.motorKv() <= 1800.0f;
        return stats.motorDiameter() >= 28.0f && stats.motorKv() <= 1500.0f;
    }

    private static boolean rotorProp(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.propDiameter() <= 2.6f;
        if (frameSize <= 4.0f) return stats.propDiameter() <= 3.6f;
        if (frameSize <= 5.5f) return stats.propDiameter() >= 4.5f && stats.propDiameter() <= 5.2f;
        if (frameSize <= 6.5f) return stats.propDiameter() >= 5.0f && stats.propDiameter() <= 7.2f;
        return stats.propDiameter() >= 5.0f;
    }

    private static boolean rotorBattery(float frameSize, ComponentStats stats) {
        if (frameSize <= 2.0f) return stats.batteryCells() == 1 && stats.batteryCapacity() <= 450;
        if (frameSize <= 4.0f) return stats.batteryCells() >= 3 && stats.batteryCells() <= 4 && stats.batteryCapacity() <= 900;
        if (frameSize <= 5.5f) return stats.batteryCells() >= 4 && stats.batteryCells() <= 6 && stats.batteryCapacity() <= 1500;
        if (frameSize <= 6.5f) return stats.batteryCells() >= 4 && stats.batteryCells() <= 6 && stats.batteryCapacity() >= 900;
        return stats.batteryCells() == 6 && stats.batteryCapacity() >= 900;
    }

    private static boolean planeMotor(float frameSize, ComponentStats stats) {
        if (frameSize <= 7.0f) return stats.motorDiameter() <= 35.0f;
        if (frameSize <= 9.0f) return stats.motorDiameter() >= 28.0f;
        return stats.motorDiameter() >= 35.0f;
    }

    private static boolean planeProp(float frameSize, ComponentStats stats) {
        if (frameSize <= 7.0f) return stats.propDiameter() <= 10.0f;
        if (frameSize <= 9.0f) return stats.propDiameter() >= 8.0f && stats.propDiameter() <= 12.0f;
        return stats.propDiameter() >= 10.0f;
    }

    private static boolean planeBattery(float frameSize, ComponentStats stats) {
        if (stats.batteryCells() < 4) return false;
        return frameSize <= 8.0f ? stats.batteryCapacity() >= 650 : stats.batteryCapacity() >= 1300;
    }

    private ComponentFit() {}
}
