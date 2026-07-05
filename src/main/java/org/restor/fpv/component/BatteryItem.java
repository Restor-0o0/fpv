package org.restor.fpv.component;

// Battery component: capacity drives flight time, weight drives hover throttle.
public class BatteryItem extends ComponentItem {
    public BatteryItem(ComponentStats stats) {
        super(stats);
    }

    public int getCells() {
        return getStats().batteryCells();
    }

    public int getCapacity() {
        return getStats().batteryCapacity();
    }
}
