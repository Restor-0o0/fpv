package org.restor.fpv.controller;

import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.stream.IntStream;

public final class Joysticks {
    private Joysticks() {}

    public static int[] ids() {
        return IntStream.rangeClosed(GLFW.GLFW_JOYSTICK_1, GLFW.GLFW_JOYSTICK_LAST)
            .filter(GLFW::glfwJoystickPresent)
            .toArray();
    }

    public static String name(int id) {
        return GLFW.glfwGetJoystickName(id);
    }

    public static float[] axes(int id) {
        FloatBuffer buf = GLFW.glfwGetJoystickAxes(id);
        if (buf == null) return null;
        float[] values = new float[buf.remaining()];
        buf.get(values);
        return values;
    }

    public static boolean[] buttons(int id) {
        ByteBuffer buf = GLFW.glfwGetJoystickButtons(id);
        if (buf == null) return null;
        boolean[] state = new boolean[buf.remaining()];
        for (int i = 0; i < state.length; i++) state[i] = buf.get(i) == GLFW.GLFW_PRESS;
        return state;
    }
}
