package org.restor.fpv.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReplayRingBuffer<T> {
    private final Object[] values;
    private int next;
    private int size;

    public ReplayRingBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        values = new Object[capacity];
    }

    public void add(T value) {
        values[next] = Objects.requireNonNull(value, "value");
        next = (next + 1) % values.length;
        if (size < values.length) size++;
    }

    public int size() {
        return size;
    }

    public void clear() {
        java.util.Arrays.fill(values, null);
        next = 0;
        size = 0;
    }

    public List<T> snapshot() {
        List<T> copy = new ArrayList<>(size);
        int first = Math.floorMod(next - size, values.length);
        for (int i = 0; i < size; i++) {
            @SuppressWarnings("unchecked")
            T value = (T) values[(first + i) % values.length];
            copy.add(value);
        }
        return List.copyOf(copy);
    }
}
