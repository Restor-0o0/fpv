package org.restor.fpv.assembly;

/**
 * What makes one frame fly differently from another of the same size: how fast it rotates,
 * how sharp it feels, how much air it drags, how well it survives a hit and how much dead
 * weight it will haul. The wing fields are only meaningful on FIXED_WING frames.
 */
public record FrameTuning(
    float agility,          // × rotation rates
    float responsiveness,   // × control responsiveness
    float dragFactor,       // × drag area
    float toughness,        // × resistance to impact wear
    float liftCapacityKg,   // heaviest warhead / payload this airframe carries
    float wingArea,         // m², fixed-wing
    float liftCoeff,        // fixed-wing
    float stallSpeed,       // m/s, fixed-wing
    float maxSpeed          // m/s cap, fixed-wing
) {
    public static final FrameTuning DEFAULT = rotor(1f, 1f, 1f, 1f, 2.5f);

    public static FrameTuning rotor(float agility, float responsiveness, float dragFactor,
                                    float toughness, float liftCapacityKg) {
        return new FrameTuning(agility, responsiveness, dragFactor, toughness, liftCapacityKg,
            0f, 0f, 0f, 0f);
    }

    public static FrameTuning wing(float agility, float responsiveness, float dragFactor,
                                   float toughness, float liftCapacityKg,
                                   float wingArea, float liftCoeff, float stallSpeed, float maxSpeed) {
        return new FrameTuning(agility, responsiveness, dragFactor, toughness, liftCapacityKg,
            wingArea, liftCoeff, stallSpeed, maxSpeed);
    }
}
