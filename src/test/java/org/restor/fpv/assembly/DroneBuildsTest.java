package org.restor.fpv.assembly;

import org.junit.jupiter.api.Test;
import org.restor.fpv.component.ComponentCatalog;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.data.DroneData;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.EXCESSIVE_HOVER_THROTTLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.FRAME_COMPONENT_MISMATCH;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.LOW_TWR;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MISSING_PARTS;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MOTOR_BATTERY_INCOMPATIBLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.MOTOR_PROP_INCOMPATIBLE;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_BATTERY;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_CAMERA;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_FC;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_FRAME;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_LIFT;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.NO_VTX;
import static org.restor.fpv.assembly.DroneAssemblyValidator.IssueCode.THRUST_IMBALANCE;

/**
 * Every build the mod hands out, weighed part by part: what it may not complain about,
 * what it must weigh and pull, and what changes when a part is swapped or worn out.
 */
class DroneBuildsTest {
    private static List<DronePresetSpecs.Build> everyBuild() {
        List<DronePresetSpecs.Build> builds = new ArrayList<>();
        for (DronePresetSpecs.Build build : DronePresetSpecs.ALL) {
            builds.add(build);
            for (DronePresetSpecs.Card card : build.bay()) builds.add(card.build());
        }
        return builds;
    }

    @Test
    void thePresetTableIsTheOneTheCreativeTabShows() {
        assertEquals(21, DronePresetSpecs.ALL.size());
        for (DronePresetSpecs.Build build : everyBuild()) {
            assertNotNull(build.spec(), build.id() + " has no frame layout");
            assertNotNull(build.frame(), build.id());
            assertNotNull(build.motor(), build.id());
            assertNotNull(build.prop(), build.id());
            assertNotNull(build.battery(), build.id());
            assertNotNull(build.camera(), build.id());
            assertEquals(build.spec().isFixedWing(), build.wing(), build.id());
            assertEquals(build.spec().motorCount(), build.motorCount(), build.id());
        }
    }

    @Test
    void everyPresetIsFlyableOutOfTheBox() {
        for (DronePresetSpecs.Build build : everyBuild()) {
            assertTrue(build.fitsFrame(), build.id() + " carries a part its frame rejects");
            var result = validate(build);
            assertTrue(result.errors().isEmpty(), build.id() + " errors: " + result.errors());
            assertTrue(result.warnings().isEmpty(), build.id() + " warnings: " + result.warnings()
                + " twr=" + result.metrics().twr() + " mass=" + result.metrics().massKg());
        }
    }

    @Test
    void everyPresetCarriesTheAvionicsItNeeds() {
        for (DronePresetSpecs.Build build : everyBuild()) {
            AssemblyParts parts = build.parts();
            assertNotNull(parts.fc(), build.id() + " has no flight controller");
            assertNotNull(parts.vtx(), build.id() + " has no video transmitter");
            assertTrue(parts.vtxRangeMultiplier() > 0.4f, build.id() + " flies on stock link range");
            assertTrue(parts.hasOsd(), build.id() + " flies without an OSD");
            float size = build.frame().frameSize();
            assertEquals(ComponentCatalog.stockVtx(size), parts.vtx(), build.id());
            assertEquals(ComponentCatalog.stockFc(size), parts.fc(), build.id());
            assertEquals(size >= 6f, parts.fc().fcHasBlackbox(), build.id() + " blackbox is the big-frame FC");
        }
    }

    @Test
    void multirotorsHoverWellBelowFullStickAndWingsPullTheirWeight() {
        for (DronePresetSpecs.Build build : everyBuild()) {
            var metrics = validate(build).metrics();
            if (build.wing()) {
                assertTrue(metrics.twr() >= 0.40f, build.id() + " twr " + metrics.twr());
                assertTrue(metrics.twr() <= 3.0f, build.id() + " twr " + metrics.twr());
            } else {
                assertTrue(metrics.twr() >= 1.4f, build.id() + " twr " + metrics.twr());
                assertTrue(metrics.twr() <= 12f, build.id() + " twr " + metrics.twr());
                assertTrue(metrics.hoverThrottle() <= 0.85f, build.id() + " hover " + metrics.hoverThrottle());
            }
            assertEquals(Math.sqrt(1f / metrics.twr()), metrics.hoverThrottle(), 1e-3,
                build.id() + " hover throttle is not the square root of 1/TWR");
        }
    }

