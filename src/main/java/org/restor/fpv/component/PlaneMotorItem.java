package org.restor.fpv.component;

import net.minecraft.world.item.Item;
// Outrunner sized for a wing: too heavy and too slow-turning for a multirotor arm.
public class PlaneMotorItem extends MotorItem {
    public PlaneMotorItem(Item.Properties props, ComponentStats stats) {
        super(props, stats);
    }
}
