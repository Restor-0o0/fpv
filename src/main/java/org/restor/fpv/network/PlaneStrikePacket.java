package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.KamikazeWingEntity;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.ScoutPlaneEntity;

public record PlaneStrikePacket(int scoutId, double tx, double ty, double tz) implements CustomPacketPayload {
    public static final Type<PlaneStrikePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Fpv.MODID, "plane_strike"));

    public static final StreamCodec<FriendlyByteBuf, PlaneStrikePacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeInt(pkt.scoutId);
            buf.writeDouble(pkt.tx);
            buf.writeDouble(pkt.ty);
            buf.writeDouble(pkt.tz);
        },
        buf -> new PlaneStrikePacket(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlaneStrikePacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel sl)) return;
            Entity e = sl.getEntity(pkt.scoutId());
            Vec3 point = new Vec3(pkt.tx(), pkt.ty(), pkt.tz());
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return;

            if (e instanceof org.restor.fpv.entity.CarrierPlaneEntity carrier && carrier.hasPassenger(player)) {
                if (point.distanceToSqr(carrier.position()) > 1024.0 * 1024.0) return;
                String released = carrier.releaseAt(point);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    released == null ? "msg.fpv.carrier_empty" : "msg.fpv.carrier_release_" + released), true);
                return;
            }

            if (e instanceof org.restor.fpv.assembly.ModularPlaneEntity built && built.hasPassenger(player)) {
                if (point.distanceToSqr(built.position()) > 1024.0 * 1024.0) return;
                String released = built.releaseAt(point);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    released == null ? "msg.fpv.carrier_empty" : "msg.fpv.carrier_release_" + released), true);
                return;
            }

            if (!(e instanceof ScoutPlaneEntity scout) || !scout.hasPassenger(player)) return;

            Vec3 target = point;
            if (!Double.isFinite(pkt.tx()) || !Double.isFinite(pkt.ty()) || !Double.isFinite(pkt.tz())
                || target.distanceToSqr(scout.position()) > 1024.0 * 1024.0 || !scout.tryStartStrike()) return;
            Vec3 toTarget = target.subtract(scout.position());
            Vec3 approach = new Vec3(toTarget.x, 0, toTarget.z);
            if (approach.lengthSqr() < 1.0e-4) {
                Vec3 look = scout.getLookAngle();
                approach = new Vec3(look.x, 0, look.z);
            }
            if (approach.lengthSqr() < 1.0e-4) approach = new Vec3(0, 0, 1);
            approach = approach.normalize();

            double horizontal = 200.0;
            Vec3 spawn = target.subtract(approach.scale(horizontal)).add(0, horizontal, 0);
            KamikazeWingEntity wing = ModEntities.PLANE_KAMIKAZE.get().create(sl);
            if (wing == null) return;
            wing.beginAttackRun(target, spawn);
            sl.addFreshEntity(wing);
        });
    }
}