    @Test
    void massGrowsWithTheAirframe() {
        float whoop = mass("whoop");
        float cine = mass("cine");
        float freestyle = mass("freestyle_5");
        float longRange = mass("longrange_7");
        float octo = mass("cargo_8");
        assertTrue(whoop < cine, whoop + " >= " + cine);
        assertTrue(cine < freestyle, cine + " >= " + freestyle);
        assertTrue(freestyle < longRange, freestyle + " >= " + longRange);
        assertTrue(longRange < octo, longRange + " >= " + octo);
        assertTrue(mass("plane_trainer") < mass("plane_cargo"));
        assertTrue(whoop > 0.02f);
    }

    @Test
    void aBalancedBuildFliesStraight() {
        for (DronePresetSpecs.Build build : everyBuild()) {
            float[] trim = build.parts().computeTrim(build.spec());
            for (float axis : trim) assertEquals(0f, axis, 1e-4f, build.id() + " trims on its own");
        }
    }

    @Test
    void aWornPropOnTheFrontRightCornerPullsTheKnownWay() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts worn = wear(build.parts(), 0, 0.5f);
        float[] trim = worn.computeTrim(build.spec());

        assertTrue(trim[0] > 1f, "a weak front-right corner banks right, got " + trim[0]);
        assertTrue(trim[1] > 1f, "a weak front corner drops the nose, got " + trim[1]);
        assertTrue(trim[2] < -0.5f, "a weak CCW corner yaws left, got " + trim[2]);

