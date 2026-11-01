package com.handspace.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandPresenceTrackerTest {

    private fun frameWithHands(count: Int) = HandFrame(
        timestampMs = 0L,
        hands = List(count) { TrackedHand(Handedness.UNKNOWN, 1f, emptyList()) },
        imageWidth = 640,
        imageHeight = 480,
        latencyMs = 0L,
    )

    @Test
    fun emitsOnlyOnTransitions() {
        val tracker = HandPresenceTracker()
        assertNull(tracker.update(frameWithHands(0)))
        assertEquals(PresenceEvent.ENTERED, tracker.update(frameWithHands(1)))
        assertNull(tracker.update(frameWithHands(1)))
        assertNull(tracker.update(frameWithHands(2)))
        assertEquals(PresenceEvent.EXITED, tracker.update(frameWithHands(0)))
    }
}
