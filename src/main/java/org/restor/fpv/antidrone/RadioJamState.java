package org.restor.fpv.antidrone;

import java.util.HashMap;
import java.util.Map;

/** Jamming bookkeeping for craft that carry no RSSI model of their own. */
public final class RadioJamState {
    private static final int MAX_DURATION_TICKS = 400;

    private record Source(float strength, long expiresAt) {}

    private final Map<Long, Source> sources = new HashMap<>();
    private float strength;

    public void apply(long sourceId, float value, int durationTicks, long now) {
        if (!Float.isFinite(value) || value <= 0.0f || durationTicks <= 0) return;
        sources.put(sourceId, new Source(Math.min(1.0f, value),
            now + Math.min(durationTicks, MAX_DURATION_TICKS)));
        refresh(now);
    }

    public float refresh(long now) {
        sources.values().removeIf(source -> source.expiresAt() <= now);
        float max = 0.0f;
        for (Source source : sources.values()) max = Math.max(max, source.strength());
        strength = max;
        return max;
    }

    public float strength() {
        return strength;
    }
}
