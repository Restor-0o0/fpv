package org.restor.fpv.assembly;

import net.minecraft.world.item.Item;
import org.restor.fpv.component.ModComponents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ЗДЕСЬ ВВОДЯТСЯ КООРДИНАТЫ ИЗ BLOCKBENCH.
 *
 * Для каждой рамы укажи, где стоят компоненты. Открой модель рамы в Blockbench,
 * наведись на точку крепления (центр площадки мотора, центр аккума и т.д.),
 * возьми её координаты в ПИКСЕЛЯХ модели (0..16, центр блока = 8,8,8) и подставь в
 * MountPoint.fromBlockbench(x, y, z). Ось Z: нос дрона = меньшие значения Z
 * (в Blockbench это "north").
 *
 * Порядок моторов: 0=перед-право, 1=перед-лево, 2=зад-право, 3=зад-лево,
 * дальше парами (середина-право, середина-лево, ...) для гекса/окто.
 *
 * Вторая часть каждой рамы — FrameTuning: чем она отличается в полёте
 * (скорость вращения, отзывчивость, сопротивление воздуха, живучесть,
 * грузоподъёмность; для самолётов ещё крыло).
 */
public final class FrameSpecs {
    private static final Map<Item, FrameSpec> SPECS = new HashMap<>();

    // 5-inch: classic true-X quad, the baseline every other frame is measured against.
    public static final FrameSpec FRAME_5INCH = quad(
        MountPoint.fromBlockbench(13.5f, 8.5f, 2.5f),
        MountPoint.fromBlockbench(2.5f, 8.5f, 2.5f),
        MountPoint.fromBlockbench(13.5f, 8.5f, 13.5f),
        MountPoint.fromBlockbench(2.5f, 8.5f, 13.5f),
        MountPoint.fromBlockbench(8f, 9.6f, 8f),         // battery bottom on top plate
        MountPoint.fromBlockbench(8f, 8.65f, 4.3f),      // camera rear pivot inside cage
        MountPoint.fromBlockbench(8f, 7.25f, 8f),        // module top against belly
        0.49f,
        FrameTuning.rotor(1.00f, 1.00f, 1.00f, 1.00f, 2.5f));

    // 7-inch long-range: wider X, heavy, calm, hauls a real warhead.
    public static final FrameSpec FRAME_7INCH = quad(
        MountPoint.fromBlockbench(15.3f, 8.5f, 0.75f),
        MountPoint.fromBlockbench(0.7f, 8.5f, 0.75f),
        MountPoint.fromBlockbench(14.6f, 8.5f, 15.15f),
        MountPoint.fromBlockbench(1.4f, 8.5f, 15.15f),
        MountPoint.fromBlockbench(8f, 9.6f, 8f),
        MountPoint.fromBlockbench(8f, 8.7f, 3.2f),
        MountPoint.fromBlockbench(8f, 7.2f, 8f),
        0.66f,
        FrameTuning.rotor(0.85f, 0.90f, 0.75f, 1.10f, 4.0f));

    // Tiny whoop: tight square, spins on a coin, falls apart if you look at it wrong.
    public static final FrameSpec FRAME_TINY_WHOOP = quad(
        MountPoint.fromBlockbench(11.5f, 8f, 4.5f),
        MountPoint.fromBlockbench(4.5f, 8f, 4.5f),
        MountPoint.fromBlockbench(11.5f, 8f, 11.5f),
        MountPoint.fromBlockbench(4.5f, 8f, 11.5f),
        MountPoint.fromBlockbench(8f, 9.35f, 8f),
        MountPoint.fromBlockbench(8f, 8.4f, 5.6f),
        MountPoint.fromBlockbench(8f, 7.3f, 8f),
        0.31f,
        FrameTuning.rotor(1.35f, 1.30f, 2.40f, 0.50f, 0.2f));

    // Cinewhoop: ducted, draggy and stable — a flying tripod.
    public static final FrameSpec FRAME_CINEWHOOP = quad(
        MountPoint.fromBlockbench(12.5f, 8.5f, 3.5f),
        MountPoint.fromBlockbench(3.5f, 8.5f, 3.5f),
        MountPoint.fromBlockbench(12.5f, 8.5f, 12.5f),
        MountPoint.fromBlockbench(3.5f, 8.5f, 12.5f),
        MountPoint.fromBlockbench(8f, 9.5f, 8f),
        MountPoint.fromBlockbench(8f, 8.7f, 5f),
        MountPoint.fromBlockbench(8f, 7.2f, 8f),
        0.40f,
        FrameTuning.rotor(0.80f, 0.85f, 2.00f, 1.25f, 1.5f));

