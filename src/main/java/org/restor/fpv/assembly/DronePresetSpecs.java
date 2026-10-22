package org.restor.fpv.assembly;

import org.restor.fpv.component.ComponentCatalog;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.warhead.WarheadType;

import java.util.List;

import static org.restor.fpv.component.ComponentCatalog.*;

/**
 * The parts list of every ready-made build, as plain component stats. DronePresets turns each
 * entry into a creative-tab item; nothing else is needed to weigh a preset, so the same table
 * can be flown through the validator without a world.
 */
public final class DronePresetSpecs {
    public record Card(int count, Build build) {}

    public record Build(
        String id,
        ComponentStats frame,
        ComponentStats motor,
        ComponentStats prop,
        ComponentStats battery,
        ComponentStats camera,
        ComponentStats module,
        WarheadType warhead,
        List<Card> bay
    ) {
        public FrameSpec spec() {
            return FrameSpecs.byStats(frame);
        }

        public boolean wing() {
            FrameSpec spec = spec();
            return spec != null && spec.isFixedWing();
        }

        public int motorCount() {
            FrameSpec spec = spec();
            return spec != null ? spec.motorCount() : 4;
        }

        /** Whether every part of this build is one the frame accepts. */
        public boolean fitsFrame() {
            if (spec() == null) return false;
            float size = frame.frameSize();
            boolean wing = wing();
            boolean fits = ComponentFit.battery(size, wing, battery)
                && ComponentFit.camera(size, camera)
                && ComponentFit.motor(size, wing, motor)
                && ComponentFit.propeller(size, wing, prop);
            if (module != null) {
                fits &= ComponentFit.module(size, wing, ComponentCatalog.MODULE_TYPES.get(module));
            }
            return fits;
        }

        /** The build as the assembly station would hand it over: stock avionics, everything new. */
        public AssemblyParts parts() {
            int slots = AssemblyParts.SLOTS;
            ComponentStats[] motors = new ComponentStats[slots];
            ComponentStats[] props = new ComponentStats[slots];
            float[] motorConditions = new float[slots];
            float[] propConditions = new float[slots];
            int count = motorCount();
            for (int i = 0; i < slots; i++) {
                if (i < count) {
                    motors[i] = motor;
                    props[i] = prop;
                }
                motorConditions[i] = 1f;
                propConditions[i] = 1f;
            }
            float size = frame != null ? frame.frameSize() : 5f;
            return new AssemblyParts(
                frame, 1f, battery, 1f, camera, 1f,
                module, null,
                ComponentCatalog.stockVtx(size), ComponentCatalog.stockFc(size),
                motors, motorConditions, props, propConditions,
                wing());
        }
    }

