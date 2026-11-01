package com.handspace.interaction

import com.handspace.gesture.GestureRecognizer
import com.handspace.gesture.HandGesture
import com.handspace.spatial.Camera3D
import com.handspace.spatial.DemoWorld
import com.handspace.spatial.HandSkeleton3D
import com.handspace.spatial.MenuAction
import com.handspace.spatial.Ray
import com.handspace.spatial.SpatialGeometry
import com.handspace.spatial.SpatialWorld
import com.handspace.spatial.Vec3
import com.handspace.spatial.WorldKind
import com.handspace.spatial.WorldObject
import com.handspace.tracking.HandFrame
import com.handspace.tracking.HandLandmarkIndex
import com.handspace.tracking.Handedness
import com.handspace.tracking.ScreenPoint
import com.handspace.tracking.TrackedHand
import com.handspace.tracking.ViewTransform

/**
 * Gestos → ações no mundo virtual 3D.
 *
 * - O raio de cada mão sai da câmera e passa pela ponta do indicador. Desenho e seleção usam a mesma projeção.
 * - PINCH na borda de subida: aciona um botão (uma vez) ou agarra um painel/esfera.
 * - Agarrar: a mão principal move o objeto ao longo do próprio raio. Afastar ou aproximar a mão no eixo
 *   de profundidade puxa ou empurra o objeto (PULL_GAIN_M por unidade de z do landmark do punho).
 * - Duas mãos agarrando o mesmo objeto: a escala acompanha a distância entre as pontas dos indicadores.
 * - OPEN_HAND na borda de subida solta. FIST e NONE não têm ação. Mão que sai da visão solta tudo.
 * - Objetos só são agarrados e acionados quando [interactive] é true (câmera traseira).
 *
 * Deve ser usado na thread principal.
 */
