package com.handspace.testutil

import com.handspace.tracking.HandFrame
import com.handspace.tracking.Handedness
import com.handspace.tracking.Landmark
import com.handspace.tracking.TrackedHand

enum class Pose { PINCH, OPEN, FIST, POINT }

/**
 * Mãos sintéticas para testar a lógica de interação. Imagem de referência de 1000x1000 px,
 * punho em (0.5, 0.8) e base do médio em (0.5, 0.5): tamanho da mão = 0.3 (300 px).
 * São fixtures de teste, não dados de sensor. O rastreamento real é validado no aparelho.
 */
object SyntheticHands {

    fun landmarks(pose: Pose, dx: Float = 0f, dy: Float = 0f): List<Landmark> {
        val extended = when (pose) {
            Pose.PINCH, Pose.POINT -> booleanArrayOf(true, false, false, false)
            Pose.OPEN -> booleanArrayOf(true, true, true, true)
            Pose.FIST -> booleanArrayOf(false, false, false, false)
        }
        val thumbGap = if (pose == Pose.PINCH) 0.01f else 0.3f

        val pts = Array(21) { Landmark(0.5f, 0.5f, 0f) }
        pts[0] = Landmark(0.5f, 0.8f, 0f)
        pts[9] = Landmark(0.5f, 0.5f, 0f)

        val xs = floatArrayOf(0.35f, 0.45f, 0.55f, 0.65f)
        val mcps = intArrayOf(5, 9, 13, 17)
        val pips = intArrayOf(6, 10, 14, 18)
        val tips = intArrayOf(8, 12, 16, 20)
        for (i in 0 until 4) {
            // O landmark 9 define o tamanho da mão: não pode ser sobrescrito.
            if (i != 1) pts[mcps[i]] = Landmark(xs[i], 0.55f, 0f)
            if (extended[i]) {
                pts[pips[i]] = Landmark(xs[i], 0.45f, 0f)
                pts[tips[i]] = Landmark(xs[i], 0.2f, 0f)
            } else {
                pts[pips[i]] = Landmark(xs[i], 0.5f, 0f)
                pts[tips[i]] = Landmark(xs[i], 0.62f, 0f)
            }
        }
        val indexTip = pts[8]
        pts[4] = Landmark(indexTip.x + thumbGap, indexTip.y, 0f)

        return pts.map { Landmark(it.x + dx, it.y + dy, it.z) }
    }

    fun trackedHand(hand: Handedness, pose: Pose, dx: Float = 0f, dy: Float = 0f): TrackedHand =
        TrackedHand(hand, 1f, landmarks(pose, dx, dy))

    fun frame(timestampMs: Long, vararg hands: TrackedHand): HandFrame = HandFrame(
        timestampMs = timestampMs,
        hands = hands.toList(),
        imageWidth = 1000,
        imageHeight = 1000,
        latencyMs = 0L,
    )
}
