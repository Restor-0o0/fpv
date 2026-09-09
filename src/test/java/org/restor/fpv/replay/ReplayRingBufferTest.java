package org.restor.fpv.replay;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayRingBufferTest {
    @Test
    void retainsOnlyNewestValuesInChronologicalOrder() {
        ReplayRingBuffer<Integer> buffer = new ReplayRingBuffer<>(3);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        buffer.add(4);
        assertEquals(3, buffer.size());
        assertEquals(List.of(2, 3, 4), buffer.snapshot());
    }

    @Test
    void snapshotsStayImmutableAndIndependent() {
        ReplayRingBuffer<Integer> buffer = new ReplayRingBuffer<>(2);
        buffer.add(1);
        buffer.add(2);
        List<Integer> snapshot = buffer.snapshot();
        buffer.add(3);
        assertEquals(List.of(1, 2), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(4));
    }

    @Test
    void clearReleasesAllValues() {
        ReplayRingBuffer<Integer> buffer = new ReplayRingBuffer<>(2);
        buffer.add(1);
        buffer.clear();
        assertEquals(0, buffer.size());
        assertEquals(List.of(), buffer.snapshot());
    }
}
