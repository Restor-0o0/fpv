package org.restor.fpv.component;

import net.minecraft.world.item.Item;
// Motor component: motorPower × the installed prop's factor gives the slot's
// thrust — mixed motors on one frame unbalance the drone exactly like mixed props
// (see DroneAssembly.motorThrustFactor / computeTrim).
public class MotorItem extends ComponentItem {
    public MotorItem(Item.Properties props, ComponentStats stats) {
        super(props, stats);
    }

    public float getPower() {
        return getStats().motorPower();
    }

    public float getKv() {
        return getStats().motorKv();
    }
}
