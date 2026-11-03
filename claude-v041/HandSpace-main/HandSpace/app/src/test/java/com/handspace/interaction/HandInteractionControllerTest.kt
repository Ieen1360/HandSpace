package com.handspace.interaction

import com.handspace.gesture.HandGesture
import com.handspace.testutil.Pose
import com.handspace.testutil.SyntheticHands
import com.handspace.tracking.Handedness
import com.handspace.tracking.TrackedHand
import com.handspace.tracking.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Testa o controlador com mãos sintéticas. Com a transformação identidade, a ponta do indicador
 * (0.35, 0.2) cai em (350, 200) px. Os deslocamentos dx/dy são normalizados (1000 px = 1.0).
 */
class HandInteractionControllerTest {

    private val identity = ViewTransform.fillCenter(1000, 1000, 1000f, 1000f)

    /** Painel em (50, 500) 300x200 e botão em (300, 150) 200x80. */
    private fun scene(): SpatialScene = SpatialScene(
        listOf(
            SpatialElement("panel", ElementKind.PANEL, 300f, 200f, 0.05f, 0.5f),
            SpatialElement("color", ElementKind.BUTTON, 200f, 80f, 0.3f, 0.15f, ButtonAction.CYCLE_OBJECT_COLOR),
        )
    ).also { it.setViewport(1000f, 1000f) }

    private fun controller(scene: SpatialScene, smoothing: Float = 1f) =
        HandInteractionController(scene, cursorSmoothing = smoothing, stableFrames = 1)

    private fun hand(side: Handedness, pose: Pose, dx: Float = 0f, dy: Float = 0f): TrackedHand =
        SyntheticHands.trackedHand(side, pose, dx, dy)

    @Test
    fun pinchOnButton_firesOnceWhilePinchIsHeld() {
        val s = scene()
        val c = controller(s)
        val pinch = hand(Handedness.RIGHT, Pose.PINCH)

        c.onFrame(SyntheticHands.frame(0, pinch), identity, 0)
        for (t in 33L..330L step 33L) {
            c.onFrame(SyntheticHands.frame(t, pinch), identity, t)
        }

        assertEquals(1, s.colorIndex)
    }

    @Test
    fun repeatedPinch_withinCooldown_isIgnored() {
        val s = scene()
        val c = controller(s)
        val pinch = hand(Handedness.RIGHT, Pose.PINCH)
        val open = hand(Handedness.RIGHT, Pose.OPEN)

        c.onFrame(SyntheticHands.frame(0, pinch), identity, 0)
        c.onFrame(SyntheticHands.frame(100, open), identity, 100)
        c.onFrame(SyntheticHands.frame(200, pinch), identity, 200)
        assertEquals("pinça dentro do cooldown deve ser ignorada", 1, s.colorIndex)

        c.onFrame(SyntheticHands.frame(300, open), identity, 300)
        c.onFrame(SyntheticHands.frame(500, pinch), identity, 500)
        assertEquals("pinça após o cooldown deve disparar", 2, s.colorIndex)
    }

    @Test
    fun pinchOnPanel_dragsWithHand_untilOpenHand() {
        val s = scene()
        val c = controller(s)
        val panel = s.element("panel")!!

        // Ponta do indicador em (200, 600), dentro do painel.
        c.onFrame(SyntheticHands.frame(0, hand(Handedness.RIGHT, Pose.PINCH, -0.15f, 0.4f)), identity, 0)
        assertEquals(Handedness.RIGHT, panel.heldBy)

        // Mão move para (300, 550): o painel acompanha, mantendo o ponto de pegada.
        c.onFrame(SyntheticHands.frame(33, hand(Handedness.RIGHT, Pose.PINCH, -0.05f, 0.35f)), identity, 33)
        assertEquals(150f, panel.left, 0.01f)
        assertEquals(450f, panel.top, 0.01f)

        // Abrir a mão solta o painel.
        c.onFrame(SyntheticHands.frame(66, hand(Handedness.RIGHT, Pose.OPEN, -0.05f, 0.35f)), identity, 66)
        assertNull(panel.heldBy)
        assertEquals(1, s.dragCount)

        // Movimento depois de soltar não arrasta mais o painel.
        c.onFrame(SyntheticHands.frame(100, hand(Handedness.RIGHT, Pose.OPEN, 0.3f, 0f)), identity, 100)
        assertEquals(150f, panel.left, 0.01f)
        assertEquals(450f, panel.top, 0.01f)
    }

