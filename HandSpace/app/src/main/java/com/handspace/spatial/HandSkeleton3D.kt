package com.handspace.spatial

import com.handspace.tracking.TrackedHand
import com.handspace.tracking.ViewTransform

/**
 * Converte os 21 landmarks de uma mão em pontos 3D.
 *
 * A posição lateral e vertical vem da projeção do próprio landmark na view (mesma transformação
 * do overlay). A profundidade é estimada a partir do z relativo do MediaPipe, em torno de uma
 * profundidade nominal. É uma aproximação: o z do MediaPipe não é métrico.
 */
class HandSkeleton3D(private val nominalDepthM: Float = NOMINAL_DEPTH_M) {

    fun joints(hand: TrackedHand, transform: ViewTransform, camera: Camera3D): List<Vec3> {
        val lms = hand.landmarks
        val wristZ = lms[0].z
        return List(lms.size) { i ->
            val p = transform.map(lms[i])
            val depth = (nominalDepthM + (lms[i].z - wristZ) * DEPTH_GAIN_M)
                .coerceIn(MIN_DEPTH_M, MAX_DEPTH_M)
            camera.unprojectAtDepth(p.x, p.y, depth)
        }
    }

    companion object {
        const val NOMINAL_DEPTH_M = 0.6f
        const val DEPTH_GAIN_M = 0.5f
        const val MIN_DEPTH_M = 0.15f
        const val MAX_DEPTH_M = 3f
    }
}
