package org.restor.fpv.component;

// What a payload/utility module DOES when installed in the frame's module slot
// (FPV_IDEAS §4). Compatibility with frames is decided in DroneAssembly.isCompatible.
public enum ModuleType {
    // Bomb-bay rack: lets the drone carry and drop charges (FIRE_PAYLOAD).
    PAYLOAD_BAY,
    // Fixed warhead: the drone detonates on impact once airborne (kamikaze behaviour).
    WARHEAD,
    // Light dart gun: in companion mode the drone shoots darts at hostile mobs.
    DART_GUN,
    // Cargo box: 9-slot storage, opened by right-clicking the drone in companion mode.
    STORAGE,
    // Targeting pod: opens the strike bay on a spotter airframe, letting it call in
    // the drone cards stowed there.
    TARGETING,
    // Mini-rocket pod: loaded with mini rockets, fired by the pilot with the weapon key.
    ROCKET_POD
}
