package com.handspace.spatial

import com.handspace.testutil.Pose
import com.handspace.testutil.SyntheticHands
import com.handspace.tracking.Handedness
import com.handspace.tracking.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandSkeleton3DTest {

    private val transform = ViewTransform.fillCenter(1000, 1000, 1000f, 1000f)
    private val camera = Camera3D(1000f, 1000f)
    private val skeleton = HandSkeleton3D()

    @Test
    fun producesTwentyOneJoints() {
        val hand = SyntheticHands.trackedHand(Handedness.RIGHT, Pose.OPEN)
        assertEquals(21, skeleton.joints(hand, transform, camera).size)
    }

    @Test
    fun wristSitsAtNominalDepth() {
        val hand = SyntheticHands.trackedHand(Handedness.RIGHT, Pose.OPEN)
        val wrist = skeleton.joints(hand, transform, camera)[0]
        assertEquals(HandSkeleton3D.NOMINAL_DEPTH_M, wrist.z, 0.0001f)
    }

    @Test
    fun landmarkCloserToCamera_hasSmallerDepth() {
        val hand = SyntheticHands.trackedHand(Handedness.RIGHT, Pose.OPEN)
        val pushed = hand.copy(
            landmarks = hand.landmarks.mapIndexed { i, lm ->
                if (i == 8) lm.copy(z = -0.2f) else lm
            }
        )
        val base = skeleton.joints(hand, transform, camera)[8].z
        val closer = skeleton.joints(pushed, transform, camera)[8].z
        assertTrue("ponta mais próxima deve ter profundidade menor", closer < base)
    }
}
