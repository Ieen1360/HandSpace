package com.handspace.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandLandmarkIndexTest {

    @Test
    fun connectionsReferenceOnlyValidLandmarks() {
        for ((a, b) in HandLandmarkIndex.CONNECTIONS) {
            assertTrue("índice fora do intervalo: $a", a in 0 until HandLandmarkIndex.LANDMARK_COUNT)
            assertTrue("índice fora do intervalo: $b", b in 0 until HandLandmarkIndex.LANDMARK_COUNT)
        }
    }

    @Test
    fun fingertipsAreFiveDistinctLandmarks() {
        assertEquals(5, HandLandmarkIndex.FINGERTIPS.toSet().size)
    }
}
