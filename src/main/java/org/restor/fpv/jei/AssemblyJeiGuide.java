package org.restor.fpv.jei;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A JEI-only view of one compatible frame and its assembly parts. */
public record AssemblyJeiGuide(ItemStack frame, ItemStack output, List<ItemStack> batteries,
    List<ItemStack> cameras, List<ItemStack> flightControllers, List<ItemStack> videoTransmitters,
    List<ItemStack> motors, List<ItemStack> propellers, List<ItemStack> optionalModules,
    List<ItemStack> optionalFirmware, int motorCount, List<ItemStack> extraParts,
    Component displayName, boolean preset) {

    public AssemblyJeiGuide(ItemStack frame, ItemStack output, List<ItemStack> batteries,
        List<ItemStack> cameras, List<ItemStack> flightControllers, List<ItemStack> videoTransmitters,
        List<ItemStack> motors, List<ItemStack> propellers, List<ItemStack> optionalModules,
        List<ItemStack> optionalFirmware, int motorCount) {
        this(frame, output, batteries, cameras, flightControllers, videoTransmitters, motors,
            propellers, optionalModules, optionalFirmware, motorCount, List.of(),
            frame.getHoverName(), false);
    }
}
