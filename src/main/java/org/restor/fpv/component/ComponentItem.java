package org.restor.fpv.component;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.restor.fpv.assembly.ComponentCondition;
import org.restor.fpv.block.ModBlocks;
import org.restor.fpv.block.entity.PartsPileBlockEntity;

import java.util.List;

public class ComponentItem extends Item {
    private final ComponentStats stats;

    public ComponentItem(ComponentStats stats) {
        super(new Item.Properties().stacksTo(stackSizeFor(stats)));
        this.stats = stats;
    }

    /**
     * Parts stack by how much of a crate they take up, in powers of two so a full stack always
     * divides a 64-item block: a 2 g whoop prop goes to 64, a 320 g plane motor to 8.
     */
    private static int stackSizeFor(ComponentStats stats) {
        float grams = stats.weight();
        if (grams <= 15f) return 64;
        if (grams <= 60f) return 32;
        if (grams <= 200f) return 16;
        if (grams <= 800f) return 8;
        return 4;
    }

    public ComponentStats getStats() {
        return stats;
    }

    public ComponentType getComponentType() {
        return stats.type();
    }

    /** Setting a part down on a block starts a pile there; further parts stack into that same pile. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        BlockPos pos = place.getClickedPos();
        ItemStack held = ctx.getItemInHand();
        boolean creative = ctx.getPlayer() != null && ctx.getPlayer().getAbilities().instabuild;

        if (level.getBlockEntity(pos) instanceof PartsPileBlockEntity pile) {
            if (!pile.accepts(held)) return InteractionResult.PASS;
            if (!level.isClientSide) {
                pile.insert(creative ? held.copy() : held);
                playSet(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!place.canPlace()) return InteractionResult.PASS;
        BlockState state = ModBlocks.PARTS_PILE.get().getStateForPlacement(place);
        if (state == null || !state.canSurvive(level, pos)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!level.setBlock(pos, state, 3)) return InteractionResult.PASS;
            if (level.getBlockEntity(pos) instanceof PartsPileBlockEntity pile) {
                pile.insert(creative ? held.copy() : held, 1);
            }
            playSet(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void playSet(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7f, 1.4f);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.fpv.part.type", Component.translatable(typeKey())));
        tooltip.add(Component.translatable("tooltip.fpv.component_condition", ComponentCondition.percent(stack)));
        switch (stats.type()) {
            case FRAME -> {
                tooltip.add(Component.translatable("tooltip.fpv.part.size", fmt(stats.frameSize())));
                tooltip.add(Component.translatable("tooltip.fpv.part.weight", fmt(stats.weight())));
                appendFrameTuning(stack, tooltip);
            }
            case MOTOR -> {
                tooltip.add(Component.translatable("tooltip.fpv.part.kv", (int) stats.motorKv()));
                tooltip.add(Component.translatable("tooltip.fpv.part.power", fmt(stats.motorPower())));
                tooltip.add(Component.translatable("tooltip.fpv.part.weight", fmt(stats.weight())));
            }
            case BATTERY -> {
                tooltip.add(Component.translatable("tooltip.fpv.part.cells", stats.batteryCells(), stats.batteryCapacity()));
                tooltip.add(Component.translatable("tooltip.fpv.part.discharge", fmt(stats.batteryMaxDischarge())));
                tooltip.add(Component.translatable("tooltip.fpv.part.weight", fmt(stats.weight())));
            }
            case PROPELLER -> {
                tooltip.add(Component.translatable("tooltip.fpv.part.prop_size",
                    fmt(stats.propDiameter()), fmt(stats.propPitch()), stats.propBlades()));
                tooltip.add(Component.translatable("tooltip.fpv.part.weight", fmt(stats.weight())));
            }
            case VTX -> {
                tooltip.add(Component.translatable("tooltip.fpv.part.vtx_power", stats.vtxPowerMw()));
                tooltip.add(Component.translatable("tooltip.fpv.part.vtx_range", fmt(stats.vtxRangeMultiplier())));
            }
            case CAMERA -> {
                tooltip.add(Component.translatable("tooltip.fpv.part.fov", (int) stats.cameraFov()));
                tooltip.add(Component.translatable("tooltip.fpv.part.tilt", (int) stats.cameraTiltAngle()));
            }
            case FLIGHT_CONTROLLER -> {
                if (stats.fcHasOsd()) tooltip.add(Component.translatable("tooltip.fpv.part.osd"));
                if (stats.fcHasBlackbox()) tooltip.add(Component.translatable("tooltip.fpv.part.blackbox"));
                tooltip.add(Component.translatable("tooltip.fpv.part.weight", fmt(stats.weight())));
            }
            case MODULE, FIRMWARE -> tooltip.add(Component.translatable("tooltip.fpv.part.weight", fmt(stats.weight())));
        }
        tooltip.add(Component.translatable("tooltip.fpv.component_place"));
    }

    private static void appendFrameTuning(ItemStack stack, List<Component> tooltip) {
        var spec = org.restor.fpv.assembly.FrameSpecs.byItem(stack.getItem());
        if (spec == null) return;
        var t = spec.tuning();
        if (spec.isFixedWing()) {
            tooltip.add(Component.translatable("tooltip.fpv.part.wing", fmt(t.wingArea()), fmt(t.liftCoeff())));
            tooltip.add(Component.translatable("tooltip.fpv.part.stall", fmt(t.stallSpeed()), Math.round(t.maxSpeed())));
        }
        tooltip.add(Component.translatable("tooltip.fpv.part.agility", fmt(t.agility()), fmt(t.dragFactor())));
        tooltip.add(Component.translatable("tooltip.fpv.part.toughness", fmt(t.toughness()), fmt(t.liftCapacityKg())));
    }

    private static String fmt(float v) {
        String out = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (out.endsWith("0")) out = out.substring(0, out.length() - 1);
        return out.endsWith(".") ? out.substring(0, out.length() - 1) : out;
    }

    private String typeKey() {
        return switch (stats.type()) {
            case FRAME -> "screen.fpv.assembly.part.frame";
            case MOTOR -> "tooltip.fpv.part.type_motor";
            case BATTERY -> "screen.fpv.assembly.part.battery";
            case PROPELLER -> "tooltip.fpv.part.type_propeller";
            case VTX -> "tooltip.fpv.part.type_vtx";
            case CAMERA -> "screen.fpv.assembly.part.camera";
            case FLIGHT_CONTROLLER -> "tooltip.fpv.part.type_fc";
            case MODULE -> "screen.fpv.assembly.part.module";
            case FIRMWARE -> "tooltip.fpv.part.type_firmware";
        };
    }
}
