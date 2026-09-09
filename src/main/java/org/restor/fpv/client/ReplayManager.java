package org.restor.fpv.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.restor.fpv.Fpv;
import org.restor.fpv.entity.CinewhoopDroneEntity;
import org.restor.fpv.entity.DroneEntity;
import org.restor.fpv.entity.PlaneEntity;
import org.restor.fpv.entity.TinyWhoopDroneEntity;
import org.restor.fpv.race.GhostFrame;
import org.restor.fpv.replay.ReplayRecording;
import org.restor.fpv.replay.ReplayRingBuffer;
import org.restor.fpv.replay.ReplayTimeline;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT)
public final class ReplayManager {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ReplayRingBuffer<GhostFrame> RECENT =
        new ReplayRingBuffer<>(ReplayRecording.MAX_FRAMES);

    private static Level observedLevel;
    private static Entity recordedVehicle;
    private static int recordedModelType;
    private static int missingVehicleTicks;
    private static ReplayRecording savedReplay;
    private static ReplayRecording playback;
    private static ReplayTimeline timeline;
    private static Entity previousCameraEntity;
    private static CameraType previousCameraType;
    private static ArmorStand replayCamera;

    private ReplayManager() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (MC.level != observedLevel) {
            clearWorldState();
            observedLevel = MC.level;
        }
        if (MC.level == null || MC.player == null) return;

        Entity vehicle = currentVehicle();
        updateRecording(vehicle);

