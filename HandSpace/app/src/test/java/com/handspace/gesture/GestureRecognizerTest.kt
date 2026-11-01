
package com.handspace.gesture

import com.handspace.tracking.Landmark
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mãos sintéticas para testar a lógica de classificação.
 * Imagem de referência: 1000x1000 px.
 * Tamanho da mão: 300 px (punho até a base do médio).
 */
class GestureRecognizerTest {

    private val w = 1000
    private val h = 1000
    private val handSize = 0.3f

    /**
     * Monta uma mão.
     * fingers = [indicador, médio, anelar, mindinho] estendidos?
     * thumbGap = distância horizontal entre polegar e indicador.
     */
    private fun hand(
        fingers: BooleanArray,
        thumbGap: Float
    ): List<Landmark> {
        val pts = Array(21) {
            Landmark(0.5f, 0.5f, 0f)
        }

        pts[0] = Landmark(0.5f, 0.8f, 0f)
        pts[9] = Landmark(0.5f, 0.8f - handSize, 0f)

        val xs = floatArrayOf(0.35f, 0.45f, 0.55f, 0.65f)
        val mcps = intArrayOf(5, 9, 13, 17)
        val pips = intArrayOf(6, 10, 14, 18)
        val tips = intArrayOf(8, 12, 16, 20)

        for (i in 0 until 4) {
            val x = xs[i]

            // O landmark 9 define o tamanho da mão.
            // Não pode ser sobrescrito pelo MCP do dedo médio.
            if (i != 1) {
                pts[mcps[i]] = Landmark(x, 0.55f, 0f)
            }

            if (fingers[i]) {
                pts[pips[i]] = Landmark(x, 0.45f, 0f)
                pts[tips[i]] = Landmark(x, 0.2f, 0f)
            } else {
                pts[pips[i]] = Landmark(x, 0.5f, 0f)
                pts[tips[i]] = Landmark(x, 0.62f, 0f)
            }
        }

        val indexTip = pts[8]
        pts[4] = Landmark(
            indexTip.x + thumbGap,
            indexTip.y,
            0f
        )

        return pts.toList()
    }

    private val allExtended =
        booleanArrayOf(true, true, true, true)

    private val allFolded =
        booleanArrayOf(false, false, false, false)

    private val openGap = 0.3f
    private val pinchGap = 0.01f

    private fun recognizer(stable: Int = 1) =
        GestureRecognizer(stableFrames = stable)

    @Test
    fun pinch_isDetectedWhenThumbAndIndexTouch() {
        val r = recognizer()
        assertEquals(
            HandGesture.PINCH,
            r.update(hand(allFolded, pinchGap), w, h)
        )
    }

    @Test
    fun openHand_isDetectedWhenAllFingersExtended() {
        val r = recognizer()
        assertEquals(
            HandGesture.OPEN_HAND,
            r.update(hand(allExtended, openGap), w, h)
        )
    }

    @Test
    fun fist_isDetectedWhenAllFingersFolded() {
        val r = recognizer()
        assertEquals(
            HandGesture.FIST,
            r.update(hand(allFolded, openGap), w, h)
        )
    }

    @Test
    fun pointing_isDetectedWhenOnlyIndexExtended() {
        val r = recognizer()
        val indexOnly = booleanArrayOf(true, false, false, false)

        assertEquals(
            HandGesture.POINTING,
            r.update(hand(indexOnly, openGap), w, h)
        )
    }

    @Test
    fun pinch_takesPriorityOverOpenHand() {
        val r = recognizer()

        assertEquals(
            HandGesture.PINCH,
            r.update(hand(allExtended, pinchGap), w, h)
        )
    }

    @Test
    fun ambiguousFingerPattern_returnsNone() {
        val r = recognizer()
        val middleAndRing =
            booleanArrayOf(false, true, true, false)

        assertEquals(
            HandGesture.NONE,
            r.update(hand(middleAndRing, openGap), w, h)
        )
    }

    @Test
    fun pinch_hysteresis_keepsPinchInsideBand_andReleasesAboveExit() {
        val r = recognizer()

        // Razão 0.2: entra na pinça.
        assertEquals(
            HandGesture.PINCH,
            r.update(hand(allExtended, 0.06f), w, h)
        )

        // Razão 0.3: continua na pinça.
        assertEquals(
            HandGesture.PINCH,
            r.update(hand(allExtended, 0.09f), w, h)
        )

        // Razão 0.4: libera a pinça.
        assertEquals(
            HandGesture.OPEN_HAND,
            r.update(hand(allExtended, 0.12f), w, h)
        )
    }

    @Test
    fun debounce_requiresConsecutiveFrames() {
        val r = GestureRecognizer(stableFrames = 3)
        val open = hand(allExtended, openGap)

        assertEquals(HandGesture.NONE, r.update(open, w, h))
        assertEquals(HandGesture.NONE, r.update(open, w, h))
        assertEquals(HandGesture.OPEN_HAND, r.update(open, w, h))
    }

    @Test
    fun debounce_ignoresFlicker() {
        val r = GestureRecognizer(stableFrames = 3)
        val open = hand(allExtended, openGap)
        val fist = hand(allFolded, openGap)

        repeat(5) {
            assertEquals(HandGesture.NONE, r.update(open, w, h))
            assertEquals(HandGesture.NONE, r.update(fist, w, h))
        }
    }

    @Test
    fun insufficientLandmarks_resetsAndReturnsNone() {
        val r = recognizer()

        assertEquals(
            HandGesture.PINCH,
            r.update(hand(allFolded, pinchGap), w, h)
        )

        assertEquals(
            HandGesture.NONE,
            r.update(emptyList(), w, h)
        )
    }

    @Test
    fun reset_clearsPinchState() {
        val r = recognizer()

        r.update(hand(allFolded, pinchGap), w, h)
        r.reset()

        assertEquals(
            HandGesture.OPEN_HAND,
            r.update(hand(allExtended, 0.09f), w, h)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidThresholds_areRejected() {
        GestureRecognizer(
            pinchEnterRatio = 0.4f,
            pinchExitRatio = 0.3f
        )
    }
}
