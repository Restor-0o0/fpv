package org.restor.fpv.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

public final class PilotView {
    private static Minecraft mc() { return Minecraft.getInstance(); }

    private PilotView() {}

    // The drone this client is flying: as a passenger, or remotely via a control
    // station (server put our camera on the drone).
    public static DroneEntity currentDrone() {
        if (mc().player == null) return null;
        if (mc().player.getVehicle() instanceof DroneEntity d) return d;
        if (mc().getCameraEntity() instanceof DroneEntity d) return d;
        return null;
    }

    public static PlaneEntity currentPlane() {
        if (mc().player == null) return null;
        if (mc().player.getVehicle() instanceof PlaneEntity p) return p;
        return null;
    }

    public static boolean isFpvActive() {
        return (currentDrone() != null || currentPlane() != null)
            && mc().options.getCameraType() == CameraType.FIRST_PERSON;
    }
}