        boolean started = false;
        if (MC.screen == null) {
            if (ModKeyBindings.SAVE_REPLAY.consumeClick()) saveReplay(false);
            started = handlePlaybackKeys(vehicle);
        }
        if (timeline != null && !started) timeline.advance();
        if (timeline != null) updateReplayCamera(started);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        clearWorldState();
        observedLevel = null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (playback == null || timeline == null) return;
        renderHud(event.getGuiGraphics(), event.getPartialTick());
    }

    static ReplayRecording playback() {
        return playback;
    }

    static double renderFrame(float partialTick) {
        return timeline == null ? 0.0 : timeline.renderFrame(partialTick);
    }

    private static Entity currentVehicle() {
        if (MC.player.getVehicle() instanceof DroneEntity drone && !drone.isRemoved()) return drone;
        if (MC.player.getVehicle() instanceof PlaneEntity plane && !plane.isRemoved()) return plane;
        if (MC.getCameraEntity() instanceof DroneEntity drone && !drone.isRemoved()) return drone;
        return null;
    }

    private static void updateRecording(Entity vehicle) {
        if (vehicle == recordedVehicle) {
            missingVehicleTicks = 0;
            if (vehicle != null) RECENT.add(captureFrame(vehicle));
            return;
        }

        if (recordedVehicle != null && vehicle == null && !recordedVehicle.isRemoved()
            && missingVehicleTicks++ < 5) {
            return;
        }

        if (recordedVehicle != null && recordedVehicle.isRemoved() && RECENT.size() > 0) {
            saveReplay(true);
        }
        if (recordedVehicle != null || vehicle != null) {
            RECENT.clear();
            recordedVehicle = vehicle;
            missingVehicleTicks = 0;
            if (vehicle != null) recordedModelType = modelType(vehicle);
        }
        if (vehicle != null) RECENT.add(captureFrame(vehicle));
    }

    private static GhostFrame captureFrame(Entity vehicle) {
        float roll;
        float throttle;
        if (vehicle instanceof DroneEntity drone) {
            roll = drone.getRoll();
            throttle = drone.getVisualThrottle();
        } else {
            PlaneEntity plane = (PlaneEntity) vehicle;
            roll = plane.getRoll();
            throttle = plane.getVisualThrottle();
        }
        return new GhostFrame(vehicle.getX(), vehicle.getY(), vehicle.getZ(),
            vehicle.getYRot(), vehicle.getXRot(), roll, throttle);
    }

    private static int modelType(Entity vehicle) {
        if (vehicle instanceof TinyWhoopDroneEntity) return 1;
        if (vehicle instanceof CinewhoopDroneEntity) return 2;
        if (vehicle instanceof PlaneEntity) return 3;
        return 0;
    }

    private static void saveReplay(boolean automatic) {
        if (RECENT.size() == 0) {
            if (!automatic) showMessage("msg.fpv.replay_nothing_to_save");
            return;
        }
        savedReplay = new ReplayRecording(recordedModelType, RECENT.snapshot());
        stopPlayback();
        showMessage(automatic ? "msg.fpv.replay_saved_crash" : "msg.fpv.replay_saved",
            formatSeconds(savedReplay.frameCount() - 1));
    }

    private static boolean handlePlaybackKeys(Entity vehicle) {
        if (ModKeyBindings.PLAY_REPLAY.consumeClick()) {
            if (playback != null) {
                stopPlayback();
                showMessage("msg.fpv.replay_stopped");
                return false;
            }
            if (vehicle != null) {
                showMessage("msg.fpv.replay_exit_vehicle");
                return false;
            }
            if (savedReplay == null) {
                showMessage("msg.fpv.replay_unavailable");
                return false;
            }
            playback = savedReplay;
            timeline = new ReplayTimeline(playback.frameCount());
            startReplayCamera();
            showMessage("msg.fpv.replay_started");
            return true;
        }
        if (timeline == null) return false;

        if (ModKeyBindings.REPLAY_PAUSE.consumeClick()) timeline.togglePaused();
        if (ModKeyBindings.REPLAY_SPEED.consumeClick()) timeline.cycleSpeed();
        boolean scrubbed = false;
        if (ModKeyBindings.REPLAY_BACK.consumeClick()) {
            timeline.scrub(-5 * 20);
            scrubbed = true;
        }
        if (ModKeyBindings.REPLAY_FORWARD.consumeClick()) {
            timeline.scrub(5 * 20);
            scrubbed = true;
        }
        if (scrubbed) updateReplayCamera(true);
        return false;
    }

    private static void stopPlayback() {
        restoreCamera();
        playback = null;
        timeline = null;
    }

    private static void startReplayCamera() {
        previousCameraEntity = MC.getCameraEntity();
        previousCameraType = MC.options.getCameraType();
        replayCamera = new ArmorStand(MC.level, 0.0, 0.0, 0.0);
        replayCamera.setInvisible(true);
        replayCamera.setNoGravity(true);
        MC.options.setCameraType(CameraType.FIRST_PERSON);
        MC.setCameraEntity(replayCamera);
    }

    private static void updateReplayCamera(boolean initialize) {
        if (replayCamera == null || playback == null || timeline == null) return;

        double frame = timeline.frame();
        int i0 = (int) Math.floor(frame);
        int i1 = Math.min(i0 + 1, playback.frameCount() - 1);
        float t = (float) (frame - i0);
        GhostFrame f0 = playback.frame(i0);
        GhostFrame f1 = playback.frame(i1);
        double x = Mth.lerp(t, f0.x(), f1.x());
        double y = Mth.lerp(t, f0.y(), f1.y());
        double z = Mth.lerp(t, f0.z(), f1.z());
        float yaw = Mth.rotLerp(t, f0.yaw(), f1.yaw());
        float pitch = Mth.rotLerp(t, f0.pitch(), f1.pitch());

        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians),
            -Math.sin(pitchRadians), Math.cos(yawRadians) * Math.cos(pitchRadians));
        double distance = playback.modelType() == 3 ? 8.0 : 4.5;
        double height = playback.modelType() == 3 ? 2.5 : 1.6;
        Vec3 target = new Vec3(x, y + (playback.modelType() == 3 ? 0.5 : 0.25), z);
        Vec3 camera = target.subtract(forward.scale(distance)).add(0.0, height, 0.0);
        Vec3 look = target.subtract(camera);
        float cameraYaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0f;
        float cameraPitch = (float) -Math.toDegrees(Math.atan2(look.y,
            Math.sqrt(look.x * look.x + look.z * look.z)));

        if (!initialize) {
            replayCamera.xo = replayCamera.getX();
            replayCamera.yo = replayCamera.getY();
            replayCamera.zo = replayCamera.getZ();
            replayCamera.yRotO = replayCamera.getYRot();
            replayCamera.xRotO = replayCamera.getXRot();
        }
        replayCamera.setPos(camera.x, camera.y - replayCamera.getEyeHeight(), camera.z);
        replayCamera.setYRot(cameraYaw);
        replayCamera.setXRot(cameraPitch);
        if (initialize) {
            replayCamera.xo = replayCamera.getX();
            replayCamera.yo = replayCamera.getY();
            replayCamera.zo = replayCamera.getZ();
            replayCamera.yRotO = cameraYaw;
            replayCamera.xRotO = cameraPitch;
        }
        if (MC.options.getCameraType() != CameraType.FIRST_PERSON) {
            MC.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static void restoreCamera() {
        if (replayCamera == null) return;
        if (MC.getCameraEntity() == replayCamera) {
            Entity restore = previousCameraEntity != null && !previousCameraEntity.isRemoved()
                ? previousCameraEntity : MC.player;
            if (restore != null) MC.setCameraEntity(restore);
        }
        if (previousCameraType != null) MC.options.setCameraType(previousCameraType);
        replayCamera = null;
        previousCameraEntity = null;
        previousCameraType = null;
    }

    private static void clearWorldState() {
        RECENT.clear();
        recordedVehicle = null;
        recordedModelType = 0;
        missingVehicleTicks = 0;
        savedReplay = null;
        stopPlayback();
    }

    private static void renderHud(GuiGraphics graphics, float partialTick) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int barWidth = Math.min(420, Math.max(160, width - 80));
        int x = (width - barWidth) / 2;
        int y = height - 38;
        double frame = timeline.renderFrame(partialTick);
        double progress = timeline.lastFrame() == 0.0 ? 1.0 : frame / timeline.lastFrame();

        graphics.fill(x - 8, y - 15, x + barWidth + 8, y + 31, 0xB0000000);
        Component state = Component.translatable(timeline.paused()
            ? "hud.fpv.replay.paused" : "hud.fpv.replay.playing");
        Component line = Component.translatable("hud.fpv.replay.line", state,
            formatSeconds(frame), formatSeconds(timeline.lastFrame()), formatSpeed(timeline.speed()));
        graphics.drawCenteredString(MC.font, line, width / 2, y - 11, 0xFFE8F6FF);

        graphics.fill(x, y, x + barWidth, y + 5, 0xFF25323A);
        graphics.fill(x, y, x + (int) Math.round(barWidth * progress), y + 5, 0xFF44D7FF);
        int marker = x + (int) Math.round((barWidth - 1) * progress);
        graphics.fill(marker, y - 2, marker + 2, y + 8, 0xFFFFFFFF);

        Component controls = Component.translatable("hud.fpv.replay.controls",
            ModKeyBindings.REPLAY_PAUSE.getTranslatedKeyMessage(),
            ModKeyBindings.REPLAY_SPEED.getTranslatedKeyMessage(),
            ModKeyBindings.PLAY_REPLAY.getTranslatedKeyMessage());
        graphics.drawCenteredString(MC.font, controls, width / 2, y + 12, 0xFF9EB2BD);
        Component scrub = Component.translatable("hud.fpv.replay.scrub",
            ModKeyBindings.REPLAY_BACK.getTranslatedKeyMessage(),
            ModKeyBindings.REPLAY_FORWARD.getTranslatedKeyMessage());
        graphics.drawCenteredString(MC.font, scrub, width / 2, y + 22, 0xFF9EB2BD);
    }

    private static String formatSeconds(double frames) {
        double seconds = Math.max(0.0, frames) / 20.0;
        int minutes = (int) (seconds / 60.0);
        return String.format(Locale.ROOT, "%d:%04.1f", minutes, seconds - minutes * 60.0);
    }

    private static String formatSpeed(float speed) {
        return speed == (int) speed
            ? Integer.toString((int) speed)
            : String.format(Locale.ROOT, "%.1f", speed);
    }

    private static void showMessage(String key, Object... args) {
        if (MC.player != null) MC.player.displayClientMessage(Component.translatable(key, args), true);
    }
}
