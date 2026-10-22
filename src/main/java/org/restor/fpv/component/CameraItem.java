package org.restor.fpv.component;

import net.minecraft.world.item.Item;
// Camera component: tilt angle becomes the drone's camera uptilt, FOV reserved for
// a future per-camera FOV override.
public class CameraItem extends ComponentItem {
    public CameraItem(Item.Properties props, ComponentStats stats) {
        super(props, stats);
    }

    public float getTilt() {
        return getStats().cameraTiltAngle();
    }

    public float getFov() {
        return getStats().cameraFov();
    }
}
