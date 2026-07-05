package org.restor.fpv.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import org.restor.fpv.entity.PlaneEntity;

import java.util.function.Supplier;

public class PlaneItem extends Item {
    private final Supplier<? extends EntityType<? extends PlaneEntity>> type;

    public PlaneItem(Supplier<? extends EntityType<? extends PlaneEntity>> type) {
        super(new Item.Properties().stacksTo(16));
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        var player = context.getPlayer();
        var pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        Vec3 spawnPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            spawnPos = spawnPos.add(0, 0.5, 0);
        }

        PlaneEntity plane = type.get().create((ServerLevel) level);
        if (plane != null) {
            plane.setPos(spawnPos);
            plane.setYRot(player != null ? player.getYRot() : 0);
            level.addFreshEntity(plane);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
