package org.restor.fpv.component;

import org.restor.fpv.entity.DroneMode;

import java.util.List;

/**
 * What a firmware chip teaches the flight controller. The firmware slot is separate
 * from the payload/utility module slot: a drone can carry a dart gun AND a guardian
 * firmware, and the chip decides which autonomous modes the owner can cycle through
 * by right-clicking the drone.
 */
public enum FirmwareType {
    // Помощник: trails the owner, carries cargo, never picks a fight.
    COMPANION("companion", 2.0f, List.of(DroneMode.FOLLOW)),
    // Разведчик: sweeps for hostiles and marks them, still refuses to engage.
    SCOUT("scout", 2.0f, List.of(DroneMode.FOLLOW, DroneMode.PATROL)),
    // Охранник: the full autonomy set — follows, patrols, defends the owner or a spot.
    GUARDIAN("guardian", 5.0f, List.of(DroneMode.FOLLOW, DroneMode.GUARD_PLAYER,
                                       DroneMode.GUARD_AREA, DroneMode.PATROL)),
    // Модуль жителя: the drone answers a right-click with a trader's offers.
    TRADER("trader", 3.5f, List.of(DroneMode.FOLLOW, DroneMode.GUARD_AREA));

    private final String id;
    private final float minFrameSize;
    private final List<DroneMode> modes;

    FirmwareType(String id, float minFrameSize, List<DroneMode> modes) {
        this.id = id;
        this.minFrameSize = minFrameSize;
        this.modes = modes;
    }

    public String id() { return id; }
    public float minFrameSize() { return minFrameSize; }

    /** Modes the owner can cycle, always ending back on OFF. */
    public List<DroneMode> modes() { return modes; }

    public boolean canFight() { return this == GUARDIAN; }
    public boolean marksTargets() { return this == SCOUT || this == GUARDIAN; }
    public boolean trades() { return this == TRADER; }

    public DroneMode next(DroneMode current) {
        int i = modes.indexOf(current);
        if (i < 0) return modes.get(0);
        return i + 1 < modes.size() ? modes.get(i + 1) : DroneMode.OFF;
    }
}
