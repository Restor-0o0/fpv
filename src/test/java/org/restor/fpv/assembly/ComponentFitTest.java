package org.restor.fpv.assembly;

import org.junit.jupiter.api.Test;
import org.restor.fpv.component.ComponentStats;
import org.restor.fpv.component.FirmwareType;
import org.restor.fpv.component.ModuleType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.restor.fpv.component.ComponentCatalog.*;

class ComponentFitTest {
    private static final boolean ROTOR = false;
    private static final boolean WING = true;

    @Test
    void whoopTakesOnlyWhoopParts() {
        float size = FRAME_TINY_WHOOP.frameSize();
        assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_1103_8000));
        assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_1404_4500));
        assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_2207_2450));
        assertTrue(ComponentFit.propeller(size, ROTOR, PROP_65MM));
        assertFalse(ComponentFit.propeller(size, ROTOR, PROP_3X2X3));
        assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_1S_300));
        assertFalse(ComponentFit.battery(size, ROTOR, BATTERY_4S_650));
    }

    @Test
    void cinewhoopTakesTheThreeInchClass() {
        float size = FRAME_CINEWHOOP.frameSize();
        assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_1404_4500));
        assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_1103_8000));
        assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_2207_2450));
        assertTrue(ComponentFit.propeller(size, ROTOR, PROP_3X2X3));
        assertFalse(ComponentFit.propeller(size, ROTOR, PROP_5X4X3));
        assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_4S_650));
        assertFalse(ComponentFit.battery(size, ROTOR, BATTERY_4S_1300));
        assertFalse(ComponentFit.battery(size, ROTOR, BATTERY_1S_300));
    }

    @Test
    void fiveInchTakesTheFreestyleClass() {
        float size = FRAME_5INCH.frameSize();
        assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_2207_2450));
        assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_2306_1700));
        assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_1404_4500));
        assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_3115_900));
        assertTrue(ComponentFit.propeller(size, ROTOR, PROP_5X4X3));
        assertTrue(ComponentFit.propeller(size, ROTOR, PROP_5X4X2));
        assertFalse(ComponentFit.propeller(size, ROTOR, PROP_3X2X3));
        assertFalse(ComponentFit.propeller(size, ROTOR, PROP_8X6));
        assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_4S_1300));
        assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_6S_1100));
        assertFalse(ComponentFit.battery(size, ROTOR, BATTERY_6S_5000));
    }

    @Test
    void hexaSixInchWantsLowerKvThanAFiveInch() {
        float size = FRAME_HEXA_6INCH.frameSize();
        assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_2306_1700));
        assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_2207_2450));
        assertTrue(ComponentFit.propeller(size, ROTOR, PROP_5X4X3));
        assertFalse(ComponentFit.propeller(size, ROTOR, PROP_3X2X3));
        assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_6S_1100));
        assertFalse(ComponentFit.battery(size, ROTOR, BATTERY_4S_650));
    }

    @Test
    void sevenInchAndOctoWantBigLowKvMotorsOnSixCells() {
        for (ComponentStats frame : new ComponentStats[]{FRAME_7INCH, FRAME_OCTO_8INCH}) {
            float size = frame.frameSize();
            assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_2806_1300), frame.name());
            assertTrue(ComponentFit.motor(size, ROTOR, MOTOR_3115_900), frame.name());
            assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_2207_2450), frame.name());
            assertFalse(ComponentFit.motor(size, ROTOR, MOTOR_1404_4500), frame.name());
            assertTrue(ComponentFit.propeller(size, ROTOR, PROP_5X4X2), frame.name());
            assertFalse(ComponentFit.propeller(size, ROTOR, PROP_3X2X3), frame.name());
            assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_6S_1100), frame.name());
            assertTrue(ComponentFit.battery(size, ROTOR, BATTERY_6S_5000), frame.name());
            assertFalse(ComponentFit.battery(size, ROTOR, BATTERY_4S_1300), frame.name());
        }
    }

    @Test
    void planeMotorsAndPropsScaleWithTheAirframe() {
        float trainer = PLANE_FRAME_TRAINER.frameSize();
        assertTrue(ComponentFit.motor(trainer, WING, MOTOR_2814_1100));
        assertTrue(ComponentFit.motor(trainer, WING, MOTOR_3548_900));
        assertFalse(ComponentFit.motor(trainer, WING, MOTOR_4250_650));
        assertTrue(ComponentFit.propeller(trainer, WING, PROP_8X6));
        assertFalse(ComponentFit.propeller(trainer, WING, PROP_12X6));

        float strike = PLANE_FRAME_STRIKE.frameSize();
        assertTrue(ComponentFit.motor(strike, WING, MOTOR_3548_900));
        assertTrue(ComponentFit.propeller(strike, WING, PROP_10X5));
        assertFalse(ComponentFit.propeller(strike, WING, PROP_5X4X3));

        float cargo = PLANE_FRAME_CARGO.frameSize();
        assertTrue(ComponentFit.motor(cargo, WING, MOTOR_4250_650));
        assertFalse(ComponentFit.motor(cargo, WING, MOTOR_2814_1100));
        assertTrue(ComponentFit.propeller(cargo, WING, PROP_12X6));
        assertFalse(ComponentFit.propeller(cargo, WING, PROP_8X6));
    }

    @Test
    void planesRunFourCellsAndUpAndBigOnesNeedACapacityPack() {
        float trainer = PLANE_FRAME_TRAINER.frameSize();
        assertTrue(ComponentFit.battery(trainer, WING, BATTERY_6S_1100));
        assertTrue(ComponentFit.battery(trainer, WING, BATTERY_4S_650));
        assertFalse(ComponentFit.battery(trainer, WING, BATTERY_1S_300));

        float carrier = PLANE_FRAME_CARRIER.frameSize();
        assertTrue(ComponentFit.battery(carrier, WING, BATTERY_6S_5000));
        assertFalse(ComponentFit.battery(carrier, WING, BATTERY_6S_1100));
    }

    @Test
    void aFrameNeverTakesTheOtherKindOfPropulsion() {
        assertFalse(ComponentFit.motor(FRAME_5INCH.frameSize(), ROTOR, MOTOR_3548_900));
        assertFalse(ComponentFit.propeller(FRAME_5INCH.frameSize(), ROTOR, PROP_10X5));
        assertFalse(ComponentFit.propeller(PLANE_FRAME_CARGO.frameSize(), WING, PROP_5X4X3));
        assertFalse(ComponentFit.motor(PLANE_FRAME_CARGO.frameSize(), WING, MOTOR_1103_8000));
    }

    @Test
    void onlyALightCameraFitsAWhoop() {
        assertTrue(ComponentFit.camera(FRAME_TINY_WHOOP.frameSize(), CAMERA_STANDARD));
        assertFalse(ComponentFit.camera(FRAME_TINY_WHOOP.frameSize(), CAMERA_WIDE));
        assertTrue(ComponentFit.camera(FRAME_CINEWHOOP.frameSize(), CAMERA_WIDE));
        assertTrue(ComponentFit.camera(PLANE_FRAME_CARGO.frameSize(), CAMERA_WIDE));
    }

    @Test
    void firmwareNeedsItsMinimumFrame() {
        assertFalse(ComponentFit.firmware(FRAME_TINY_WHOOP.frameSize(), FirmwareType.COMPANION));
        assertTrue(ComponentFit.firmware(FRAME_CINEWHOOP.frameSize(), FirmwareType.COMPANION));
        assertTrue(ComponentFit.firmware(FRAME_CINEWHOOP.frameSize(), FirmwareType.TRADER));
        assertFalse(ComponentFit.firmware(FRAME_CINEWHOOP.frameSize(), FirmwareType.GUARDIAN));
        assertTrue(ComponentFit.firmware(FRAME_5INCH.frameSize(), FirmwareType.GUARDIAN));
        for (FirmwareType type : FirmwareType.values()) {
            assertTrue(ComponentFit.firmware(FRAME_OCTO_8INCH.frameSize(), type), type.name());
        }
    }

    @Test
    void heavyModulesNeedAHeavyFrame() {
        float whoop = FRAME_TINY_WHOOP.frameSize();
        for (ModuleType type : ModuleType.values()) {
            assertFalse(ComponentFit.module(whoop, ROTOR, type), type + " must not fit a whoop");
        }

        float cine = FRAME_CINEWHOOP.frameSize();
        assertTrue(ComponentFit.module(cine, ROTOR, ModuleType.PAYLOAD_BAY));
        assertTrue(ComponentFit.module(cine, ROTOR, ModuleType.STORAGE));
        assertFalse(ComponentFit.module(cine, ROTOR, ModuleType.WARHEAD));
        assertFalse(ComponentFit.module(cine, ROTOR, ModuleType.DART_GUN));
        assertFalse(ComponentFit.module(cine, ROTOR, ModuleType.ROCKET_POD));

        float five = FRAME_5INCH.frameSize();
        assertTrue(ComponentFit.module(five, ROTOR, ModuleType.WARHEAD));
        assertTrue(ComponentFit.module(five, ROTOR, ModuleType.DART_GUN));
        assertTrue(ComponentFit.module(five, ROTOR, ModuleType.ROCKET_POD));
        assertFalse(ComponentFit.module(five, ROTOR, ModuleType.TARGETING));
        assertTrue(ComponentFit.module(FRAME_OCTO_8INCH.frameSize(), ROTOR, ModuleType.TARGETING));
    }

    @Test
    void aWingOnlyCarriesWhatItCanUse() {
        float kamikaze = PLANE_FRAME_KAMIKAZE.frameSize();
        assertTrue(ComponentFit.module(kamikaze, WING, ModuleType.WARHEAD));
        assertTrue(ComponentFit.module(kamikaze, WING, ModuleType.STORAGE));
        assertFalse(ComponentFit.module(kamikaze, WING, ModuleType.PAYLOAD_BAY));
        assertFalse(ComponentFit.module(kamikaze, WING, ModuleType.DART_GUN));
        assertFalse(ComponentFit.module(kamikaze, WING, ModuleType.ROCKET_POD));
        assertFalse(ComponentFit.module(kamikaze, WING, ModuleType.TARGETING));
        assertTrue(ComponentFit.module(PLANE_FRAME_SCOUT.frameSize(), WING, ModuleType.TARGETING));
    }
}
