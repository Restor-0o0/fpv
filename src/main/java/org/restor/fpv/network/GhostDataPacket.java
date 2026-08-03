package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.race.GhostRecording;

import java.util.function.Supplier;

// Server -> client: the ghost recordings to play back for the run that is starting. Sent once
// at run start alongside the RaceTimerPacket so both share the same startTime clock. An empty
// packet (no personal, no overall) clears any previously loaded ghosts.
public class GhostDataPacket {
    private final long startTime;
    private final boolean personalIsOverall; // player holds the track record → one ghost, "both" colour
    private final GhostRecording personal;   // nullable
    private final GhostRecording overall;    // nullable

    public GhostDataPacket(long startTime, boolean personalIsOverall,
                           GhostRecording personal, GhostRecording overall) {
        this.startTime = startTime;
        this.personalIsOverall = personalIsOverall;
        this.personal = personal;
        this.overall = overall;
    }

    public GhostDataPacket(FriendlyByteBuf buf) {
        this.startTime = buf.readLong();
        this.personalIsOverall = buf.readBoolean();
        this.personal = buf.readBoolean() ? GhostRecording.read(buf) : null;
        this.overall  = buf.readBoolean() ? GhostRecording.read(buf) : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(startTime);
        buf.writeBoolean(personalIsOverall);
        buf.writeBoolean(personal != null);
        if (personal != null) personal.write(buf);
        buf.writeBoolean(overall != null);
        if (overall != null) overall.write(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> org.restor.fpv.client.GhostRenderer.setGhosts(
                startTime, personalIsOverall, personal, overall)));
        ctx.get().setPacketHandled(true);
    }
}
