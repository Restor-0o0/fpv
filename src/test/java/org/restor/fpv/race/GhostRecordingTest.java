package org.restor.fpv.race;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostRecordingTest {
    @Test
    void boundsNbtFramesByDeclaredAvailableAndMaximum() {
        assertEquals(3, GhostRecording.boundedFrameCount(10, 3, 8));
        assertEquals(GhostRecording.MAX_FRAMES,
            GhostRecording.boundedFrameCount(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, GhostRecording.boundedFrameCount(-1, 10, 10));
    }

    @Test
    void rejectsInvalidNetworkFrameCounts() {
        assertEquals(GhostRecording.MAX_FRAMES,
            GhostRecording.requireNetworkFrameCount(GhostRecording.MAX_FRAMES));
        assertThrows(IllegalArgumentException.class, () -> GhostRecording.requireNetworkFrameCount(-1));
        assertThrows(IllegalArgumentException.class,
            () -> GhostRecording.requireNetworkFrameCount(GhostRecording.MAX_FRAMES + 1));
    }

    @Test
    void comparesRecordingsByStableContent() {
        UUID playerId = UUID.randomUUID();
        List<GhostFrame> frames = List.of(new GhostFrame(1, 2, 3, 4, 5, 6, 7));
        GhostRecording first = GhostRecording.of("pilot", playerId, 20, 0, frames);
        GhostRecording reloadedEquivalent = GhostRecording.of("pilot", playerId, 20, 0, frames);
        GhostRecording different = GhostRecording.of("pilot", playerId, 21, 0, frames);
        assertTrue(first.sameRecording(reloadedEquivalent));
        assertFalse(first.sameRecording(different));
    }
}
