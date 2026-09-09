package org.restor.fpv.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;
import org.restor.fpv.Fpv;

@EventBusSubscriber(modid = Fpv.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModKeyBindings {
    public static final KeyMapping CYCLE_MODE = new KeyMapping(
        "key.fpv.cycle_mode",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.fpv"
    );

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
        "key.fpv.open_settings",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        "key.categories.fpv"
    );

    public static final KeyMapping CAMERA_UP = new KeyMapping(
        "key.fpv.camera_up",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_PAGE_UP,
        "key.categories.fpv"
    );

    public static final KeyMapping CAMERA_DOWN = new KeyMapping(
        "key.fpv.camera_down",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_PAGE_DOWN,
        "key.categories.fpv"
    );

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
        "key.fpv.toggle_hud",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.fpv"
    );

    public static final KeyMapping ARM_SWITCH = new KeyMapping(
        "key.fpv.arm_switch",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        "key.categories.fpv"
    );

    // Plane-only, no RC connected: swaps the mouse between flying (roll/pitch) and
    // looking around the belly camera (gimbal pan/tilt).
    public static final KeyMapping PLANE_MOUSE_MODE = new KeyMapping(
        "key.fpv.plane_mouse_mode",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.fpv"
    );

    // Recenters the belly-camera gimbal (yaw/pitch back to 0) on a plane or a heavy drone.
    public static final KeyMapping PLANE_RESET_CAM = new KeyMapping(
        "key.fpv.plane_reset_cam",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.fpv"
    );

    // Plane-only: toggle orbit autopilot — circle the locked/crosshair point.
    public static final KeyMapping PLANE_ORBIT = new KeyMapping(
        "key.fpv.plane_orbit",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        "key.categories.fpv"
    );

    // Scout plane only: call in a kamikaze wing strike onto the locked/crosshair point.
    public static final KeyMapping PLANE_CALL_STRIKE = new KeyMapping(
        "key.fpv.plane_call_strike",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.fpv"
    );

    // Heavy-lift drones only: WASD moves the airframe, so yaw moves onto its own pair of
    // keys. Both are swallowed while flying one, or Q would drop the held item and E open
    // the inventory.
    public static final KeyMapping DRONE_YAW_LEFT = new KeyMapping(
        "key.fpv.drone_yaw_left",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Q,
        "key.categories.fpv"
    );

    public static final KeyMapping DRONE_YAW_RIGHT = new KeyMapping(
        "key.fpv.drone_yaw_right",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_E,
        "key.categories.fpv"
    );

    // Drone-only: drop one loaded payload charge (FPV_IDEAS §4 FIRE_PAYLOAD).
    public static final KeyMapping FIRE_PAYLOAD = new KeyMapping(
        "key.fpv.fire_payload",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_X,
        "key.categories.fpv"
    );

    public static final KeyMapping SAVE_REPLAY = new KeyMapping(
        "key.fpv.save_replay", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.fpv");
    public static final KeyMapping PLAY_REPLAY = new KeyMapping(
        "key.fpv.play_replay", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.fpv");
    public static final KeyMapping REPLAY_PAUSE = new KeyMapping(
        "key.fpv.replay_pause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.fpv");
    public static final KeyMapping REPLAY_SPEED = new KeyMapping(
        "key.fpv.replay_speed", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.fpv");
    public static final KeyMapping REPLAY_BACK = new KeyMapping(
        "key.fpv.replay_back", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.fpv");
    public static final KeyMapping REPLAY_FORWARD = new KeyMapping(
        "key.fpv.replay_forward", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.fpv");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_MODE);
        event.register(OPEN_SETTINGS);
        event.register(CAMERA_UP);
        event.register(CAMERA_DOWN);
        event.register(TOGGLE_HUD);
        event.register(ARM_SWITCH);
        event.register(PLANE_MOUSE_MODE);
        event.register(PLANE_RESET_CAM);
        event.register(PLANE_ORBIT);
        event.register(PLANE_CALL_STRIKE);
        event.register(DRONE_YAW_LEFT);
        event.register(DRONE_YAW_RIGHT);
        event.register(FIRE_PAYLOAD);
        event.register(SAVE_REPLAY);
        event.register(PLAY_REPLAY);
        event.register(REPLAY_PAUSE);
        event.register(REPLAY_SPEED);
        event.register(REPLAY_BACK);
        event.register(REPLAY_FORWARD);
    }
}
