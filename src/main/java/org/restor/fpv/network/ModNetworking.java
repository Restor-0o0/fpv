package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.restor.fpv.Fpv;
import org.restor.fpv.compat.CustomPacketPayload;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.compat.StreamCodec;

import java.util.function.BiConsumer;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel channel;
    private static int nextId;

    public static SimpleChannel channel() {
        return channel;
    }

    public static void register() {
        channel = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(Fpv.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

        add(ControllerPacket.class, ControllerPacket.STREAM_CODEC, ControllerPacket::handle);
        add(CycleModePacket.class, CycleModePacket.STREAM_CODEC, CycleModePacket::handle);
        add(CameraAnglePacket.class, CameraAnglePacket.STREAM_CODEC, CameraAnglePacket::handle);
        add(DroneRatesPacket.class, DroneRatesPacket.STREAM_CODEC, DroneRatesPacket::handle);
        add(SetDroneCodePacket.class, SetDroneCodePacket.STREAM_CODEC, SetDroneCodePacket::handle);
        add(StartRemoteControlPacket.class, StartRemoteControlPacket.STREAM_CODEC, StartRemoteControlPacket::handle);
        add(StationCodesPacket.class, StationCodesPacket.STREAM_CODEC, StationCodesPacket::handle);
        add(PlaneControllerPacket.class, PlaneControllerPacket.STREAM_CODEC, PlaneControllerPacket::handle);
        add(RaceTimerPacket.class, RaceTimerPacket.STREAM_CODEC, RaceTimerPacket::handle);
        add(RaceStartConfigPacket.class, RaceStartConfigPacket.STREAM_CODEC, RaceStartConfigPacket::handle);
        add(RaceGateConfigPacket.class, RaceGateConfigPacket.STREAM_CODEC, RaceGateConfigPacket::handle);
        add(RaceMarkPacket.class, RaceMarkPacket.STREAM_CODEC, RaceMarkPacket::handle);
        add(PlaneOrbitPacket.class, PlaneOrbitPacket.STREAM_CODEC, PlaneOrbitPacket::handle);
        add(PlaneStrikePacket.class, PlaneStrikePacket.STREAM_CODEC, PlaneStrikePacket::handle);
        add(ExplosionParticlesPacket.class, ExplosionParticlesPacket.STREAM_CODEC, ExplosionParticlesPacket::handle);
        add(PlaneTargetStreamPacket.class, PlaneTargetStreamPacket.STREAM_CODEC, PlaneTargetStreamPacket::handle);
        add(DevDroneGivePacket.class, DevDroneGivePacket.STREAM_CODEC, DevDroneGivePacket::handle);
        add(GhostDataPacket.class, GhostDataPacket.STREAM_CODEC, GhostDataPacket::handle);
        add(FirePayloadPacket.class, FirePayloadPacket.STREAM_CODEC, FirePayloadPacket::handle);
        add(DronePosePacket.class, DronePosePacket.STREAM_CODEC, DronePosePacket::handle);
        add(DroneDismountPacket.class, DroneDismountPacket.STREAM_CODEC, DroneDismountPacket::handle);
        add(LineOfSightPacket.class, LineOfSightPacket.STREAM_CODEC, LineOfSightPacket::handle);
    }

    private static <T extends CustomPacketPayload> void add(Class<T> type,
                                                            StreamCodec<FriendlyByteBuf, T> codec,
                                                            BiConsumer<T, IPayloadContext> handler) {
        channel.registerMessage(nextId++, type,
            (msg, buf) -> codec.encode(buf, msg),
            codec::decode,
            (msg, ctx) -> {
                NetworkEvent.Context context = ctx.get();
                handler.accept(msg, new IPayloadContext(context));
                context.setPacketHandled(true);
            });
    }
}
