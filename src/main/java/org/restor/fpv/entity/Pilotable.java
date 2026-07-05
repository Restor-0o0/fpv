package org.restor.fpv.entity;

// Implemented by any vehicle entity that can host a pilot (riding or, for drones, remote) —
// lets OperatorDummyEntity clean itself up without being tied to DroneEntity specifically.
public interface Pilotable {
    boolean hasAnyPilot();
}
