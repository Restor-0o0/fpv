package org.restor.fpv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.restor.fpv.Fpv;
import org.restor.fpv.compat.CustomPacketPayload;
import org.restor.fpv.compat.IPayloadContext;
import org.restor.fpv.compat.StreamCodec;
import org.restor.fpv.rules.ExplosiveParam;
import org.restor.fpv.rules.ServerRule;
import org.restor.fpv.rules.ServerRules;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public record SetServerRulesPacket(Map<String, Double> changes) implements CustomPacketPayload {
    public static final Type<SetServerRulesPacket> TYPE =
        new Type<>(new ResourceLocation(Fpv.MODID, "set_server_rules"));

    public static final StreamCodec<FriendlyByteBuf, SetServerRulesPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {
            buf.writeVarInt(pkt.changes.size());
            pkt.changes.forEach((id, value) -> {
                buf.writeUtf(id, 64);
                buf.writeDouble(value);
            });
        },
        buf -> {
            int count = Math.min(buf.readVarInt(), 256);
            Map<String, Double> changes = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) changes.put(buf.readUtf(64), buf.readDouble());
            return new SetServerRulesPacket(changes);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetServerRulesPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !ServerRules.canEdit(player)) return;
            Map<ServerRule, Double> changes = new EnumMap<>(ServerRule.class);
            Map<ExplosiveParam, Double> explosives = new LinkedHashMap<>();
            pkt.changes().forEach((id, value) -> {
                if (!Double.isFinite(value)) return;
                ServerRule rule = ServerRule.byId(id);
                if (rule != null) {
                    changes.put(rule, value);
                    return;
                }
                ExplosiveParam param = ExplosiveParam.byId(id);
                if (param != null) explosives.put(param, value);
            });
            ServerRules.apply(changes, explosives);
        });
    }
}