class SpatialWorldController(
    private val world: SpatialWorld,
    private val onCameraAction: () -> Unit,
    private val cooldownMs: Long = ActivationCooldown.DEFAULT_INTERVAL_MS,
    private val stableFrames: Int = GestureRecognizer.DEFAULT_STABLE_FRAMES,
) {

    /** Estado de uma mão para desenho: 21 pontos 3D, gesto e o que a mão está tocando. */
    data class HandPose3D(
        val hand: Handedness,
        val joints: List<Vec3>,
        val gesture: HandGesture,
        val tipScreen: ScreenPoint,
        val holdingId: String?,
        val hoverId: String?,
    )

    /** Quando false (câmera frontal), as mãos são desenhadas mas não agarram nem acionam objetos. */
    var interactive: Boolean = false

    /** Alternado pelo botão "Config." do menu. Controla só a visualização do esqueleto. */
    var showSkeleton: Boolean = true
        private set

    /** Arrastes concluídos pela mão principal. */
    var dragCount: Int = 0
        private set

    val hands: List<HandPose3D> get() = handList

    private val skeleton = HandSkeleton3D()
    private val slots = HashMap<Handedness, Slot>()

    /** Cooldown por botão: impede repetir o mesmo botão em rajada, sem bloquear outros elementos. */
    private val buttonCooldowns = HashMap<String, ActivationCooldown>()
    private val handList = ArrayList<HandPose3D>()
    private val scaleStates = HashMap<String, ScaleState>()

    private inner class Slot(val hand: Handedness) {
        val recognizer = GestureRecognizer(stableFrames = stableFrames)
        var gesture: HandGesture = HandGesture.NONE
        var ray: Ray? = null
        var wristZ: Float = 0f
        var wristZGrab: Float = 0f
        var grab: WorldObject? = null
        var tGrab: Float = 0f
        var tCur: Float = 0f
        var offset: Vec3 = Vec3.ZERO
        var seen: Boolean = false

        /** Ponta do indicador no mundo, no raio atual, à distância do objeto agarrado. */
        fun worldTip(): Vec3? = ray?.pointAt(tCur)
    }

    private class ScaleState(val baseScale: Float, val baseSpread: Float)

    fun onFrame(frame: HandFrame, transform: ViewTransform, camera: Camera3D, nowMs: Long) {
        handList.clear()
        for (s in slots.values) s.seen = false

        if (frame.imageWidth > 0 && frame.imageHeight > 0) {
            for (hand in frame.hands) {
                if (hand.landmarks.size < HandLandmarkIndex.LANDMARK_COUNT) continue
                val slot = slots.getOrPut(hand.handedness) { Slot(hand.handedness) }
                if (slot.seen) continue
                slot.seen = true
                processHand(hand, slot, frame, transform, camera, nowMs)
            }
        }

        for (s in slots.values) {
            if (!s.seen) lose(s)
        }
        updateTwoHandScale()
    }

    /** Solta tudo e limpa o estado. Chamar ao parar a câmera. */
    fun reset() {
        for (s in slots.values) lose(s)
        handList.clear()
        scaleStates.clear()
    }

    /** Liga ou desliga a visualização do esqueleto (botão Config.). */
    fun toggleSkeleton() {
        showSkeleton = !showSkeleton
    }

    private fun processHand(
        hand: TrackedHand,
        slot: Slot,
        frame: HandFrame,
        transform: ViewTransform,
        camera: Camera3D,
        nowMs: Long,
    ) {
        val lms = hand.landmarks
        val gesture = slot.recognizer.update(lms, frame.imageWidth, frame.imageHeight)
        val tipScreen = transform.map(lms[HandLandmarkIndex.INDEX_TIP])
        val ray = camera.unproject(tipScreen.x, tipScreen.y)
        slot.ray = ray
        slot.wristZ = lms[HandLandmarkIndex.WRIST].z

        val previous = slot.gesture
        slot.gesture = gesture

        if (interactive) {
            if (gesture == HandGesture.PINCH && previous != HandGesture.PINCH) {
                onPinchStart(slot, ray, nowMs)
            }
            if (gesture == HandGesture.OPEN_HAND && previous != HandGesture.OPEN_HAND) {
                release(slot)
            }
            val grabbed = slot.grab
            if (grabbed != null && grabbed.heldBy == slot.hand) follow(slot, grabbed, ray)
        }

        val holding = slot.grab?.id
        val hover = if (interactive && holding == null) world.pick(ray)?.obj?.id else holding
        val joints = skeleton.joints(hand, transform, camera)
        handList.add(HandPose3D(hand.handedness, joints, gesture, tipScreen, holding, hover))
    }

    private fun onPinchStart(slot: Slot, ray: Ray, nowMs: Long) {
        if (slot.grab != null) return
        val hit = world.pick(ray) ?: return
        val obj = hit.obj

        // Botão dispara uma vez por pinça, e o mesmo botão não repete antes do cooldown.
        if (obj.kind == WorldKind.BUTTON) {
            val cooldown = buttonCooldowns.getOrPut(obj.id) { ActivationCooldown(cooldownMs) }
            if (!cooldown.tryFire(nowMs)) return
            obj.pressedAtMs = nowMs
            obj.action?.let { performAction(it) }
            return
        }

        slot.grab = obj
        slot.tGrab = hit.distance
        slot.tCur = hit.distance
        slot.offset = obj.center - ray.pointAt(hit.distance)
        slot.wristZGrab = slot.wristZ
        // A primeira mão vira principal. Uma segunda mão pode se juntar só para escalar.
        if (obj.heldBy == null) obj.heldBy = slot.hand
    }

    private fun follow(slot: Slot, obj: WorldObject, ray: Ray) {
        slot.tCur = (slot.tGrab + (slot.wristZ - slot.wristZGrab) * PULL_GAIN_M)
            .coerceIn(MIN_DISTANCE_M, MAX_DISTANCE_M)
        val target = ray.pointAt(slot.tCur) + slot.offset
        obj.center = Vec3(target.x, target.y, target.z.coerceAtLeast(MIN_OBJECT_Z_M))
    }

    private fun release(slot: Slot) {
        val obj = slot.grab ?: return
        slot.grab = null
        if (obj.heldBy == slot.hand) {
            // Se outra mão ainda segura o objeto, ela passa a ser a principal.
            obj.heldBy = slots.values.firstOrNull { it.grab === obj }?.hand
            dragCount++
        }
    }

    private fun lose(slot: Slot) {
        release(slot)
        slot.recognizer.reset()
        slot.gesture = HandGesture.NONE
        slot.ray = null
    }

    /** Solta todas as mãos que seguram [obj]. Usado quando o objeto fica oculto. */
    private fun releaseObject(obj: WorldObject) {
        for (s in slots.values) {
            if (s.grab === obj) release(s)
        }
    }

    private fun updateTwoHandScale() {
        val active = slots.values.filter { it.grab != null && it.ray != null }
        val touched = HashSet<String>()
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]
                val b = active[j]
                val objA = a.grab ?: continue
                val objB = b.grab ?: continue
                if (objA !== objB) continue
                val ta = a.worldTip() ?: continue
                val tb = b.worldTip() ?: continue
                val spread = (ta - tb).length()
                val state = scaleStates.getOrPut(objA.id) { ScaleState(objA.scale, spread) }
                objA.scale = SpatialGeometry.scaleFromSpread(
                    state.baseScale, state.baseSpread, spread, MIN_SCALE, MAX_SCALE,
                )
                touched.add(objA.id)
            }
        }
        scaleStates.keys.retainAll(touched)
    }

    private fun performAction(action: MenuAction) {
        when (action) {
            MenuAction.HOME -> world.resetLayout()
            MenuAction.OBJECTS -> {
                val sphere = world.get(DemoWorld.SPHERE_ID) ?: return
                sphere.visible = !sphere.visible
                if (!sphere.visible) releaseObject(sphere)
            }
            MenuAction.SETTINGS -> toggleSkeleton()
            MenuAction.TRACKING -> {
                val info = world.get(DemoWorld.INFO_PANEL_ID) ?: return
                info.visible = !info.visible
            }
            MenuAction.CAMERA -> onCameraAction()
        }
    }

    private companion object {
        const val PULL_GAIN_M = 2f
        const val MIN_DISTANCE_M = 0.2f
        const val MAX_DISTANCE_M = 3f
        const val MIN_OBJECT_Z_M = 0.2f
        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 2.5f
    }
}
