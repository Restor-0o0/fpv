package org.restor.fpv.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.restor.fpv.compat.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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

        DroneEntity drone = spawn((ServerLevel) level, spawnPos,
            player != null ? player.getYRot() : 0, stack, player);
        if (drone != null && player != null && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    public DroneEntity spawn(ServerLevel level, Vec3 pos, float yRot, ItemStack stack,
                             net.minecraft.world.entity.player.Player owner) {
        EntityType<? extends DroneEntity> type = entityTypeSupplier.get();
        DroneEntity drone = type.create(level);
        if (drone == null) return null;
        drone.setPos(pos);
        drone.setYRot(yRot);
        CustomData data = CustomData.get(stack);
        if (data != null && (usesNbt || data.contains("Battery"))) {
            drone.readFromItemTag(data.copyTag());
        }
        if (owner != null) drone.claimOwner(owner);
        if (drone.isRemoteControllable()) {
            String def = org.restor.fpv.Config.DEFAULT_LINK_CODE.get();
            if (def.matches("\\d{6}")) drone.setLinkCode(def);
        }
        level.addFreshEntity(drone);
        return drone;
    }

    /** Ready to strike: a dedicated kamikaze airframe, or a build carrying a warhead module. */
    public static boolean isArmed(ItemStack stack) {
        if (!(stack.getItem() instanceof DroneItem)) return false;
        if (stack.is(ModItems.KAMIKAZE_DRONE.get())) return true;
        CustomData data = CustomData.get(stack);
        if (data == null || !data.contains("Assembly")) return false;
        return org.restor.fpv.assembly.DroneAssembly.load(data.copyTag().getCompound("Assembly"))
            .moduleType() == org.restor.fpv.component.ModuleType.WARHEAD;
    }
}
