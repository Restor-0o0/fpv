package org.restor.fpv.entity;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where a pilot's body belongs once an FPV session ends, and the only way it is put there. */
public record PilotReturn(ResourceKey<Level> dimension, Vec3 pos) {
    public static final Codec<PilotReturn> CODEC = RecordCodecBuilder.create(i -> i.group(
        Level.RESOURCE_KEY_CODEC.fieldOf("Dim").forGetter(PilotReturn::dimension),
        Vec3.CODEC.fieldOf("Pos").forGetter(PilotReturn::pos)
    ).apply(i, PilotReturn::new));

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
        ServerLevel target = player.level().getServer().getLevel(dimension);
        if (target == null) target = player.level();
        player.stopRiding();
        player.setCamera(player);
        player.teleportTo(target, pos.x, pos.y, pos.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }
}
