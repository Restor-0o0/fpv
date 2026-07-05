package org.restor.fpv.component;

public record ComponentStats(
    ComponentType type,
    String name,
    float weight,
    // Frame
    float frameSize,
    float frameDurability,
    // Motor
    float motorKv,
    float motorPower,
    float motorDiameter,
    // Battery
    int batteryCells,
    int batteryCapacity,
    float batteryMaxDischarge,
    // Propeller
    float propDiameter,
    float propPitch,
    int propBlades,
    float propEfficiency,
    // VTX
    int vtxPowerMw,
    float vtxRangeMultiplier,
    // Camera
    float cameraFov,
    float cameraTiltAngle,
    // Flight Controller
    boolean fcHasOsd,
    boolean fcHasBlackbox
) {
    public static ComponentStats frame(String name, float size, float durability, float weight) {
        return new ComponentStats(ComponentType.FRAME, name, weight, size, durability,
            0,0,0, 0,0,0, 0,0,0,0, 0,0, 0,0, false,false);
    }

    public static ComponentStats motor(String name, float kv, float power, float diameter, float weight) {
        return new ComponentStats(ComponentType.MOTOR, name, weight, 0,0,
            kv,power,diameter, 0,0,0, 0,0,0,0, 0,0, 0,0, false,false);
    }

    public static ComponentStats battery(String name, int cells, int capacity, float maxDischarge, float weight) {
        return new ComponentStats(ComponentType.BATTERY, name, weight, 0,0,
            0,0,0, cells,capacity,maxDischarge, 0,0,0,0, 0,0, 0,0, false,false);
    }

    public static ComponentStats propeller(String name, float diameter, float pitch, int blades, float efficiency, float weight) {
        return new ComponentStats(ComponentType.PROPELLER, name, weight, 0,0,
            0,0,0, 0,0,0, diameter,pitch,blades,efficiency, 0,0, 0,0, false,false);
    }

    public static ComponentStats vtx(String name, int powerMw, float rangeMultiplier, float weight) {
        return new ComponentStats(ComponentType.VTX, name, weight, 0,0,
            0,0,0, 0,0,0, 0,0,0,0, powerMw,rangeMultiplier, 0,0, false,false);
    }

    public static ComponentStats camera(String name, float fov, float tiltAngle, float weight) {
        return new ComponentStats(ComponentType.CAMERA, name, weight, 0,0,
            0,0,0, 0,0,0, 0,0,0,0, 0,0, fov,tiltAngle, false,false);
    }

    public static ComponentStats fc(String name, boolean hasOsd, boolean hasBlackbox, float weight) {
        return new ComponentStats(ComponentType.FLIGHT_CONTROLLER, name, weight, 0,0,
            0,0,0, 0,0,0, 0,0,0,0, 0,0, 0,0, hasOsd,hasBlackbox);
    }
}
