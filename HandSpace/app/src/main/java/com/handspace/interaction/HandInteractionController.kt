package com.handspace.interaction

import com.handspace.gesture.GestureRecognizer
import com.handspace.gesture.HandGesture
import com.handspace.tracking.HandFrame
import com.handspace.tracking.HandLandmarkIndex
import com.handspace.tracking.Handedness
import com.handspace.tracking.Landmark
import com.handspace.tracking.ViewTransform

/** Estado de uma mão no último frame: cursor já suavizado e o que a mão está fazendo. */
data class HandCursor(
    val hand: Handedness,
    val x: Float,
    val y: Float,
    val gesture: HandGesture,
    val holdingId: String?,
    val hoverId: String?,
)

/**
 * Traduz gestos em ações sobre a [SpatialScene].
 *
 * - Cursor: segue a ponta do indicador (landmark 8), já mapeado para a tela e suavizado.
 * - PINCH na borda de subida: seleciona o elemento sob o cursor. Botões disparam uma vez;
 *   painéis e objetos passam a ser arrastados. Pinça contínua não gera novas seleções,
 *   e cada nova seleção respeita o cooldown.
 * - OPEN_HAND na borda de subida: solta o elemento que a mão segurava.
 * - FIST e NONE: sem ação; um elemento já segurado continua seguro.
 * - Mão que sai do campo de visão solta o que segurava.
 * - Cada mão tem seu próprio estado, indexado pela lateralidade.
 *
 * Deve ser usado sempre na thread principal, junto com a renderização.
 */
class HandInteractionController(
    private val scene: SpatialScene,
    private val cooldownMs: Long = ActivationCooldown.DEFAULT_INTERVAL_MS,
    private val cursorSmoothing: Float = CursorSmoother.DEFAULT_FACTOR,
    private val stableFrames: Int = GestureRecognizer.DEFAULT_STABLE_FRAMES,
) {

    private inner class HandSlot {
        val recognizer = GestureRecognizer(stableFrames = stableFrames)
        val smoother = CursorSmoother(cursorSmoothing)
        val cooldown = ActivationCooldown(cooldownMs)
        var gesture: HandGesture = HandGesture.NONE
        var cursorX: Float = 0f
        var cursorY: Float = 0f
        var holding: SpatialElement? = null
        var grabDx: Float = 0f
        var grabDy: Float = 0f
        var seen: Boolean = false
    }

    private val slots = HashMap<Handedness, HandSlot>()
    private val cursorList = ArrayList<HandCursor>()

    /** Cursores do último frame, uma entrada por mão visível. */
    val cursors: List<HandCursor> get() = cursorList

    fun primaryGesture(): HandGesture = cursorList.firstOrNull()?.gesture ?: HandGesture.NONE

    fun onFrame(frame: HandFrame, transform: ViewTransform, nowMs: Long) {
        cursorList.clear()
        for (slot in slots.values) slot.seen = false

        if (frame.imageWidth > 0 && frame.imageHeight > 0) {
            for (hand in frame.hands) {
                if (hand.landmarks.size < HandLandmarkIndex.LANDMARK_COUNT) continue
                val slot = slots.getOrPut(hand.handedness) { HandSlot() }
                if (slot.seen) continue
                slot.seen = true
                processHand(hand.handedness, slot, hand.landmarks, frame, transform, nowMs)
            }
        }

        for (slot in slots.values) {
            if (!slot.seen) lose(slot)
        }
    }

    /** Solta tudo e limpa os cursores. Chamar quando a câmera for pausada. */
    fun reset() {
        for (slot in slots.values) lose(slot)
        cursorList.clear()
    }

    private fun processHand(
        hand: Handedness,
        slot: HandSlot,
        landmarks: List<Landmark>,
        frame: HandFrame,
        transform: ViewTransform,
        nowMs: Long,
    ) {
        val gesture = slot.recognizer.update(landmarks, frame.imageWidth, frame.imageHeight)
        val tip = transform.map(landmarks[HandLandmarkIndex.INDEX_TIP])
        val smoothed = slot.smoother.update(tip.x, tip.y)
        slot.cursorX = smoothed.x
        slot.cursorY = smoothed.y

        val previous = slot.gesture
        slot.gesture = gesture

        if (gesture == HandGesture.PINCH && previous != HandGesture.PINCH) {
            onPinchStart(hand, slot, nowMs)
        }
        if (gesture == HandGesture.OPEN_HAND && previous != HandGesture.OPEN_HAND) {
            release(slot)
        }
        slot.holding?.let { held ->
            scene.moveTo(held, slot.cursorX - slot.grabDx, slot.cursorY - slot.grabDy)
        }

        val holdingId = slot.holding?.id
        val hoverId = holdingId ?: scene.hitTest(slot.cursorX, slot.cursorY)?.id
        cursorList.add(HandCursor(hand, slot.cursorX, slot.cursorY, gesture, holdingId, hoverId))
    }

    private fun onPinchStart(hand: Handedness, slot: HandSlot, nowMs: Long) {
        if (slot.holding != null) return
        val target = scene.hitTest(slot.cursorX, slot.cursorY) ?: return
        val owner = target.heldBy
        if (owner != null && owner != hand) return
        if (!slot.cooldown.tryFire(nowMs)) return

        if (target.kind == ElementKind.BUTTON) {
            scene.press(target, nowMs)
            return
        }

        slot.holding = target
        slot.grabDx = slot.cursorX - target.left
        slot.grabDy = slot.cursorY - target.top
        target.heldBy = hand
        scene.bringToFront(target)
    }

    private fun release(slot: HandSlot) {
        val held = slot.holding ?: return
        held.heldBy = null
        slot.holding = null
        scene.completeDrag()
    }

    private fun lose(slot: HandSlot) {
        release(slot)
        slot.recognizer.reset()
        slot.smoother.reset()
        slot.gesture = HandGesture.NONE
    }
}
