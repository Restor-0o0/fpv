package org.restor.fpv.assembly;

import java.util.List;

/**
 * Physical layout of a frame: how many motors it takes and where every component
 * attaches. Motor order convention: index 0 = front-right, 1 = front-left,
 * 2 = rear-right, 3 = rear-left, then extra pairs (mid-right, mid-left, ...) for
 * hexa/octo frames. Spin direction alternates per the standard "props-out" layout:
 * even indices CCW, odd indices CW.
 */
public record FrameSpec(
    int motorCount,
    List<MountPoint> motorMounts,   // size == motorCount, order as documented above
    MountPoint batteryMount,        // battery sits on top of the frame plate
    MountPoint cameraMount,         // camera at the nose
    MountPoint moduleMount,         // payload/utility module slot (under the belly)
    float armLength,                // center→motor distance in blocks (ground effect)
    FrameKind kind,
    FrameTuning tuning,
    FrameRole role
) {
    public FrameSpec {
        if (motorMounts.size() != motorCount) {
            throw new IllegalArgumentException("motorMounts size must equal motorCount");
        }
    }

    public FrameSpec(int motorCount, List<MountPoint> motorMounts, MountPoint batteryMount,
                     MountPoint cameraMount, MountPoint moduleMount, float armLength,
                     FrameKind kind, FrameTuning tuning) {
        this(motorCount, motorMounts, batteryMount, cameraMount, moduleMount, armLength,
            kind, tuning, FrameRole.STANDARD);
    }

    public boolean isFixedWing() {
        return kind == FrameKind.FIXED_WING;
    }

    /** Six motors and up: an agricultural/heavy-lift rig, not an FPV racer. */
    public boolean isHeavyLift() {
        return kind == FrameKind.MULTIROTOR && motorCount >= 6;
    }

    /**
     * Tailsitter: the rotors sit on the fuselage axis, so the airframe rests and hovers
     * standing on its tail and accelerates by tipping the whole tube over, rocket style.
     */
    public boolean isTailsitter() {
        return role == FrameRole.INTERCEPTOR;
    }
}
