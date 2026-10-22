package org.restor.fpv.assembly;

/**
 * Single source of truth for how big an assembled drone is drawn. Both the preset
 * drones (DroneRenderer) and the bench-built ones (ModularDroneRenderer) resolve
 * their geometry here, keyed by frame, so a stock 5" and a 5" built at the station
 * come out identical.
 */
public record DroneVisual(float scale, float armSpread, float frameScale, float motorScale,
                          float propScale, float batteryScale, float cameraScale,
                          float electronicsScale, float moduleScale, float propLift) {

    public static final DroneVisual TINY_WHOOP =
        new DroneVisual(0.30f, 0.95f, 0.82f, 1.00f, 1.10f, 0.85f, 0.80f, 0.42f, 1.00f, 0.113f);
    public static final DroneVisual CINEWHOOP =
        new DroneVisual(0.50f, 1.05f, 1.02f, 0.76f, 1.42f, 1.14f, 0.75f, 0.48f, 1.00f, 0.091f);
    public static final DroneVisual FIVE_INCH =
        new DroneVisual(0.60f, 1.18f, 1.18f, 0.82f, 1.41f, 0.98f, 0.65f, 0.50f, 0.82f, 0.103f);
    public static final DroneVisual HEXA_6INCH =
        new DroneVisual(0.72f, 1.18f, 1.18f, 0.49f, 0.85f, 0.59f, 0.42f, 0.43f, 0.76f, 0.059f);
    public static final DroneVisual SEVEN_INCH =
        new DroneVisual(0.84f, 1.18f, 1.18f, 0.76f, 1.70f, 0.92f, 0.60f, 0.39f, 0.71f, 0.096f);
    public static final DroneVisual OCTO_8INCH =
        new DroneVisual(0.97f, 1.18f, 1.18f, 0.47f, 0.75f, 0.57f, 0.40f, 0.35f, 0.68f, 0.083f);

    // propLift is read along the tube axis here, not upward: the props sit behind the motors.
    public static final DroneVisual INTERCEPTOR =
        new DroneVisual(0.58f, 1.00f, 1.10f, 0.60f, 0.52f, 0.46f, 0.52f, 0.40f, 0.62f, 0.097f);
    // Fixed-wing parts sit on an airframe the renderer already scales up, so the part
    // scales here are relative to that model, not to a quad frame.
    public static final DroneVisual PLANE =
        new DroneVisual(1.00f, 1.00f, 1.00f, 0.55f, 0.62f, 0.36f, 0.42f, 0.45f, 0.80f, 0.0f);

    public static DroneVisual of(FrameSpec spec) {
        if (spec != null && spec.isFixedWing()) return PLANE;
        if (spec == FrameSpecs.FRAME_INTERCEPTOR) return INTERCEPTOR;
        if (spec == FrameSpecs.FRAME_TINY_WHOOP) return TINY_WHOOP;
        if (spec == FrameSpecs.FRAME_CINEWHOOP)  return CINEWHOOP;
        if (spec == FrameSpecs.FRAME_HEXA_6INCH) return HEXA_6INCH;
        if (spec == FrameSpecs.FRAME_7INCH)      return SEVEN_INCH;
        if (spec == FrameSpecs.FRAME_OCTO_8INCH) return OCTO_8INCH;
        return FIVE_INCH;
    }

    /** Hitbox the frame deserves, in blocks: width tracks the prop span, height the stack. */
    public float hitboxWidth()  { return scale * armSpread * 1.13f; }
    public float hitboxHeight() { return hitboxWidth() * 0.5f; }
}
