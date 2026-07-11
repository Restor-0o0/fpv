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
    float armLength                 // center→motor distance in blocks (ground effect)
) {
    public FrameSpec {
        if (motorMounts.size() != motorCount) {
            throw new IllegalArgumentException("motorMounts size must equal motorCount");
        }
    }
}
