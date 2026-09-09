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
        super(new Item.Properties().stacksTo(16));
        this.stats = stats;
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
        tooltip.add(Component.literal("§7Type: " + getLocalizedName()));
        tooltip.add(Component.translatable("tooltip.fpv.component_condition", ComponentCondition.percent(stack)));
        switch (stats.type()) {
            case FRAME -> {
                tooltip.add(Component.literal("§8Size: " + stats.frameSize() + "mm"));
                tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
                appendFrameTuning(stack, tooltip);
            }
            case MOTOR -> {
                tooltip.add(Component.literal("§8KV: " + (int) stats.motorKv()));
                tooltip.add(Component.literal("§8Power: " + stats.motorPower()));
                tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
            }
            case BATTERY -> {
                tooltip.add(Component.literal("§8" + stats.batteryCells() + "S " + stats.batteryCapacity() + "mAh"));
                tooltip.add(Component.literal("§8Discharge: " + stats.batteryMaxDischarge() + "C"));
                tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
            }
            case PROPELLER -> {
                tooltip.add(Component.literal("§8" + stats.propDiameter() + "x" + stats.propPitch() + "x" + stats.propBlades()));
                tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
            }
            case VTX -> {
                tooltip.add(Component.literal("§8Power: " + stats.vtxPowerMw() + "mW"));
                tooltip.add(Component.literal("§8Range: x" + stats.vtxRangeMultiplier()));
            }
            case CAMERA -> {
                tooltip.add(Component.literal("§8FOV: " + (int) stats.cameraFov() + "°"));
                tooltip.add(Component.literal("§8Tilt: " + (int) stats.cameraTiltAngle() + "°"));
            }
            case FLIGHT_CONTROLLER -> {
                if (stats.fcHasOsd()) tooltip.add(Component.literal("§8Has OSD"));
                if (stats.fcHasBlackbox()) tooltip.add(Component.literal("§8Has Blackbox"));
                tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
            }
            case MODULE, FIRMWARE -> tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
        }
        tooltip.add(Component.translatable("tooltip.fpv.component_place"));
    }

    private static void appendFrameTuning(ItemStack stack, List<Component> tooltip) {
        var spec = org.restor.fpv.assembly.FrameSpecs.byItem(stack.getItem());
        if (spec == null) return;
        var t = spec.tuning();
        if (spec.isFixedWing()) {
            tooltip.add(Component.literal("§8Wing: " + fmt(t.wingArea()) + " m² · Cl " + fmt(t.liftCoeff())));
            tooltip.add(Component.literal("§8Stall: " + fmt(t.stallSpeed()) + " m/s · Vmax "
                + Math.round(t.maxSpeed()) + " m/s"));
        }
        tooltip.add(Component.literal("§8Agility x" + fmt(t.agility()) + " · Drag x" + fmt(t.dragFactor())));
        tooltip.add(Component.literal("§8Toughness x" + fmt(t.toughness()) + " · Payload "
            + fmt(t.liftCapacityKg()) + " kg"));
    }

    private static String fmt(float v) {
        String out = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (out.endsWith("0")) out = out.substring(0, out.length() - 1);
        return out.endsWith(".") ? out.substring(0, out.length() - 1) : out;
    }

    private String getLocalizedName() {
        return switch (stats.type()) {
            case FRAME -> "Frame";
            case MOTOR -> "Motor";
            case BATTERY -> "Battery";
            case PROPELLER -> "Propeller";
            case VTX -> "VTX";
            case CAMERA -> "Camera";
            case FLIGHT_CONTROLLER -> "Flight Controller";
            case MODULE -> "Module";
            case FIRMWARE -> "Firmware";
        };
    }
}
