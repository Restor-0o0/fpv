package org.restor.fpv.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class MotorSoundHandler {
    private static final int SWEEP_INTERVAL = 10;
    // SoundEngine registers a played instance on its own executor, so isActive() lies for
    // a moment after play(). Believing it too early spawns a second loop on the same craft.
    private static final int STARTUP_GRACE = 60;

    private record Loop(DroneMotorSound sound, int startedAt) {}

    private static final Map<Integer, Loop> LOOPS = new HashMap<>();
    private static int tickCount;

    private MotorSoundHandler() {}

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clearAll();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clearAll();
            return;
        }
        if (mc.isPaused()) return;
        tickCount++;

        retuneStaleLoops(mc);
        if (tickCount % SWEEP_INTERVAL != 0) return;

        LOOPS.values().removeIf(loop -> {
            var craft = loop.sound().craft();
            boolean gone = craft.isRemoved() || !craft.isAlive() || craft.level() != mc.level;
            boolean lost = loop.sound().isStopped()
                || (tickCount - loop.startedAt() > STARTUP_GRACE
                    && !mc.getSoundManager().isActive(loop.sound()));
            if (!gone && !lost) return false;
            mc.getSoundManager().stop(loop.sound());
            return true;
        });

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof DroneEntity) && !(entity instanceof PlaneEntity)
                && !(entity instanceof org.restor.fpv.entity.NpcDroneEntity)) continue;
            if (LOOPS.containsKey(entity.getId())) continue;
            start(mc, entity, 0f);
        }
    }

    /**
     * Whether the loop is pinned to the listener and which sample it uses are both fixed when
     * the instance is played, so a craft that swaps props or that the pilot climbs into needs
     * its loop rebuilt — carrying the current loudness over so the swap isn't audible.
     */
    private static void retuneStaleLoops(Minecraft mc) {
        List<Entity> stale = null;
        for (Loop loop : LOOPS.values()) {
            DroneMotorSound sound = loop.sound();
            Entity craft = sound.craft();
            if (craft.isRemoved() || craft.level() != mc.level) continue;
            boolean firstPerson = isPilotView(mc, craft);
            if (firstPerson == sound.isFirstPerson() && DroneMotorSound.tierOf(craft) == sound.tier()) continue;
            if (stale == null) stale = new ArrayList<>();
            stale.add(craft);
        }
        if (stale == null) return;
        for (Entity craft : stale) {
            Loop old = LOOPS.remove(craft.getId());
            mc.getSoundManager().stop(old.sound());
            start(mc, craft, old.sound().loudness());
        }
    }

    private static void start(Minecraft mc, Entity craft, float loudness) {
        DroneMotorSound loop = new DroneMotorSound(craft, isPilotView(mc, craft), loudness);
        LOOPS.put(craft.getId(), new Loop(loop, tickCount));
        mc.getSoundManager().play(loop);
    }

    private static boolean isPilotView(Minecraft mc, Entity craft) {
        Entity camera = mc.getCameraEntity();
        return camera != null && (camera == craft || camera.getVehicle() == craft);
    }

    private static void clearAll() {
        if (LOOPS.isEmpty()) return;
        var manager = Minecraft.getInstance().getSoundManager();
        for (Loop loop : LOOPS.values()) manager.stop(loop.sound());
        LOOPS.clear();
    }
}
