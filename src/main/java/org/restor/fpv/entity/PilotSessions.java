package org.restor.fpv.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.restor.fpv.Fpv;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A pilot in a projected FPV session is invisible, invulnerable and looking through the
 * aircraft's camera. Those flags are undone by the aircraft's own tick, which never runs
 * when the player logs out, dies, changes dimension or the craft unloads — so the session
 * is tracked here and force-closed on every one of those paths. The persistent flag is the
 * last resort: a world saved mid-session is repaired on the next login.
 */
@Mod.EventBusSubscriber(modid = Fpv.MODID)
public final class PilotSessions {
    private static final String FLAG = "fpv_pilot_session";
    private static final String WAS_INVULNERABLE = "fpv_was_invulnerable";
    private static final String HOME = "fpv_pilot_home";
    private static final Map<UUID, Entity> ACTIVE = new ConcurrentHashMap<>();

    private PilotSessions() {}

    public static void open(ServerPlayer player, Entity craft, PilotReturn home) {
        ACTIVE.put(player.getUUID(), craft);
        CompoundTag data = player.getPersistentData();
        data.putBoolean(FLAG, true);
        data.putBoolean(WAS_INVULNERABLE, player.isInvulnerable());
        if (home != null) {
            CompoundTag tag = new CompoundTag();
            home.save(tag, "");
            data.put(HOME, tag);
        }
    }

    /** The craft owns the return point, but it dies with the craft — this copy does not. */
    public static PilotReturn home(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(HOME) ? PilotReturn.load(data.getCompound(HOME), "", player.level()) : null;
    }

    // A player never tracks itself, so vanilla never sends them their own entity
    // data: without this their client still believes the projected body is visible.
    public static void syncFlagsToSelf(ServerPlayer player) {
        var values = player.getEntityData().getNonDefaultValues();
        if (values != null && !values.isEmpty()) {
            player.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                    player.getId(), values));
        }
    }

    public static void close(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        clearFlag(player);
    }

    public static void forget(UUID pilot) {
        if (pilot != null) ACTIVE.remove(pilot);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) abort(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) abort(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) abort(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ACTIVE.remove(player.getUUID());
            repair(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) repair(player, true);
    }

    @SubscribeEvent
    public static void onCraftLeavesLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof DroneEntity drone && drone.hasRemotePilot()) {
            drone.endRemoteControl();
        }
    }

    /** Ends whatever session the player is in and puts their body back the way it was. */
    private static void abort(ServerPlayer player) {
        Entity craft = ACTIVE.remove(player.getUUID());
        if (craft instanceof DroneEntity drone) drone.endRemoteControl();
        if (player.getVehicle() instanceof Pilotable) player.stopRiding();
        repair(player, false);
    }

    // A world saved mid-session stored the pilot's body at the aircraft, so a login repair
    // also has to put it back; a respawn already has a bed or spawn point to go to.
    private static void repair(ServerPlayer player, boolean sendHome) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(FLAG)) return;
        boolean wasInvulnerable = data.getBoolean(WAS_INVULNERABLE);
        PilotReturn home = sendHome ? home(player) : null;
        clearFlag(player);
        player.setInvisible(false);
        player.setInvulnerable(wasInvulnerable);
        if (player.getCamera() != player) player.setCamera(player);
        if (home != null) home.send(player);
    }

    private static void clearFlag(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(FLAG);
        data.remove(WAS_INVULNERABLE);
        data.remove(HOME);
    }
}
