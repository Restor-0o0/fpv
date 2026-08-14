package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.race.GhostRecording;

// Server -> client: the ghost recordings to play back for the run that is starting. Sent once
// at run start alongside the RaceTimerPacket so both share the same startTime clock. An empty
// packet (no personal, no overall) clears any previously loaded ghosts.
public record GhostDataPacket(long startTime, boolean personalIsOverall, GhostRecording personal,
                               GhostRecording overall) implements CustomPacketPayload {
    public static final Type<GhostDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "ghost_data"));

    public static final StreamCodec<FriendlyByteBuf, GhostDataPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeLong(pkt.startTime);
            buf.writeBoolean(pkt.personalIsOverall);
            buf.writeBoolean(pkt.personal != null);
            if (pkt.personal != null) pkt.personal.write(buf);
            buf.writeBoolean(pkt.overall != null);
            if (pkt.overall != null) pkt.overall.write(buf);
        },
        buf -> new GhostDataPacket(
            buf.readLong(),
            buf.readBoolean(),
            buf.readBoolean() ? GhostRecording.read(buf) : null,
            buf.readBoolean() ? GhostRecording.read(buf) : null)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GhostDataPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> org.restor.fpv.client.GhostRenderer.setGhosts(
            pkt.startTime(), pkt.personalIsOverall(), pkt.personal(), pkt.overall()));
    }
}
