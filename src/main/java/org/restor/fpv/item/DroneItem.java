package org.restor.fpv.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.entity.ComponentDroneEntity;
import org.restor.fpv.entity.DroneEntity;

import java.util.function.Supplier;

public class DroneItem extends Item {
    private final Supplier<? extends EntityType<? extends DroneEntity>> entityTypeSupplier;
    private final boolean usesNbt;

    public DroneItem(Supplier<? extends EntityType<? extends DroneEntity>> entityTypeSupplier, boolean usesNbt) {
        super(new Item.Properties().stacksTo(16));
        this.entityTypeSupplier = entityTypeSupplier;
        this.usesNbt = usesNbt;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        var player = context.getPlayer();
        var pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();

        Vec3 spawnPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        if (!state.getCollisionShape(level, pos).isEmpty()) {
            spawnPos = spawnPos.add(0, 0.5, 0);
        }

        EntityType<? extends DroneEntity> type = entityTypeSupplier.get();
        DroneEntity drone = type.create((ServerLevel) level);
        if (drone != null) {
            drone.setPos(spawnPos);
            drone.setYRot(player != null ? player.getYRot() : 0);
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (usesNbt && data != null) {
                drone.readFromItemTag(data.copyTag());
            }
            // Newly placed remote drones adopt the remembered link code (if any).
            if (drone.isRemoteControllable()) {
                String def = org.restor.fpv.Config.DEFAULT_LINK_CODE.get();
                if (def.matches("\\d{6}")) drone.setLinkCode(def);
            }
            level.addFreshEntity(drone);
            if (player != null && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.SUCCESS;
    }
}
