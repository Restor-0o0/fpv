package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.entity.KamikazeWingEntity;
import org.restor.fpv.entity.ModEntities;
import org.restor.fpv.entity.ScoutPlaneEntity;

import java.util.function.Supplier;

public class PlaneStrikePacket {
    private final int scoutId;
    private final double tx, ty, tz;

    public PlaneStrikePacket(int scoutId, double tx, double ty, double tz) {
        this.scoutId = scoutId;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
    }

    public PlaneStrikePacket(FriendlyByteBuf buf) {
        scoutId = buf.readInt();
        tx = buf.readDouble();
        ty = buf.readDouble();
        tz = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(scoutId);
        buf.writeDouble(tx);
        buf.writeDouble(ty);
        buf.writeDouble(tz);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.level() instanceof ServerLevel sl)) return;
            Entity e = sl.getEntity(scoutId);
            if (!(e instanceof ScoutPlaneEntity scout) || !scout.hasPassenger(player)) return;

            Vec3 target = new Vec3(tx, ty, tz);
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
        ctx.get().setPacketHandled(true);
    }
}
