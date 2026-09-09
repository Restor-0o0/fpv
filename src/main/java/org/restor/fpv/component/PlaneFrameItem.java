package org.restor.fpv.component;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.restor.fpv.assembly.DroneAssembly;
import org.restor.fpv.assembly.ModularPlaneEntity;
import org.restor.fpv.assembly.ModularPlaneItem;
import org.restor.fpv.block.entity.PartsPileBlockEntity;

// Fixed-wing airframe: needs plane motors and plane props, and the bench builds a
// ModularPlaneEntity from it instead of a multirotor.
public class PlaneFrameItem extends FrameItem {
    public PlaneFrameItem(ComponentStats stats) {
        super(stats);
    }

    /**
     * Set down on its own an airframe becomes a bare, unflyable aircraft you can walk around
     * and pick back up. Sneaking, or clicking an existing pile, stores it as a part instead.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        var player = ctx.getPlayer();
        if (player != null && player.isSecondaryUseActive()) return super.useOn(ctx);
        if (level.getBlockEntity(ctx.getClickedPos()) instanceof PartsPileBlockEntity) {
            return super.useOn(ctx);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ModularPlaneEntity plane = ModularPlaneItem.spawn(ctx);
        if (plane == null) return InteractionResult.FAIL;
        ItemStack held = ctx.getItemInHand();
        DroneAssembly a = new DroneAssembly();
        a.frame = held.copyWithCount(1);
        plane.setAssembly(a);
        level.addFreshEntity(plane);
        if (player == null || !player.getAbilities().instabuild) held.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
