package org.restor.fpv.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

public final class PilotView {
    private static final Minecraft MC = Minecraft.getInstance();

    private PilotView() {}

    // The drone this client is flying: as a passenger, or remotely via a control
    // station (server put our camera on the drone).
    public static DroneEntity currentDrone() {
        if (MC.player == null) return null;
        if (MC.player.getVehicle() instanceof DroneEntity d) return d;
        if (MC.getCameraEntity() instanceof DroneEntity d) return d;
        return null;
    }

    public static PlaneEntity currentPlane() {
        if (MC.player == null) return null;
        if (MC.player.getVehicle() instanceof PlaneEntity p) return p;
        return null;
    }

    public static boolean isFpvActive() {
        return (currentDrone() != null || currentPlane() != null)
            && MC.options.getCameraType() == CameraType.FIRST_PERSON;
    }
}
