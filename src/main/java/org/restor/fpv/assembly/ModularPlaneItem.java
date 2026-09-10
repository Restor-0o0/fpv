package org.restor.fpv.assembly;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.entity.ModEntities;

import java.util.List;

// Item form of a bench-built airframe; the whole assembly lives in its NBT.
public class ModularPlaneItem extends Item {
    public ModularPlaneItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ModularPlaneEntity plane = spawn(context);
        if (plane == null) return InteractionResult.FAIL;
        ItemStack stack = context.getItemInHand();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) plane.readFromItemTag(data.copyTag());
        level.addFreshEntity(plane);
        var player = context.getPlayer();
        if (player != null && !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** Positions an airframe on the clicked block; the caller fills it in and adds it. */
    public static ModularPlaneEntity spawn(UseOnContext context) {
        Level level = context.getLevel();
        var pos = context.getClickedPos();
        Vec3 spawnPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            spawnPos = spawnPos.add(0, 0.5, 0);
        }
        ModularPlaneEntity plane = ModEntities.MODULAR_PLANE.get().create((ServerLevel) level);
        if (plane == null) return null;
        plane.setPos(spawnPos);
        plane.setYRot(context.getPlayer() != null ? context.getPlayer().getYRot() : 0);
        return plane;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains("Assembly")) return;
        DroneAssembly a = DroneAssembly.load(data.copyTag().getCompound("Assembly"));
        if (!a.isComplete()) tooltip.add(Component.translatable("tooltip.fpv.assembly_incomplete"));
        if (!a.frame.isEmpty())   tooltip.add(Component.literal("§7" + a.frame.getHoverName().getString()));
        if (!a.battery.isEmpty()) tooltip.add(Component.literal("§8+ " + a.battery.getHoverName().getString()));
        if (!a.camera.isEmpty())  tooltip.add(Component.literal("§8+ " + a.camera.getHoverName().getString()));
        if (!a.module.isEmpty())  tooltip.add(Component.literal("§8+ " + a.module.getHoverName().getString()));
        tooltip.add(Component.translatable("tooltip.fpv.assembly_condition",
            Math.round(a.minimumCondition() / 10f)));
        FrameTuning t = a.tuning();
        tooltip.add(Component.literal(String.format("§8%.0f g · %.0f N · Vmax %.0f m/s",
            a.totalMassKg() * 1000, a.totalThrustN(), t.maxSpeed())));
    }
}