        var result = worn.validate(build.spec(), build.fitsFrame());
        assertTrue(result.warnings().contains(THRUST_IMBALANCE));
        assertTrue(result.isComplete());
    }

    @Test
    void evenWearCostsThrustButNotBalance() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts fresh = build.parts();
        AssemblyParts worn = fresh;
        for (int i = 0; i < build.motorCount(); i++) worn = wear(worn, i, 0.6f);

        assertTrue(worn.totalThrustN(4) < fresh.totalThrustN(4));
        assertEquals(fresh.totalMassKg(), worn.totalMassKg(), 1e-6f);
        for (float axis : worn.computeTrim(build.spec())) assertEquals(0f, axis, 1e-4f);
        assertFalse(worn.validate(build.spec(), true).warnings().contains(THRUST_IMBALANCE));
    }

    @Test
    void aMismatchedPropCostsThrustAndWearsTheBuildOut() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts stock = build.parts();
        AssemblyParts swapped = withProp(stock, 1, ComponentCatalog.PROP_3X2X3);

        assertEquals(1f, stock.slotEfficiency(1), 1e-6f);
        assertTrue(swapped.slotEfficiency(1) < 1f);
        assertTrue(swapped.motorThrustFactor(1) < stock.motorThrustFactor(1));
        assertEquals(1f, stock.stressFactor(4), 1e-6f);
        assertTrue(swapped.stressFactor(4) > 1f, "a mismatched corner must wear the build out");

        var result = swapped.validate(build.spec(), false);
        assertTrue(result.warnings().contains(MOTOR_PROP_INCOMPATIBLE));
        assertTrue(result.warnings().contains(FRAME_COMPONENT_MISMATCH));
        assertTrue(result.warnings().contains(THRUST_IMBALANCE));
        assertTrue(result.isComplete(), "a mismatch is never an error");
    }

    @Test
    void anEmptyMotorSlotIsOnlyAWarning() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts parts = withProp(withMotor(build.parts(), 3, null), 3, null);
        var result = parts.validate(build.spec(), build.fitsFrame());
        assertTrue(result.warnings().contains(MISSING_PARTS));
        assertTrue(result.isComplete());
        assertEquals(0f, parts.motorThrustFactor(3));
        assertEquals(0f, parts.slotEfficiency(3));
    }

    @Test
    void aBuildWithNothingLeftToFlyOnIsRejected() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts parts = build.parts();
        FrameSpec spec = build.spec();

        assertTrue(strip(parts, "frame").validate(spec, true).errors().contains(NO_FRAME));
        assertTrue(strip(parts, "battery").validate(spec, true).errors().contains(NO_BATTERY));
        assertTrue(strip(parts, "fc").validate(spec, true).errors().contains(NO_FC));
        assertTrue(strip(parts, "vtx").validate(spec, true).errors().contains(NO_VTX));
        assertTrue(strip(parts, "camera").validate(spec, true).warnings().contains(NO_CAMERA));

        AssemblyParts bare = parts;
        for (int i = 0; i < AssemblyParts.SLOTS; i++) bare = withProp(withMotor(bare, i, null), i, null);
        var result = bare.validate(spec, true);
        assertTrue(result.errors().contains(NO_LIFT));
        assertFalse(result.isComplete());
    }

    @Test
    void anUnderpoweredQuadIsRejectedAndAWingOfTheSamePullIsNot() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts heavy = build.parts();
        for (int i = 0; i < 4; i++) heavy = wear(heavy, i, 0.02f);
        var result = heavy.validate(build.spec(), true);
        assertTrue(result.metrics().twr() < 1f, "twr " + result.metrics().twr());
        assertTrue(result.errors().contains(NO_LIFT));
        assertFalse(result.warnings().contains(LOW_TWR), "a grounded build is not also nagged about TWR");
        assertFalse(result.warnings().contains(EXCESSIVE_HOVER_THROTTLE));

        DronePresetSpecs.Build wing = preset("plane_trainer");
        AssemblyParts wornWing = wing.parts();
        for (int i = 0; i < wing.motorCount(); i++) wornWing = wear(wornWing, i, 0.45f);
        var wingResult = wornWing.validate(wing.spec(), true);
        assertTrue(wingResult.isComplete(), "a wing glides on a fraction of the thrust");
    }

    @Test
    void overVoltingLiftsThrustAndTheHeavierPackCostsIt() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts stock = build.parts();
        AssemblyParts sixCell = withBattery(stock, ComponentCatalog.BATTERY_6S_1100);

        assertTrue(sixCell.totalThrustN(4) > stock.totalThrustN(4), "6S must spin the motors harder");
        assertTrue(sixCell.totalMassKg() > stock.totalMassKg(), "6S 1100 is the heavier pack");
        assertTrue(withBattery(stock, ComponentCatalog.BATTERY_1S_300).totalThrustN(4)
            < stock.totalThrustN(4), "under-volting must cost thrust");
        assertTrue(sixCell.validate(build.spec(), true).warnings().contains(MOTOR_BATTERY_INCOMPATIBLE),
            "2450KV on six cells is over-volted");
    }

    @Test
    void aWornBatteryHalvesItsShareOfThrustAndRange() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts fresh = build.parts();
        AssemblyParts flat = withBatteryCondition(fresh, 0.2f);
        assertTrue(flat.totalThrustN(4) < fresh.totalThrustN(4));
        assertTrue(flat.buildDroneData(build.spec()).batteryCapacity
            < fresh.buildDroneData(build.spec()).batteryCapacity);
    }

    @Test
    void flightDataFollowsTheParts() {
        for (DronePresetSpecs.Build build : everyBuild()) {
            FrameSpec spec = build.spec();
            AssemblyParts parts = build.parts();
            DroneData data = parts.buildDroneData(spec);

            assertEquals(parts.totalMassKg(), data.mass, 1e-6f, build.id());
            assertEquals(parts.totalThrustN(spec.motorCount()), data.motorPower, 1e-3f, build.id());
            assertEquals(spec.armLength(), data.armLength, 1e-6f, build.id());
            assertEquals(build.battery().batteryCapacity(), data.batteryCapacity, 1f, build.id());
            assertEquals(parts.propPitchSpeed(), data.propPitchSpeed, 1e-6f, build.id());
            assertTrue(data.propPitchSpeed >= 5f, build.id() + " pitch speed " + data.propPitchSpeed);
            assertTrue(data.maxRollRate > 0 && data.maxPitchRate > 0 && data.maxYawRate > 0, build.id());
            assertTrue(data.rotationResponsiveness > 0, build.id());
            assertTrue(data.dragCoefficient > 0, build.id());

            if (spec.isTailsitter()) {
                assertEquals(90f, data.cameraAngle, build.id() + " a tailsitter looks out of its nose");
            } else if (spec.isHeavyLift()) {
                assertEquals(-45f, data.cameraAngle, build.id() + " a heavy lift looks down");
            } else {
                assertEquals(build.camera().cameraTiltAngle(), data.cameraAngle, build.id());
            }
        }
    }

    @Test
    void heavyLiftFramesAreCalmerThanTheFiveInch() {
        DroneData five = preset("freestyle_5").parts().buildDroneData(FrameSpecs.FRAME_5INCH);
        DroneData octo = preset("cargo_8").parts().buildDroneData(FrameSpecs.FRAME_OCTO_8INCH);
        assertTrue(octo.maxRollRate < five.maxRollRate);
        assertTrue(octo.rotationResponsiveness < five.rotationResponsiveness);
    }

    @Test
    void propPitchSpeedRisesWithPitchAndVoltage() {
        DronePresetSpecs.Build build = preset("freestyle_5");
        AssemblyParts stock = build.parts();
        assertTrue(withBattery(stock, ComponentCatalog.BATTERY_6S_1100).propPitchSpeed()
            > stock.propPitchSpeed());
        assertTrue(stock.propPitchSpeed() > preset("whoop").parts().propPitchSpeed());
    }

    @Test
    void aBuildAlwaysHasOneSlotPerMotorMount() {
        assertThrows(IllegalArgumentException.class, () -> new AssemblyParts(
            ComponentCatalog.FRAME_5INCH, 1f, ComponentCatalog.BATTERY_4S_1300, 1f,
            ComponentCatalog.CAMERA_STANDARD, 1f, null, null,
            ComponentCatalog.VTX_200MW, ComponentCatalog.FC_F411,
            new ComponentStats[4], new float[4], new ComponentStats[4], new float[4], false));
    }

    private static DroneAssemblyValidator.ValidationResult validate(DronePresetSpecs.Build build) {
        return build.parts().validate(build.spec(), build.fitsFrame());
    }

    private static DronePresetSpecs.Build preset(String id) {
        return everyBuild().stream().filter(b -> b.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("no preset " + id));
    }

    private static float mass(String id) {
        return preset(id).parts().totalMassKg();
    }

    private static AssemblyParts wear(AssemblyParts parts, int slot, float condition) {
        float[] props = parts.propConditions().clone();
        props[slot] = condition;
        return copy(parts, parts.frame(), parts.battery(), parts.batteryCondition(), parts.camera(),
            parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), parts.props(), props);
    }

    private static AssemblyParts withProp(AssemblyParts parts, int slot, ComponentStats prop) {
        ComponentStats[] props = parts.props().clone();
        props[slot] = prop;
        return copy(parts, parts.frame(), parts.battery(), parts.batteryCondition(), parts.camera(),
            parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), props, parts.propConditions());
    }

    private static AssemblyParts withMotor(AssemblyParts parts, int slot, ComponentStats motor) {
        ComponentStats[] motors = parts.motors().clone();
        motors[slot] = motor;
        return copy(parts, parts.frame(), parts.battery(), parts.batteryCondition(), parts.camera(),
            parts.vtx(), parts.fc(), motors, parts.motorConditions(), parts.props(), parts.propConditions());
    }

    private static AssemblyParts withBattery(AssemblyParts parts, ComponentStats battery) {
        return copy(parts, parts.frame(), battery, parts.batteryCondition(), parts.camera(),
            parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), parts.props(),
            parts.propConditions());
    }

    private static AssemblyParts withBatteryCondition(AssemblyParts parts, float condition) {
        return copy(parts, parts.frame(), parts.battery(), condition, parts.camera(),
            parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), parts.props(),
            parts.propConditions());
    }

    private static AssemblyParts strip(AssemblyParts parts, String slot) {
        return switch (slot) {
            case "frame" -> copy(parts, null, parts.battery(), parts.batteryCondition(), parts.camera(),
                parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), parts.props(),
                parts.propConditions());
            case "battery" -> copy(parts, parts.frame(), null, parts.batteryCondition(), parts.camera(),
                parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), parts.props(),
                parts.propConditions());
            case "camera" -> copy(parts, parts.frame(), parts.battery(), parts.batteryCondition(), null,
                parts.vtx(), parts.fc(), parts.motors(), parts.motorConditions(), parts.props(),
                parts.propConditions());
            case "vtx" -> copy(parts, parts.frame(), parts.battery(), parts.batteryCondition(),
                parts.camera(), null, parts.fc(), parts.motors(), parts.motorConditions(), parts.props(),
                parts.propConditions());
            case "fc" -> copy(parts, parts.frame(), parts.battery(), parts.batteryCondition(),
                parts.camera(), parts.vtx(), null, parts.motors(), parts.motorConditions(), parts.props(),
                parts.propConditions());
            default -> throw new AssertionError(slot);
        };
    }

    private static AssemblyParts copy(AssemblyParts parts, ComponentStats frame, ComponentStats battery,
                                      float batteryCondition, ComponentStats camera, ComponentStats vtx,
                                      ComponentStats fc, ComponentStats[] motors, float[] motorConditions,
                                      ComponentStats[] props, float[] propConditions) {
        return new AssemblyParts(frame, parts.frameCondition(), battery, batteryCondition,
            camera, parts.cameraCondition(), parts.module(), parts.firmware(), vtx, fc,
            motors, motorConditions, props, propConditions, parts.wing());
    }
}
