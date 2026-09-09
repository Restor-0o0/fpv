package org.restor.fpv.compat;

import net.minecraft.server.level.ServerPlayer;
import org.restor.fpv.network.ModNetworking;

public final class PacketDistributor {
    private PacketDistributor() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ModNetworking.channel().sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ModNetworking.channel().send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        ModNetworking.channel().send(
            net.minecraftforge.network.PacketDistributor.ALL.noArg(), payload);
    }
}
