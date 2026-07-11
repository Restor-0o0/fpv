package org.restor.fpv.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.restor.fpv.Fpv;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Fpv.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );
    private static int id;

    public static void register() {
        CHANNEL.registerMessage(id++, ControllerPacket.class,
            ControllerPacket::encode, ControllerPacket::new, ControllerPacket::handle);
        CHANNEL.registerMessage(id++, CycleModePacket.class,
            CycleModePacket::encode, CycleModePacket::new, CycleModePacket::handle);
        CHANNEL.registerMessage(id++, CameraAnglePacket.class,
            CameraAnglePacket::encode, CameraAnglePacket::new, CameraAnglePacket::handle);
        CHANNEL.registerMessage(id++, DroneRatesPacket.class,
            DroneRatesPacket::encode, DroneRatesPacket::new, DroneRatesPacket::handle);
        CHANNEL.registerMessage(id++, SetDroneCodePacket.class,
            SetDroneCodePacket::encode, SetDroneCodePacket::new, SetDroneCodePacket::handle);
        CHANNEL.registerMessage(id++, StartRemoteControlPacket.class,
            StartRemoteControlPacket::encode, StartRemoteControlPacket::new, StartRemoteControlPacket::handle);
        CHANNEL.registerMessage(id++, StationCodesPacket.class,
            StationCodesPacket::encode, StationCodesPacket::new, StationCodesPacket::handle);
        CHANNEL.registerMessage(id++, PlaneControllerPacket.class,
            PlaneControllerPacket::encode, PlaneControllerPacket::new, PlaneControllerPacket::handle);
        CHANNEL.registerMessage(id++, RaceTimerPacket.class,
            RaceTimerPacket::encode, RaceTimerPacket::new, RaceTimerPacket::handle);
        CHANNEL.registerMessage(id++, RaceStartConfigPacket.class,
            RaceStartConfigPacket::encode, RaceStartConfigPacket::new, RaceStartConfigPacket::handle);
        CHANNEL.registerMessage(id++, RaceGateConfigPacket.class,
            RaceGateConfigPacket::encode, RaceGateConfigPacket::new, RaceGateConfigPacket::handle);
        CHANNEL.registerMessage(id++, RaceMarkPacket.class,
            RaceMarkPacket::encode, RaceMarkPacket::new, RaceMarkPacket::handle);
        CHANNEL.registerMessage(id++, PlaneOrbitPacket.class,
            PlaneOrbitPacket::encode, PlaneOrbitPacket::new, PlaneOrbitPacket::handle);
        CHANNEL.registerMessage(id++, PlaneStrikePacket.class,
            PlaneStrikePacket::encode, PlaneStrikePacket::new, PlaneStrikePacket::handle);
        CHANNEL.registerMessage(id++, ExplosionParticlesPacket.class,
            ExplosionParticlesPacket::encode, ExplosionParticlesPacket::new, ExplosionParticlesPacket::handle);
        CHANNEL.registerMessage(id++, PlaneTargetStreamPacket.class,
            PlaneTargetStreamPacket::encode, PlaneTargetStreamPacket::new, PlaneTargetStreamPacket::handle);
        CHANNEL.registerMessage(id++, DevDroneGivePacket.class,
            DevDroneGivePacket::encode, DevDroneGivePacket::new, DevDroneGivePacket::handle);
        CHANNEL.registerMessage(id++, GhostDataPacket.class,
            GhostDataPacket::encode, GhostDataPacket::new, GhostDataPacket::handle);
        CHANNEL.registerMessage(id++, FirePayloadPacket.class,
            FirePayloadPacket::encode, FirePayloadPacket::new, FirePayloadPacket::handle);
    }
}
