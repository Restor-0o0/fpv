package org.restor.fpv.component;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Type: " + getLocalizedName()));
        switch (stats.type()) {
            case FRAME -> {
                tooltip.add(Component.literal("§8Size: " + stats.frameSize() + "mm"));
                tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
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
            case MODULE -> tooltip.add(Component.literal("§8Weight: " + stats.weight() + "g"));
        }
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
        };
    }
}
