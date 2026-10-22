package org.restor.fpv.controller;

import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLJoystick;
import org.lwjgl.sdl.SDLStdinc;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public final class Joysticks {
    private static final Map<Integer, Long> OPEN = new HashMap<>();
    private static boolean initFailed;

    private Joysticks() {}

    private static boolean init() {
        if (initFailed) return false;
        if (SDLInit.SDL_WasInit(SDLInit.SDL_INIT_JOYSTICK) != 0) return true;
        if (!SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_JOYSTICK)) initFailed = true;
        return !initFailed;
    }

    public static int[] ids() {
        if (!init()) return new int[0];
        SDLJoystick.SDL_UpdateJoysticks();
        IntBuffer buf = SDLJoystick.SDL_GetJoysticks();
        if (buf == null) return new int[0];
        int[] ids = new int[buf.remaining()];
        buf.get(ids);
        SDLStdinc.SDL_free(buf);
        OPEN.entrySet().removeIf(e -> {
            for (int id : ids) if (id == e.getKey()) return false;
            SDLJoystick.SDL_CloseJoystick(e.getValue());
            return true;
        });
        return ids;
    }

    public static String name(int id) {
        return init() ? SDLJoystick.SDL_GetJoystickNameForID(id) : null;
    }

    private static long handle(int id) {
        if (!init()) return 0L;
        Long h = OPEN.get(id);
        if (h != null && SDLJoystick.SDL_JoystickConnected(h)) return h;
        if (h != null) {
            SDLJoystick.SDL_CloseJoystick(h);
            OPEN.remove(id);
        }
        long opened = SDLJoystick.SDL_OpenJoystick(id);
        if (opened != 0L) OPEN.put(id, opened);
        return opened;
    }

    public static float[] axes(int id) {
        long h = handle(id);
        if (h == 0L) return null;
        SDLJoystick.SDL_UpdateJoysticks();
        int n = SDLJoystick.SDL_GetNumJoystickAxes(h);
        if (n < 0) return null;
        float[] values = new float[n];
        for (int i = 0; i < n; i++) {
            values[i] = Math.max(-1f, SDLJoystick.SDL_GetJoystickAxis(h, i) / 32767f);
        }
        return values;
    }

    // Each hat is exposed as four extra buttons: up, right, down, left.
    public static boolean[] buttons(int id) {
        long h = handle(id);
        if (h == 0L) return null;
        int buttons = Math.max(0, SDLJoystick.SDL_GetNumJoystickButtons(h));
        int hats = Math.max(0, SDLJoystick.SDL_GetNumJoystickHats(h));
        boolean[] state = new boolean[buttons + hats * 4];
        for (int i = 0; i < buttons; i++) state[i] = SDLJoystick.SDL_GetJoystickButton(h, i);
        for (int i = 0; i < hats; i++) {
            int hat = SDLJoystick.SDL_GetJoystickHat(h, i);
            int base = buttons + i * 4;
            state[base] = (hat & SDLJoystick.SDL_HAT_UP) != 0;
            state[base + 1] = (hat & SDLJoystick.SDL_HAT_RIGHT) != 0;
            state[base + 2] = (hat & SDLJoystick.SDL_HAT_DOWN) != 0;
            state[base + 3] = (hat & SDLJoystick.SDL_HAT_LEFT) != 0;
        }
        return state;
    }
}
