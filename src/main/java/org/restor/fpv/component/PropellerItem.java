package org.restor.fpv.component;

// Propeller component: diameter × pitch × efficiency scale the motor's thrust in
// its slot (DroneAssembly.motorThrustFactor).
public class PropellerItem extends ComponentItem {
    public PropellerItem(ComponentStats stats) {
        super(stats);
    }

    /** Thrust factor normalized to a 5x4 prop at efficiency 1.0. */
    public float getThrustFactor() {
        var s = getStats();
        return s.propEfficiency() * (s.propDiameter() * s.propPitch()) / 20f;
    }
}
