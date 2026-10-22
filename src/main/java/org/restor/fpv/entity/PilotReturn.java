package org.restor.fpv.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where a pilot's body belongs once an FPV session ends, and the only way it is put there. */
public record PilotReturn(ResourceKey<Level> dimension, Vec3 pos) {

    public static PilotReturn capture(ServerPlayer player, Entity craft) {
        Vec3 pos = player.position();
        if (pos.distanceToSqr(craft.position()) < 1.0E-4) pos = pos.add(0, 1.0, 0);
        return new PilotReturn(player.level().dimension(), pos);
    }

    public static PilotReturn at(Entity anchor) {
        return new PilotReturn(anchor.level().dimension(), anchor.position());
    }

    // ServerPlayer#teleportTo(ServerLevel, ...) is the only move that also tells the client and
    // then ignores its movement until it acknowledges. Entity#teleportTo is server-side only, so
    // the client's next position packet dragged the pilot straight back onto the wreck.
    public void send(ServerPlayer player) {
        ServerLevel target = player.getServer() == null ? null : player.getServer().getLevel(dimension);
        if (target == null) target = player.serverLevel();
        player.stopRiding();
        player.setCamera(player);
        player.teleportTo(target, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    public void save(CompoundTag tag, String prefix) {
        tag.putDouble(prefix + "X", pos.x);
        tag.putDouble(prefix + "Y", pos.y);
        tag.putDouble(prefix + "Z", pos.z);
        tag.putString(prefix + "Dim", dimension.location().toString());
    }

    public static PilotReturn load(CompoundTag tag, String prefix, Level fallback) {
        if (!tag.contains(prefix + "X")) return null;
        ResourceKey<Level> dim = fallback.dimension();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(prefix + "Dim"));
        if (id != null) dim = ResourceKey.create(Registries.DIMENSION, id);
        return new PilotReturn(dim, new Vec3(
            tag.getDouble(prefix + "X"), tag.getDouble(prefix + "Y"), tag.getDouble(prefix + "Z")));
    }
}
