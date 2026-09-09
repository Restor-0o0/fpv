package org.restor.fpv.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayTimelineTest {
    @Test
    void advancesPausesAndClampsAtTheEnd() {
        ReplayTimeline timeline = new ReplayTimeline(3);
        timeline.advance();
        assertEquals(1.0, timeline.frame());
        assertFalse(timeline.paused());
        timeline.advance();
        assertEquals(2.0, timeline.frame());
        assertFalse(timeline.paused());
        timeline.advance();
        assertEquals(2.0, timeline.frame());
        assertTrue(timeline.paused());
    }

    @Test
    void cyclesSpeedAndUsesItForRenderInterpolation() {
        ReplayTimeline timeline = new ReplayTimeline(20);
        assertEquals(1.0f, timeline.speed());
        assertEquals(2.0f, timeline.cycleSpeed());
        timeline.advance();
        assertEquals(2.0, timeline.frame());
        assertEquals(1.0, timeline.renderFrame(0.5f));
    }

    @Test
    void scrubAndPauseRemainWithinRecording() {
        ReplayTimeline timeline = new ReplayTimeline(201);
        timeline.scrub(100);
        assertEquals(100.0, timeline.frame());
        timeline.togglePaused();
        timeline.advance();
        assertEquals(100.0, timeline.frame());
        timeline.scrub(-500);
        assertEquals(0.0, timeline.frame());
        timeline.scrub(500);
        assertEquals(200.0, timeline.frame());
    }
}
