package org.restor.fpv.replay;

import org.restor.fpv.race.GhostFrame;

import java.util.List;

public record ReplayRecording(int modelType, List<GhostFrame> frames) {
    public static final int MAX_FRAMES = 30 * 20;

    public ReplayRecording {
        frames = List.copyOf(frames);
        if (modelType < 0 || modelType > 3) {
            throw new IllegalArgumentException("modelType out of range: " + modelType);
        }
        if (frames.isEmpty() || frames.size() > MAX_FRAMES) {
            throw new IllegalArgumentException("frame count out of range: " + frames.size());
        }
    }

    public int frameCount() {
        return frames.size();
    }

    public GhostFrame frame(int index) {
        return frames.get(index);
    }
}
