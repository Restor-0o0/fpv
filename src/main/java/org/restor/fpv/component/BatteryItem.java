package org.restor.fpv.component;

import net.minecraft.world.item.Item;
// Battery component: capacity drives flight time, weight drives hover throttle.
public class BatteryItem extends ComponentItem {
    public BatteryItem(Item.Properties props, ComponentStats stats) {
        super(props, stats);
    }

    public int getCells() {
        return getStats().batteryCells();
    }

    public int getCapacity() {
        return getStats().batteryCapacity();
    }
}