    // Interceptor: a torpedo that flies nose-first. Four motors ring the tail and their
    // props stand vertical, so thrust runs down the tube axis instead of straight up.
    // Mount order here is right / top / left / bottom, which keeps opposite rotors
    // counter-rotating under the even-CCW, odd-CW convention.
    public static final FrameSpec FRAME_INTERCEPTOR = new FrameSpec(4, List.of(
        MountPoint.fromBlockbench(14.3f, 8f, 12.9f),
        MountPoint.fromBlockbench(8f, 14.3f, 12.9f),
        MountPoint.fromBlockbench(1.7f, 8f, 12.9f),
        MountPoint.fromBlockbench(8f, 1.7f, 12.9f)),
        MountPoint.fromBlockbench(8f, 10.4f, 8.2f),
        MountPoint.fromBlockbench(8f, 8.2f, 2.6f),
        MountPoint.fromBlockbench(8f, 8f, 4.4f),
        0.40f,
        FrameKind.MULTIROTOR,
        FrameTuning.rotor(1.45f, 1.35f, 0.62f, 0.60f, 1.6f),
        FrameRole.INTERCEPTOR);

    public static final FrameSpec FRAME_HEXA_6INCH = new FrameSpec(6, List.of(
        MountPoint.fromBlockbench(14.4f, 8.5f, 1.85f),
        MountPoint.fromBlockbench(1.6f, 8.5f, 1.85f),
        MountPoint.fromBlockbench(15.5f, 8.5f, 8f),
        MountPoint.fromBlockbench(0.5f, 8.5f, 8f),
        MountPoint.fromBlockbench(14.4f, 8.5f, 14.15f),
        MountPoint.fromBlockbench(1.6f, 8.5f, 14.15f)),
        MountPoint.fromBlockbench(8f, 9.6f, 8f),
        MountPoint.fromBlockbench(8f, 8.7f, 3.3f),
        MountPoint.fromBlockbench(8f, 7.15f, 8f),
        0.60f,
        FrameKind.MULTIROTOR,
        FrameTuning.rotor(0.90f, 0.95f, 1.10f, 1.20f, 6.0f));

    public static final FrameSpec FRAME_OCTO_8INCH = new FrameSpec(8, List.of(
        MountPoint.fromBlockbench(13.35f, 8.5f, 2.65f),
        MountPoint.fromBlockbench(2.65f, 8.5f, 2.65f),
        MountPoint.fromBlockbench(15.55f, 8.5f, 8f),
        MountPoint.fromBlockbench(0.45f, 8.5f, 8f),
        MountPoint.fromBlockbench(13.35f, 8.5f, 13.35f),
        MountPoint.fromBlockbench(2.65f, 8.5f, 13.35f),
        MountPoint.fromBlockbench(8f, 8.5f, 15.55f),
        MountPoint.fromBlockbench(8f, 8.5f, 0.45f)),
        MountPoint.fromBlockbench(8f, 9.65f, 8f),
        MountPoint.fromBlockbench(8f, 8.7f, 3.4f),
        MountPoint.fromBlockbench(8f, 7.1f, 8f),
        0.78f,
        FrameKind.MULTIROTOR,
        FrameTuning.rotor(0.72f, 0.80f, 1.25f, 1.40f, 10.0f));

    // --- Самолётные рамы. Мотор один; его точка совпадает с местом винта в модели. ---

    public static final FrameSpec PLANE_TRAINER = plane(
        MountPoint.fromBlockbench(8f, 8f, 11.0f),
        MountPoint.fromBlockbench(8f, 8.7f, 7.0f),
        MountPoint.fromBlockbench(8f, 8.63f, 4.0f),
        MountPoint.fromBlockbench(8f, 7.0f, 8.4f),
        0.80f,
        FrameTuning.wing(1.00f, 1.00f, 1.00f, 1.00f, 2.0f, 6.5f, 0.13f, 5.0f, 70f));

