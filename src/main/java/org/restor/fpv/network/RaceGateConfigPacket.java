package org.restor.fpv.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.restor.fpv.block.entity.RaceGateBlockEntity;

import java.util.function.Supplier;

// Client -> server config for a race gate.
public class RaceGateConfigPacket {
    public static final int SET_SIZE = 0;
    public static final int CYCLE_COLOR = 1;
    public static final int ADJUST_YAW = 2;
    public static final int ADJUST_PITCH = 3;
    public static final int SET_OFFSET = 4;

    private final BlockPos pos;
    private final int action;
    private final int value;

    public RaceGateConfigPacket(BlockPos pos, int action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    public RaceGateConfigPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readInt();
        this.value = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(action);
        buf.writeInt(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (player.level().getBlockEntity(pos) instanceof RaceGateBlockEntity be) {
                switch (action) {
                    case SET_SIZE -> be.setSize(value);
                    case CYCLE_COLOR -> be.cycleColor();
                    case ADJUST_YAW -> be.adjustYaw(value);
                    case ADJUST_PITCH -> be.adjustPitch(value);
                    case SET_OFFSET -> be.setOffset(value);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
