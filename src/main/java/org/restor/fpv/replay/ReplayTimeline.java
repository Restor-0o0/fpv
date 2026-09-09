package org.restor.fpv.replay;

public final class ReplayTimeline {
    private static final float[] SPEEDS = {0.5f, 1.0f, 2.0f, 4.0f};

    private final double lastFrame;
    private double previousFrame;
    private double frame;
    private int speedIndex = 1;
    private boolean paused;

    public ReplayTimeline(int frameCount) {
        if (frameCount <= 0) throw new IllegalArgumentException("frameCount must be positive");
        lastFrame = frameCount - 1.0;
    }

    public void advance() {
        if (paused) return;
        if (frame >= lastFrame) {
            paused = true;
            return;
        }
        previousFrame = frame;
        frame = Math.min(lastFrame, frame + speed());
    }

    public void togglePaused() {
        paused = !paused;
        previousFrame = frame;
    }

    public boolean paused() {
        return paused;
    }

    public float cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        return speed();
    }

    public float speed() {
        return SPEEDS[speedIndex];
    }

    public void scrub(double frames) {
        frame = clamp(frame + frames);
        previousFrame = frame;
    }

    public double frame() {
        return frame;
    }

    public double renderFrame(float partialTick) {
        if (paused) return frame;
        float partial = Math.max(0.0f, Math.min(1.0f, partialTick));
        return previousFrame + (frame - previousFrame) * partial;
    }

    public double lastFrame() {
        return lastFrame;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(lastFrame, value));
    }
}
