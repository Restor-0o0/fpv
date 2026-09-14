package org.restor.fpv.client.render;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.restor.fpv.client.PilotView;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

public final class FpvCamera {
    private FpvCamera() {}

    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!PilotView.isFpvActive()) return;
        float pt = (float) event.getPartialTick();

        DroneEntity drone = PilotView.currentDrone();
        if (drone != null) {
            // Heavy rigs carry the camera on a stabilised belly gimbal: it follows the
            // airframe's heading only, so the horizon stays level.
            if (drone.isHeavyLift()) {
                float heading = Mth.rotLerp(pt, drone.yRotO, drone.getYRot());
                event.setYaw(heading + drone.getGimbalYaw());
                event.setPitch(-drone.getGimbalPitch());
                event.setRoll(0f);
                return;
            }
            // Slerp the whole orientation: per-axis Euler lerps jerk near pitch ±90
            // where the decomposition flips yaw/roll by 180°.
            Quaternionf q = new Quaternionf(
                    DroneEntity.attitudeQuat(drone.yRotO, drone.xRotO, drone.getPrevRoll()))
                .slerp(DroneEntity.attitudeQuat(drone.getYRot(), drone.getXRot(), drone.getRoll()), pt);
            q.rotateX((float) Math.toRadians(-drone.getCameraAngle()));
            apply(event, DroneEntity.decomposeAttitude(q));
            return;
        }

        PlaneEntity plane = PilotView.currentPlane();
        if (plane == null) return;

        Quaternionf q = new Quaternionf(
                DroneEntity.attitudeQuat(plane.yRotO, plane.xRotO, plane.getPrevRoll()))
            .slerp(DroneEntity.attitudeQuat(plane.getYRot(), plane.getXRot(), plane.getRoll()), pt)
            .rotateY((float) Math.toRadians(-plane.getGimbalYaw()))
            .rotateX((float) Math.toRadians(-plane.getGimbalPitch()));
        if (plane.isStabilizedGimbalView()) {
            Vector3f fwd = q.transform(new Vector3f(0, 0, 1));
            event.setYaw((float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z)));
            event.setPitch((float) -Math.toDegrees(Math.asin(Mth.clamp(fwd.y, -1f, 1f))));
            event.setRoll(0f);
        } else {
            apply(event, DroneEntity.decomposeAttitude(q));
        }
    }

    private static void apply(ViewportEvent.ComputeCameraAngles event, float[] a) {
        event.setYaw(a[0]);
        event.setPitch(a[1]);
        event.setRoll(a[2]);
    }
}