    public static final FrameSpec PLANE_DELTA = plane(
        MountPoint.fromBlockbench(8f, 8f, 11.9f),
        MountPoint.fromBlockbench(8f, 8.7f, 7.4f),
        MountPoint.fromBlockbench(8f, 8.75f, 3.8f),
        MountPoint.fromBlockbench(8f, 7.0f, 8.6f),
        0.85f,
        FrameTuning.wing(1.30f, 1.20f, 0.85f, 1.00f, 3.0f, 5.0f, 0.09f, 9.0f, 110f));

    public static final FrameSpec PLANE_GLIDER = plane(
        MountPoint.fromBlockbench(8f, 8f, 1.75f),
        MountPoint.fromBlockbench(8f, 8.7f, 7.2f),
        MountPoint.fromBlockbench(8f, 8.8f, 3.6f),
        MountPoint.fromBlockbench(8f, 7.0f, 8.4f),
        1.05f,
        FrameTuning.wing(0.70f, 0.85f, 0.60f, 0.60f, 1.0f, 9.5f, 0.20f, 3.0f, 55f));

    public static final FrameSpec PLANE_STRIKE = plane(
        MountPoint.fromBlockbench(8f, 8f, 13.6f),
        MountPoint.fromBlockbench(8f, 8.7f, 8.0f),
        MountPoint.fromBlockbench(8f, 9.05f, 3.2f),
        MountPoint.fromBlockbench(8f, 7.0f, 9.0f),
        0.95f,
        FrameTuning.wing(0.75f, 0.80f, 0.95f, 1.20f, 8.0f, 8.0f, 0.12f, 6.5f, 95f),
        FrameRole.KAMIKAZE);

    public static final FrameSpec PLANE_CARGO = plane(
        MountPoint.fromBlockbench(8f, 8f, 10.6f),
        MountPoint.fromBlockbench(8f, 8.8f, 7.0f),
        MountPoint.fromBlockbench(8f, 8.9f, 3.2f),
        MountPoint.fromBlockbench(8f, 6.9f, 8.2f),
        1.10f,
        FrameTuning.wing(0.60f, 0.75f, 1.25f, 1.40f, 12.0f, 11.0f, 0.14f, 6.0f, 60f));

    public static final FrameSpec PLANE_WING = plane(
        MountPoint.fromBlockbench(8f, 8f, 2.5f),
        MountPoint.fromBlockbench(8f, 8.7f, 8.0f),
        MountPoint.fromBlockbench(8f, 8.5f, 4.4f),
        MountPoint.fromBlockbench(8f, 7.0f, 9.2f),
        0.75f,
        FrameTuning.wing(1.50f, 1.35f, 0.70f, 0.70f, 3.5f, 4.2f, 0.10f, 8.0f, 130f));

    // Spotter: big slow observation wing. Useless on its own, decisive with a
    // targeting pod, which opens its strike bay.
    public static final FrameSpec PLANE_SPOTTER = plane(
        MountPoint.fromBlockbench(8f, 8f, 11.0f),
        MountPoint.fromBlockbench(8f, 8.7f, 7.2f),
        MountPoint.fromBlockbench(8f, 8.63f, 4.0f),
        MountPoint.fromBlockbench(8f, 7.0f, 8.4f),
        1.15f,
        FrameTuning.wing(0.60f, 0.75f, 0.55f, 0.75f, 1.5f, 12.0f, 0.24f, 2.6f, 45f),
        FrameRole.SPOTTER);

    public static final FrameSpec PLANE_KAMIKAZE = plane(
        MountPoint.fromBlockbench(8f, 8f, 11.0f),
        MountPoint.fromBlockbench(8f, 8.7f, 7.6f),
        MountPoint.fromBlockbench(8f, 8.63f, 4.0f),
        MountPoint.fromBlockbench(8f, 7.0f, 8.8f),
        0.80f,
        FrameTuning.wing(1.15f, 1.20f, 0.80f, 0.55f, 4.0f, 4.6f, 0.11f, 7.5f, 130f),
        FrameRole.KAMIKAZE);

