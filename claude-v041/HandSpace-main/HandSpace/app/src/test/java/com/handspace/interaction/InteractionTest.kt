package com.handspace.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorSmootherTest {

    @Test
    fun firstUpdate_snapsToTarget() {
        val s = CursorSmoother(0.5f)
        val p = s.update(100f, 200f)
        assertEquals(100f, p.x, 0.001f)
        assertEquals(200f, p.y, 0.001f)
    }

    @Test
    fun subsequentUpdates_moveByFactorOfRemainingDistance() {
        val s = CursorSmoother(0.5f)
        s.update(0f, 0f)
        val p = s.update(100f, 0f)
        assertEquals(50f, p.x, 0.001f)
        assertEquals(0f, p.y, 0.001f)
    }

    @Test
    fun reset_makesNextUpdateSnap() {
        val s = CursorSmoother(0.1f)
        s.update(0f, 0f)
        s.reset()
        assertEquals(500f, s.update(500f, 500f).x, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroFactor_isRejected() {
        CursorSmoother(0f)
    }
}

class ActivationCooldownTest {

    @Test
    fun blocksFiresWithinInterval() {
        val c = ActivationCooldown(400L)
        assertTrue(c.tryFire(1000L))
        assertFalse(c.tryFire(1200L))
        assertFalse(c.tryFire(1399L))
        assertTrue(c.tryFire(1400L))
    }

    @Test
    fun reset_allowsImmediateFire() {
        val c = ActivationCooldown(400L)
        c.tryFire(1000L)
        c.reset()
        assertTrue(c.tryFire(1001L))
    }
}
