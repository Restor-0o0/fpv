package org.restor.fpv.assembly;

import org.junit.jupiter.api.Test;
import org.restor.fpv.component.ComponentCatalog;
import org.restor.fpv.component.ComponentStats;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSpecsTest {
    private static final Map<String, FrameSpec> SPECS = declaredSpecs();

    @Test
    void everyShippedFrameHasALayout() {
        for (ComponentStats frame : ComponentCatalog.FRAMES) {
            FrameSpec spec = FrameSpecs.byStats(frame);
            assertNotNull(spec, frame.name() + " has no FrameSpec");
            boolean plane = frame.name().toLowerCase().contains("airframe")
                || frame.name().toLowerCase().contains("wing");
            assertEquals(plane, spec.isFixedWing(), frame.name() + " frame kind");
        }
        assertEquals(ComponentCatalog.FRAMES.size(), SPECS.size(),
            "every FrameSpec constant must belong to a shipped frame");
    }

    @Test
    void layoutIsKeyedByIdentityOfTheCatalogEntry() {
        assertSame(FrameSpecs.FRAME_5INCH, FrameSpecs.byStats(ComponentCatalog.FRAME_5INCH));
        assertSame(FrameSpecs.PLANE_SPOTTER, FrameSpecs.byStats(ComponentCatalog.PLANE_FRAME_SCOUT));
        assertNull(FrameSpecs.byStats(ComponentCatalog.MOTOR_2207_2450));
        assertNull(FrameSpecs.byStats(null));
    }

    @Test
    void mountsStayInsideTheBlockAndCountMatchesMotors() {
        SPECS.forEach((name, spec) -> {
            assertEquals(spec.motorCount(), spec.motorMounts().size(), name + " mount count");
            assertTrue(spec.armLength() > 0, name + " arm length");
            List<MountPoint> mounts = new ArrayList<>(spec.motorMounts());
            mounts.add(spec.batteryMount());
            mounts.add(spec.cameraMount());
            mounts.add(spec.moduleMount());
            for (MountPoint m : mounts) {
                assertTrue(inBlock(m.x()) && inBlock(m.y()) && inBlock(m.z()),
                    name + " mount outside the block: " + m);
            }
        });
    }

    @Test
    void motorsAreSymmetricAboutTheRollAxis() {
        SPECS.forEach((name, spec) -> {
            float x = 0, y = 0;
            for (MountPoint m : spec.motorMounts()) {
                x += m.x();
                y += m.y();
            }
            assertEquals(0f, x, 1e-4f, name + " motors are not left/right symmetric");
            assertEquals(0f, y / spec.motorCount(), 0.5f, name + " motors sit outside the frame");
        });
    }

    @Test
    void cameraLooksForwardAndModuleHangsUnderTheBattery() {
        SPECS.forEach((name, spec) -> {
            assertTrue(spec.cameraMount().z() < 0, name + " camera is not at the nose");
            assertTrue(spec.moduleMount().y() < spec.batteryMount().y(),
                name + " module does not sit under the battery");
            assertEquals(0f, spec.cameraMount().x(), 1e-4f, name + " camera is off-centre");
            assertEquals(0f, spec.batteryMount().x(), 1e-4f, name + " battery is off-centre");
        });
    }

    @Test
    void multirotorsCarryMotorPairsAndWingsCarryOne() {
        SPECS.forEach((name, spec) -> {
            if (spec.isFixedWing()) {
                assertEquals(1, spec.motorCount(), name + " a wing runs a single motor");
            } else {
                assertTrue(spec.motorCount() >= 4 && spec.motorCount() % 2 == 0,
                    name + " needs an even number of at least four motors");
            }
        });
    }

    @Test
    void heavyLiftAndTailsitterFollowTheFrameLayout() {
        assertTrue(FrameSpecs.FRAME_HEXA_6INCH.isHeavyLift());
        assertTrue(FrameSpecs.FRAME_OCTO_8INCH.isHeavyLift());
        assertFalse(FrameSpecs.FRAME_5INCH.isHeavyLift());
        assertFalse(FrameSpecs.PLANE_CARGO.isHeavyLift());

        assertTrue(FrameSpecs.FRAME_INTERCEPTOR.isTailsitter());
        assertEquals(FrameRole.INTERCEPTOR, FrameSpecs.FRAME_INTERCEPTOR.role());
        SPECS.forEach((name, spec) ->
            assertEquals(spec.role() == FrameRole.INTERCEPTOR, spec.isTailsitter(), name + " tailsitter"));
    }

    @Test
    void interceptorRotorsRingTheTubeAndCancelOut() {
        FrameSpec spec = FrameSpecs.FRAME_INTERCEPTOR;
        float z = spec.motorMounts().get(0).z();
        float x = 0, y = 0;
        for (MountPoint m : spec.motorMounts()) {
            assertEquals(z, m.z(), 1e-4f, "interceptor rotors share one plane");
            x += m.x();
            y += m.y();
        }
        assertEquals(0f, x, 1e-4f);
        assertEquals(0f, y, 1e-4f);
        assertTrue(z > 0, "interceptor rotors sit behind the centre of mass");
    }

    @Test
    void wingTuningIsOnlySetOnWings() {
        SPECS.forEach((name, spec) -> {
            FrameTuning t = spec.tuning();
            assertTrue(t.agility() > 0 && t.responsiveness() > 0 && t.dragFactor() > 0
                && t.toughness() > 0 && t.liftCapacityKg() > 0, name + " tuning must be positive");
            if (spec.isFixedWing()) {
                assertTrue(t.wingArea() > 0 && t.liftCoeff() > 0, name + " wing has no wing");
                assertTrue(t.stallSpeed() > 0 && t.maxSpeed() > t.stallSpeed(), name + " wing speeds");
            } else {
                assertEquals(0f, t.wingArea(), name + " a multirotor has no wing area");
                assertEquals(0f, t.liftCoeff());
                assertEquals(0f, t.stallSpeed());
                assertEquals(0f, t.maxSpeed());
            }
        });
    }

    @Test
    void biggerFramesHaveLongerArms() {
        assertTrue(FrameSpecs.FRAME_TINY_WHOOP.armLength() < FrameSpecs.FRAME_5INCH.armLength());
        assertTrue(FrameSpecs.FRAME_5INCH.armLength() < FrameSpecs.FRAME_7INCH.armLength());
        assertTrue(FrameSpecs.FRAME_7INCH.armLength() < FrameSpecs.FRAME_OCTO_8INCH.armLength());
        assertTrue(FrameSpecs.PLANE_TRAINER.armLength() < FrameSpecs.PLANE_CARRIER.armLength());
    }

    @Test
    void mountCountMustMatchMotorCount() {
        assertThrows(IllegalArgumentException.class, () -> new FrameSpec(4,
            List.of(MountPoint.fromBlockbench(8, 8, 8)),
            MountPoint.fromBlockbench(8, 8, 8), MountPoint.fromBlockbench(8, 8, 4),
            MountPoint.fromBlockbench(8, 7, 8), 0.5f, FrameKind.MULTIROTOR, FrameTuning.DEFAULT));
    }

    @Test
    void blockbenchCentreIsTheFrameCentre() {
        MountPoint centre = MountPoint.fromBlockbench(8, 8, 8);
        assertEquals(0f, centre.x());
        assertEquals(0f, centre.y());
        assertEquals(0f, centre.z());
        assertEquals(0.5f, MountPoint.fromBlockbench(16, 8, 8).x(), 1e-6f);
        assertEquals(-0.5f, MountPoint.fromBlockbench(8, 8, 0).z(), 1e-6f);
    }

    private static boolean inBlock(float v) {
        return v >= -0.5f && v <= 0.5f;
    }

    private static Map<String, FrameSpec> declaredSpecs() {
        Map<String, FrameSpec> specs = new LinkedHashMap<>();
        for (Field field : FrameSpecs.class.getDeclaredFields()) {
            if (field.getType() != FrameSpec.class || !Modifier.isPublic(field.getModifiers())) continue;
            try {
                specs.put(field.getName(), (FrameSpec) field.get(null));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        return specs;
    }
}
