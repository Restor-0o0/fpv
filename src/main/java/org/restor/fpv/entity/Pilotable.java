package org.restor.fpv.entity;

// Implemented by any vehicle entity that can host a pilot (riding or, for drones, remote) —
// lets OperatorDummyEntity clean itself up without being tied to DroneEntity specifically.
public interface Pilotable {
    boolean hasAnyPilot();

    /** True only for the stand-in this vehicle currently owns; strays clean themselves up. */
    boolean isActiveDummy(java.util.UUID dummy);

    /** An unfinished build sits where it is put: it takes no pilot and binds to no radio. */
    default boolean isFlightReady() {
        return true;
    }
}
