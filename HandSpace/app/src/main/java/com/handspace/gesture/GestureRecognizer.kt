package com.handspace.gesture

import com.handspace.tracking.HandLandmarkIndex
import com.handspace.tracking.Landmark

enum class HandGesture { NONE, PINCH, OPEN_HAND, FIST, POINTING }

/**
 * Reconhece gestos a partir dos 21 landmarks de uma mão, com:
 *
 * - Histerese na pinça: entra abaixo de [pinchEnterRatio] e só sai acima de [pinchExitRatio],
 *   para que pequenas oscilações não liguem e desliguem a seleção.
 * - Estabilidade: um gesto só vira o gesto atual depois de [stableFrames] frames consecutivos.
 *
 * Prioridade: PINCH > OPEN_HAND > FIST > POINTING. Um frame sem mão deve chamar [reset].
 * Não é thread-safe: use sempre a mesma thread (a thread de resultados do rastreador).
 */
class GestureRecognizer(
    private val pinchEnterRatio: Float = DEFAULT_PINCH_ENTER,
    private val pinchExitRatio: Float = DEFAULT_PINCH_EXIT,
    private val stableFrames: Int = DEFAULT_STABLE_FRAMES,
) {
    init {
        require(pinchEnterRatio > 0f && pinchEnterRatio < pinchExitRatio) {
            "pinchEnterRatio deve ser positivo e menor que pinchExitRatio"
        }
        require(stableFrames >= 1) { "stableFrames deve ser >= 1" }
    }

    private var pinchActive = false
    private var candidate = HandGesture.NONE
    private var candidateCount = 0
    private var stable = HandGesture.NONE

    /** Gesto estável da mão neste frame. */
    fun update(landmarks: List<Landmark>, imageWidth: Int, imageHeight: Int): HandGesture {
        if (landmarks.size < HandLandmarkIndex.LANDMARK_COUNT || imageWidth <= 0 || imageHeight <= 0) {
            reset()
            return HandGesture.NONE
        }
        return debounce(classifyRaw(landmarks, imageWidth, imageHeight))
    }

    /** Classificação sem estabilização. Atualiza o estado de histerese da pinça. */
    fun classifyRaw(landmarks: List<Landmark>, imageWidth: Int, imageHeight: Int): HandGesture {
        val ratio = HandPoseAnalyzer.pinchRatio(landmarks, imageWidth, imageHeight)
        pinchActive = if (pinchActive) ratio <= pinchExitRatio else ratio <= pinchEnterRatio
        if (pinchActive) return HandGesture.PINCH

        val extended = HandPoseAnalyzer.extendedFingers(landmarks, imageWidth, imageHeight)
        return when {
            extended.all { it } -> HandGesture.OPEN_HAND
            extended.none { it } -> HandGesture.FIST
            extended[0] && !extended[1] && !extended[2] && !extended[3] -> HandGesture.POINTING
            else -> HandGesture.NONE
        }
    }

    private fun debounce(raw: HandGesture): HandGesture {
        if (raw == candidate) {
            candidateCount++
        } else {
            candidate = raw
            candidateCount = 1
        }
        if (candidateCount >= stableFrames) stable = candidate
        return stable
    }

    fun reset() {
        pinchActive = false
        candidate = HandGesture.NONE
        candidateCount = 0
        stable = HandGesture.NONE
    }

    companion object {
        const val DEFAULT_PINCH_ENTER = 0.25f
        const val DEFAULT_PINCH_EXIT = 0.35f
        const val DEFAULT_STABLE_FRAMES = 3
    }
}
