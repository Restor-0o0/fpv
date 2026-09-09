package org.restor.fpv.component;

public enum ComponentType {
    FRAME,
    MOTOR,
    BATTERY,
    PROPELLER,
    VTX,
    CAMERA,
    FLIGHT_CONTROLLER,
    // Payload / utility module that mounts in the frame's module slot (FPV_IDEAS §4-5).
    MODULE,
    // Firmware chip in the FC's own slot: gives the drone autonomous modes.
    FIRMWARE
}