    // Carrier: single tail-mounted pusher on a slow, tough hauler whose bay always takes
    // FPV cards. Wing-mounted props would slice straight through the wing model.
    public static final FrameSpec PLANE_CARRIER = plane(
        MountPoint.fromBlockbench(8f, 8f, 11.3f),
        MountPoint.fromBlockbench(8f, 8.8f, 7.0f),
        MountPoint.fromBlockbench(8f, 9.2f, 2.8f),
        MountPoint.fromBlockbench(8f, 6.9f, 8.2f),
        1.30f,
        FrameTuning.wing(0.45f, 0.65f, 1.45f, 1.60f, 20.0f, 15.0f, 0.17f, 5.0f, 55f),
        FrameRole.CARRIER);

    public static final FrameSpec PLANE_LOITER = plane(
        MountPoint.fromBlockbench(8f, 9.8f, 12.4f),
        MountPoint.fromBlockbench(8f, 8.7f, 7.4f),
        MountPoint.fromBlockbench(8f, 8.9f, 3.4f),
        MountPoint.fromBlockbench(8f, 7.0f, 8.8f),
        1.05f,
        FrameTuning.wing(0.65f, 0.75f, 0.95f, 1.15f, 16.0f, 8.0f, 0.13f, 6.2f, 90f),
        FrameRole.LOITER);

    private static FrameSpec plane(MountPoint motor, MountPoint battery, MountPoint camera,
                                   MountPoint module, float armLength, FrameTuning tuning) {
        return plane(motor, battery, camera, module, armLength, tuning, FrameRole.STANDARD);
    }

    private static FrameSpec plane(MountPoint motor, MountPoint battery, MountPoint camera,
                                   MountPoint module, float armLength, FrameTuning tuning,
                                   FrameRole role) {
        return new FrameSpec(1, List.of(motor), battery, camera, module, armLength,
            FrameKind.FIXED_WING, tuning, role);
    }

    private static FrameSpec quad(MountPoint fr, MountPoint fl, MountPoint rr, MountPoint rl,
                                  MountPoint battery, MountPoint camera, MountPoint module,
                                  float armLength, FrameTuning tuning) {
        return new FrameSpec(4, List.of(fr, fl, rr, rl), battery, camera, module, armLength,
            FrameKind.MULTIROTOR, tuning);
    }

    /** Frame item → its layout. Null if the item is not a known frame. */
    public static FrameSpec byItem(Item item) {
        if (SPECS.isEmpty()) {
            SPECS.put(ModComponents.FRAME_5INCH.get(), FRAME_5INCH);
            SPECS.put(ModComponents.FRAME_7INCH.get(), FRAME_7INCH);
            SPECS.put(ModComponents.FRAME_TINY_WHOOP.get(), FRAME_TINY_WHOOP);
            SPECS.put(ModComponents.FRAME_CINEWHOOP.get(), FRAME_CINEWHOOP);
            SPECS.put(ModComponents.FRAME_INTERCEPTOR.get(), FRAME_INTERCEPTOR);
            SPECS.put(ModComponents.FRAME_HEXA_6INCH.get(), FRAME_HEXA_6INCH);
            SPECS.put(ModComponents.FRAME_OCTO_8INCH.get(), FRAME_OCTO_8INCH);
            SPECS.put(ModComponents.PLANE_FRAME_TRAINER.get(), PLANE_TRAINER);
            SPECS.put(ModComponents.PLANE_FRAME_DELTA.get(), PLANE_DELTA);
            SPECS.put(ModComponents.PLANE_FRAME_GLIDER.get(), PLANE_GLIDER);
            SPECS.put(ModComponents.PLANE_FRAME_STRIKE.get(), PLANE_STRIKE);
            SPECS.put(ModComponents.PLANE_FRAME_CARGO.get(), PLANE_CARGO);
            SPECS.put(ModComponents.PLANE_FRAME_WING.get(), PLANE_WING);
            SPECS.put(ModComponents.PLANE_FRAME_SCOUT.get(), PLANE_SPOTTER);
            SPECS.put(ModComponents.PLANE_FRAME_KAMIKAZE.get(), PLANE_KAMIKAZE);
            SPECS.put(ModComponents.PLANE_FRAME_CARRIER.get(), PLANE_CARRIER);
            SPECS.put(ModComponents.PLANE_FRAME_LOITER.get(), PLANE_LOITER);
        }
        return SPECS.get(item);
    }

    private FrameSpecs() {}
}
