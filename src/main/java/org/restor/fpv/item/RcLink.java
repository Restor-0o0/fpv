package org.restor.fpv.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.restor.fpv.CommonConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

import java.util.UUID;

/** Pairing between an RC transmitter stack and the aircraft it flies. */
public final class RcLink {
    private RcLink() {}

    public static boolean isTransmitter(ItemStack stack) {
        return stack.getItem() instanceof RcTransmitterItem;
    }

    public static ItemStack heldTransmitter(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (isTransmitter(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasTransmitter(Player player) {
        if (!CommonConfig.REQUIRE_TRANSMITTER.get()) return true;
        return !heldTransmitter(player).isEmpty();
    }

    public static boolean hasGoggles(Player player) {
        if (!CommonConfig.REQUIRE_GOGGLES_FOR_FPV.get()) return true;
        return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
            .getItem() instanceof FpvGogglesItem;
    }

    public static UUID boundId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID("BoundEntity") ? tag.getUUID("BoundEntity") : null;
    }

    public static String boundName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        return data.copyTag().getString("BoundName");
    }

    public static void bind(ItemStack stack, Entity target) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID("BoundEntity", target.getUUID());
            tag.putString("BoundName", target.getType().getDescriptionId());
        });
    }

    public static void unbind(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove("BoundEntity");
            tag.remove("BoundName");
        });
    }

    public static Entity resolve(ServerLevel level, ItemStack stack) {
        UUID id = boundId(stack);
        return id == null ? null : level.getEntity(id);
    }

    public static boolean isAircraft(Entity entity) {
        return entity instanceof DroneEntity || entity instanceof PlaneEntity;
    }

    public static boolean isBindable(Entity entity) {
        return isAircraft(entity)
            && (!(entity instanceof org.restor.fpv.entity.Pilotable p) || p.isFlightReady());
    }

    /** Aircraft the player is aiming at, up to the transmitter's own range. */
    public static Entity aircraftInSight(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 ray = player.getViewVector(1.0f).scale(reach);
        Vec3 end = eye.add(ray);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
            player.getBoundingBox().expandTowards(ray).inflate(2.0),
            e -> isBindable(e) && !e.isSpectator() && e.isPickable(), reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    /** Binds when the target is new, clears the binding when it is the one already bound. */
    public static void toggleBinding(ServerPlayer player, ItemStack stack, Entity target) {
        boolean wasBound = target.getUUID().equals(boundId(stack));
        if (wasBound) {
            unbind(stack);
            player.displayClientMessage(Component.translatable("msg.fpv.tx_unlinked"), true);
        } else {
            bind(stack, target);
            player.displayClientMessage(Component.translatable("msg.fpv.tx_linked",
                Component.translatable(target.getType().getDescriptionId())), true);
        }
        player.level().playSound(null, player.blockPosition(),
            wasBound ? org.restor.fpv.sound.ModSounds.TX_UNBIND.get() : org.restor.fpv.sound.ModSounds.TX_BIND.get(),
            SoundSource.PLAYERS, 0.6f, wasBound ? 0.8f : 1.5f);
        if (player.serverLevel() != null) {
            player.serverLevel().sendParticles(
                wasBound ? ParticleTypes.SMOKE : ParticleTypes.END_ROD,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                wasBound ? 6 : 12, 0.25, 0.25, 0.25, 0.02);
        }
    }

    public static double range(ItemStack stack) {
        return stack.getItem() instanceof RcTransmitterItem tx && tx.longRange()
            ? CommonConfig.TX_LR_RANGE.get() : CommonConfig.TX_RANGE.get();
    }
}