    @Test
    fun pinchInEmptySpace_holdsNothing() {
        val s = scene()
        val c = controller(s)

        // Ponta em (800, 200): fora de todos os elementos.
        c.onFrame(SyntheticHands.frame(0, hand(Handedness.RIGHT, Pose.PINCH, 0.45f, 0f)), identity, 0)
        c.onFrame(SyntheticHands.frame(33, hand(Handedness.RIGHT, Pose.PINCH, 0.55f, 0f)), identity, 33)

        assertEquals(0, s.dragCount)
        assertNull(s.element("panel")!!.heldBy)
        assertNull(c.cursors.single().holdingId)
    }

    @Test
    fun handLeavingView_whileHolding_releasesElement() {
        val s = scene()
        val c = controller(s)
        val panel = s.element("panel")!!

        c.onFrame(SyntheticHands.frame(0, hand(Handedness.RIGHT, Pose.PINCH, -0.15f, 0.4f)), identity, 0)
        c.onFrame(SyntheticHands.frame(33), identity, 33)

        assertNull(panel.heldBy)
        assertEquals(1, s.dragCount)
    }

    @Test
    fun reset_releasesElement() {
        val s = scene()
        val c = controller(s)
        val panel = s.element("panel")!!

        c.onFrame(SyntheticHands.frame(0, hand(Handedness.RIGHT, Pose.PINCH, -0.15f, 0.4f)), identity, 0)
        c.reset()

        assertNull(panel.heldBy)
        assertEquals(1, s.dragCount)
    }

    @Test
    fun twoHands_operateIndependently() {
        val s = scene()
        val c = controller(s)

        // Mão esquerda segura o painel; mão direita aciona o botão no mesmo frame.
        c.onFrame(
            SyntheticHands.frame(
                0,
                hand(Handedness.LEFT, Pose.PINCH, -0.15f, 0.4f),
                hand(Handedness.RIGHT, Pose.PINCH, 0f, 0f),
            ),
            identity,
            0,
        )

        assertEquals(Handedness.LEFT, s.element("panel")!!.heldBy)
        assertEquals(1, s.colorIndex)
        assertEquals(2, c.cursors.size)
    }

    @Test
    fun cursor_isSmoothedTowardsTarget() {
        val s = scene()
        val c = controller(s, smoothing = 0.5f)

        c.onFrame(SyntheticHands.frame(0, hand(Handedness.RIGHT, Pose.POINT)), identity, 0)
        c.onFrame(SyntheticHands.frame(33, hand(Handedness.RIGHT, Pose.POINT, 0.1f, 0f)), identity, 33)

        val cursor = c.cursors.single()
        assertEquals(400f, cursor.x, 0.01f)
        assertEquals(200f, cursor.y, 0.01f)
        assertEquals(HandGesture.POINTING, cursor.gesture)
    }

    @Test
    fun cursor_isMappedThroughViewTransform() {
        val s = scene()
        val c = controller(s)
        val halfScale = ViewTransform.fillCenter(1000, 1000, 500f, 500f)

        c.onFrame(SyntheticHands.frame(0, hand(Handedness.RIGHT, Pose.POINT)), halfScale, 0)

        val cursor = c.cursors.single()
        assertEquals(175f, cursor.x, 0.01f)
        assertEquals(100f, cursor.y, 0.01f)
    }
}
