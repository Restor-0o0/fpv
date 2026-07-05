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
 * Текущие значения — симметричные заглушки, полёт с ними корректен; замена на
 * реальные координаты влияет на рендер и на плечи моментов (перекос от пропов).
 */
public final class FrameSpecs {
    private static final Map<Item, FrameSpec> SPECS = new HashMap<>();

    // 5-inch: classic true-X quad.
    public static final FrameSpec FRAME_5INCH = quad(
        MountPoint.fromBlockbench(13.5f, 8.5f, 2.5f),   // TODO координаты из Blockbench
        MountPoint.fromBlockbench(2.5f, 8.5f, 2.5f),
        MountPoint.fromBlockbench(13.5f, 8.5f, 13.5f),
        MountPoint.fromBlockbench(2.5f, 8.5f, 13.5f),
        MountPoint.fromBlockbench(8f, 11f, 8f),          // battery (top plate)
        MountPoint.fromBlockbench(8f, 9f, 1.5f),         // camera (nose)
        0.49f);

    // 7-inch long-range: wider X.
    public static final FrameSpec FRAME_7INCH = quad(
        MountPoint.fromBlockbench(15f, 8.5f, 1f),        // TODO координаты из Blockbench
        MountPoint.fromBlockbench(1f, 8.5f, 1f),
        MountPoint.fromBlockbench(15f, 8.5f, 15f),
        MountPoint.fromBlockbench(1f, 8.5f, 15f),
        MountPoint.fromBlockbench(8f, 11.5f, 8f),
        MountPoint.fromBlockbench(8f, 9f, 0.5f),
        0.62f);

    // Tiny whoop: tight square.
    public static final FrameSpec FRAME_TINY_WHOOP = quad(
        MountPoint.fromBlockbench(11.5f, 8f, 4.5f),      // TODO координаты из Blockbench
        MountPoint.fromBlockbench(4.5f, 8f, 4.5f),
        MountPoint.fromBlockbench(11.5f, 8f, 11.5f),
        MountPoint.fromBlockbench(4.5f, 8f, 11.5f),
        MountPoint.fromBlockbench(8f, 10f, 8f),
        MountPoint.fromBlockbench(8f, 9f, 4f),
        0.31f);

    // Cinewhoop: square with ducts.
    public static final FrameSpec FRAME_CINEWHOOP = quad(
        MountPoint.fromBlockbench(12.5f, 8.5f, 3.5f),    // TODO координаты из Blockbench
        MountPoint.fromBlockbench(3.5f, 8.5f, 3.5f),
        MountPoint.fromBlockbench(12.5f, 8.5f, 12.5f),
        MountPoint.fromBlockbench(3.5f, 8.5f, 12.5f),
        MountPoint.fromBlockbench(8f, 11f, 8f),
        MountPoint.fromBlockbench(8f, 9f, 2.5f),
        0.40f);

    public static final FrameSpec FRAME_HEXA_6INCH = new FrameSpec(6, List.of(
        MountPoint.fromBlockbench(14.5f, 8.5f, 2f),
        MountPoint.fromBlockbench(1.5f, 8.5f, 2f),
        MountPoint.fromBlockbench(15f, 8.5f, 8f),
        MountPoint.fromBlockbench(1f, 8.5f, 8f),
        MountPoint.fromBlockbench(14.5f, 8.5f, 14f),
        MountPoint.fromBlockbench(1.5f, 8.5f, 14f)),
        MountPoint.fromBlockbench(8f, 11.5f, 8f),
        MountPoint.fromBlockbench(8f, 9f, -0.5f),
        0.56f);

    public static final FrameSpec FRAME_OCTO_8INCH = new FrameSpec(8, List.of(
        MountPoint.fromBlockbench(15.5f, 8.5f, 0.5f),
        MountPoint.fromBlockbench(0.5f, 8.5f, 0.5f),
        MountPoint.fromBlockbench(15.5f, 8.5f, 5.5f),
        MountPoint.fromBlockbench(0.5f, 8.5f, 5.5f),
        MountPoint.fromBlockbench(15.5f, 8.5f, 10.5f),
        MountPoint.fromBlockbench(0.5f, 8.5f, 10.5f),
        MountPoint.fromBlockbench(15.5f, 8.5f, 15.5f),
        MountPoint.fromBlockbench(0.5f, 8.5f, 15.5f)),
        MountPoint.fromBlockbench(8f, 12f, 8f),
        MountPoint.fromBlockbench(8f, 9f, -1f),
        0.72f);

    private static FrameSpec quad(MountPoint fr, MountPoint fl, MountPoint rr, MountPoint rl,
                                  MountPoint battery, MountPoint camera, float armLength) {
        return new FrameSpec(4, List.of(fr, fl, rr, rl), battery, camera, armLength);
    }

    /** Frame item → its layout. Null if the item is not a known frame. */
    public static FrameSpec byItem(Item item) {
        if (SPECS.isEmpty()) {
            SPECS.put(ModComponents.FRAME_5INCH.get(), FRAME_5INCH);
            SPECS.put(ModComponents.FRAME_7INCH.get(), FRAME_7INCH);
            SPECS.put(ModComponents.FRAME_TINY_WHOOP.get(), FRAME_TINY_WHOOP);
            SPECS.put(ModComponents.FRAME_CINEWHOOP.get(), FRAME_CINEWHOOP);
            SPECS.put(ModComponents.FRAME_HEXA_6INCH.get(), FRAME_HEXA_6INCH);
            SPECS.put(ModComponents.FRAME_OCTO_8INCH.get(), FRAME_OCTO_8INCH);
        }
        return SPECS.get(item);
    }

    private FrameSpecs() {}
}
