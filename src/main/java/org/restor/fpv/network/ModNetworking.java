package org.restor.fpv.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(ControllerPacket.TYPE, ControllerPacket.STREAM_CODEC, ControllerPacket::handle);
        registrar.playToServer(CycleModePacket.TYPE, CycleModePacket.STREAM_CODEC, CycleModePacket::handle);
        registrar.playToServer(CameraAnglePacket.TYPE, CameraAnglePacket.STREAM_CODEC, CameraAnglePacket::handle);
        registrar.playToServer(DroneRatesPacket.TYPE, DroneRatesPacket.STREAM_CODEC, DroneRatesPacket::handle);
        registrar.playToServer(SetDroneCodePacket.TYPE, SetDroneCodePacket.STREAM_CODEC, SetDroneCodePacket::handle);
        registrar.playToServer(StartRemoteControlPacket.TYPE, StartRemoteControlPacket.STREAM_CODEC, StartRemoteControlPacket::handle);
        registrar.playToServer(StationCodesPacket.TYPE, StationCodesPacket.STREAM_CODEC, StationCodesPacket::handle);
        registrar.playToServer(PlaneControllerPacket.TYPE, PlaneControllerPacket.STREAM_CODEC, PlaneControllerPacket::handle);
        registrar.playToClient(RaceTimerPacket.TYPE, RaceTimerPacket.STREAM_CODEC, RaceTimerPacket::handle);
        registrar.playToServer(RaceStartConfigPacket.TYPE, RaceStartConfigPacket.STREAM_CODEC, RaceStartConfigPacket::handle);
        registrar.playToServer(RaceGateConfigPacket.TYPE, RaceGateConfigPacket.STREAM_CODEC, RaceGateConfigPacket::handle);
        registrar.playToServer(RaceMarkPacket.TYPE, RaceMarkPacket.STREAM_CODEC, RaceMarkPacket::handle);
        registrar.playToServer(PlaneOrbitPacket.TYPE, PlaneOrbitPacket.STREAM_CODEC, PlaneOrbitPacket::handle);
        registrar.playToServer(PlaneStrikePacket.TYPE, PlaneStrikePacket.STREAM_CODEC, PlaneStrikePacket::handle);
        registrar.playToClient(ExplosionParticlesPacket.TYPE, ExplosionParticlesPacket.STREAM_CODEC, ExplosionParticlesPacket::handle);
        registrar.playToServer(PlaneTargetStreamPacket.TYPE, PlaneTargetStreamPacket.STREAM_CODEC, PlaneTargetStreamPacket::handle);
        registrar.playToClient(GhostDataPacket.TYPE, GhostDataPacket.STREAM_CODEC, GhostDataPacket::handle);
        registrar.playToServer(FirePayloadPacket.TYPE, FirePayloadPacket.STREAM_CODEC, FirePayloadPacket::handle);
        registrar.playToServer(FireWeaponPacket.TYPE, FireWeaponPacket.STREAM_CODEC, FireWeaponPacket::handle);
        registrar.playToServer(DronePosePacket.TYPE, DronePosePacket.STREAM_CODEC, DronePosePacket::handle);
        registrar.playToServer(DroneDismountPacket.TYPE, DroneDismountPacket.STREAM_CODEC, DroneDismountPacket::handle);
        registrar.playToClient(LineOfSightPacket.TYPE, LineOfSightPacket.STREAM_CODEC, LineOfSightPacket::handle);
    }
}