    public static final List<Build> ALL = List.of(
        rotor("freestyle_5", FRAME_5INCH, MOTOR_2207_2450, PROP_5X4X3, BATTERY_4S_1300, CAMERA_STANDARD),
        rotor("whoop", FRAME_TINY_WHOOP, MOTOR_1103_8000, PROP_65MM, BATTERY_1S_300, CAMERA_STANDARD),
        rotor("cine", FRAME_CINEWHOOP, MOTOR_1404_4500, PROP_3X2X3, BATTERY_4S_650, CAMERA_WIDE),
        rotor("longrange_7", FRAME_7INCH, MOTOR_2806_1300, PROP_5X4X2, BATTERY_6S_1100, CAMERA_STANDARD),
        build("bomber_5", FRAME_5INCH, MOTOR_2207_2450, PROP_5X4X3, BATTERY_4S_1300, CAMERA_STANDARD,
            MODULE_PAYLOAD_BAY, null),
        build("kamikaze_5", FRAME_5INCH, MOTOR_2207_2450, PROP_5X4X3, BATTERY_4S_1300, CAMERA_STANDARD,
            MODULE_WARHEAD, WarheadType.HE),
        build("interceptor", FRAME_INTERCEPTOR, MOTOR_2207_2450, PROP_5X4X3, BATTERY_4S_1300, CAMERA_STANDARD,
            MODULE_WARHEAD, WarheadType.PROXIMITY),
        build("strike_6", FRAME_HEXA_6INCH, MOTOR_2306_1700, PROP_5X4X3, BATTERY_6S_1100, CAMERA_STANDARD,
            MODULE_WARHEAD, WarheadType.FRAG),
        build("cargo_8", FRAME_OCTO_8INCH, MOTOR_3115_900, PROP_5X4X2, BATTERY_6S_1100, CAMERA_WIDE,
            MODULE_STORAGE, null),
        build("agro_6", FRAME_HEXA_6INCH, MOTOR_2306_1700, PROP_5X4X3, BATTERY_6S_1100, CAMERA_WIDE,
            MODULE_PAYLOAD_BAY, null),
        build("agro_8", FRAME_OCTO_8INCH, MOTOR_3115_900, PROP_5X4X2, BATTERY_6S_5000, CAMERA_WIDE,
            MODULE_PAYLOAD_BAY, null),

        rotor("plane_trainer", PLANE_FRAME_TRAINER, MOTOR_2814_1100, PROP_8X6, BATTERY_6S_1100, CAMERA_STANDARD),
        rotor("plane_recon", PLANE_FRAME_GLIDER, MOTOR_2814_1100, PROP_8X6, BATTERY_6S_5000, CAMERA_WIDE),
        rotor("plane_racer", PLANE_FRAME_WING, MOTOR_3548_900, PROP_10X5, BATTERY_6S_1100, CAMERA_STANDARD),
        build("plane_delta_strike", PLANE_FRAME_DELTA, MOTOR_3548_900, PROP_10X5, BATTERY_6S_1100,
            CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.HE),
        build("plane_bomber", PLANE_FRAME_STRIKE, MOTOR_3548_900, PROP_10X5, BATTERY_6S_5000,
            CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.HEAVY),
        build("plane_cargo", PLANE_FRAME_CARGO, MOTOR_4250_650, PROP_12X6, BATTERY_6S_5000,
            CAMERA_WIDE, MODULE_STORAGE, null),

        withBay(build("plane_spotter", PLANE_FRAME_SCOUT, MOTOR_2814_1100, PROP_8X6, BATTERY_6S_5000,
                CAMERA_WIDE, MODULE_TARGETING, null),
            new Card(4, build("spotter_card_quad", FRAME_5INCH, MOTOR_2207_2450, PROP_5X4X3,
                BATTERY_4S_1300, CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.HE)),
            new Card(2, build("spotter_card_wing", PLANE_FRAME_KAMIKAZE, MOTOR_3548_900, PROP_10X5,
                BATTERY_6S_1100, CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.HEAVY))),
        build("plane_kamikaze", PLANE_FRAME_KAMIKAZE, MOTOR_3548_900, PROP_10X5, BATTERY_6S_1100,
            CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.HE),
        withBay(rotor("plane_carrier", PLANE_FRAME_CARRIER, MOTOR_4250_650, PROP_12X6, BATTERY_6S_5000,
                CAMERA_WIDE),
            new Card(4, build("carrier_card_quad", FRAME_5INCH, MOTOR_2207_2450, PROP_5X4X3,
                BATTERY_4S_1300, CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.FRAG)),
            new Card(2, build("carrier_card_7", FRAME_7INCH, MOTOR_2806_1300, PROP_5X4X2,
                BATTERY_6S_1100, CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.SHAPED))),
        build("plane_loiter", PLANE_FRAME_LOITER, MOTOR_4250_650, PROP_12X6, BATTERY_6S_5000,
            CAMERA_STANDARD, MODULE_WARHEAD, WarheadType.HEAVY));

    private static Build rotor(String id, ComponentStats frame, ComponentStats motor, ComponentStats prop,
                               ComponentStats battery, ComponentStats camera) {
        return build(id, frame, motor, prop, battery, camera, null, null);
    }

    private static Build build(String id, ComponentStats frame, ComponentStats motor, ComponentStats prop,
                               ComponentStats battery, ComponentStats camera, ComponentStats module,
                               WarheadType warhead) {
        return new Build(id, frame, motor, prop, battery, camera, module, warhead, List.of());
    }

    private static Build withBay(Build build, Card... cards) {
        return new Build(build.id(), build.frame(), build.motor(), build.prop(), build.battery(),
            build.camera(), build.module(), build.warhead(), List.of(cards));
    }

    private DronePresetSpecs() {}
}
