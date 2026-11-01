
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

class SpatialWorldController(
    private val world: SpatialWorld,
    private val onCameraAction: () -> Unit,
    private val cooldownMs: Long = ActivationCooldown.DEFAULT_INTERVAL_MS,
    private val stableFrames: Int = GestureRecognizer.DEFAULT_STABLE_FRAMES,
) {

    data class HandPose3D(
        val hand: Handedness,
        val joints: List<Vec3>,
        val gesture: HandGesture,
        val tipScreen: ScreenPoint,
        val holdingId: String?,
        val hoverId: String?,
    )

    var interactive: Boolean = false

    var showSkeleton: Boolean = true
        private set

    var dragCount: Int = 0
        private set

    val hands: List<HandPose3D>
        get() = handList

    private val skeleton = HandSkeleton3D()
    private val handStates = HashMap<Handedness, HandState>()
    private val handList = ArrayList<HandPose3D>()
    private val buttonCooldowns = HashMap<String, ActivationCooldown>()
    private val scaleStates = HashMap<String, ScaleState>()

    private inner class HandState(val hand: Handedness) {
        val recognizer = GestureRecognizer(stableFrames = stableFrames)
        var gesture: HandGesture = HandGesture.NONE
        var ray: Ray? = null
        var wristZ: Float = 0f
        var grab: WorldObject? = null
        var distance: Float = 0f
        var grabDistance: Float = 0f
        var wristZAtGrab: Float = 0f
        var offset: Vec3 = Vec3.ZERO
        var seen: Boolean = false
    }

    private class ScaleState(
        val baseScale: Float,
        val baseSpread: Float,
    )

    fun onFrame(
        frame: HandFrame,
        transform: ViewTransform,
        camera: Camera3D,
        nowMs: Long,
    ) {
        handList.clear()

        for (state in handStates.values) {
            state.seen = false
        }

        if (frame.imageWidth > 0 && frame.imageHeight > 0) {
            for (hand in frame.hands) {
                if (hand.landmarks.size < HandLandmarkIndex.LANDMARK_COUNT) continue

                val state = handStates.getOrPut(hand.handedness) {
                    HandState(hand.handedness)
                }

                if (state.seen) continue

                state.seen = true
                processHand(hand, state, frame, transform, camera, nowMs)
            }
        }

        for (state in handStates.values) {
            if (!state.seen) {
                lose(state)
            }
        }

        updateTwoHandScale()
    }

    fun reset() {
        for (state in handStates.values) {
            release(state)
            state.recognizer.reset()
            state.gesture = HandGesture.NONE
            state.ray = null
            state.wristZ = 0f
            state.seen = false
        }

        handStates.clear()
        handList.clear()
        scaleStates.clear()
        buttonCooldowns.clear()
        dragCount = 0
    }

    fun toggleSkeleton() {
        showSkeleton = !showSkeleton
    }

    private fun processHand(
        hand: TrackedHand,
        state: HandState,
        frame: HandFrame,
        transform: ViewTransform,
        camera: Camera3D,
        nowMs: Long,
    ) {
        val lms = hand.landmarks

        val gesture = state.recognizer.update(
            lms,
            frame.imageWidth,
            frame.imageHeight,
        )

        val tip = transform.map(lms[HandLandmarkIndex.INDEX_TIP])
        val ray = camera.unproject(tip.x, tip.y)

        state.ray = ray
        state.wristZ = lms[HandLandmarkIndex.WRIST].z

        val previous = state.gesture
        state.gesture = gesture

        if (interactive) {
            if (
                gesture == HandGesture.PINCH &&
                previous != HandGesture.PINCH
            ) {
                onPinch(state, ray, nowMs)
            }

            if (
                gesture == HandGesture.OPEN_HAND &&
                previous != HandGesture.OPEN_HAND
            ) {
                release(state)
            }

            val grabbed = state.grab

            if (grabbed != null && grabbed.heldBy == state.hand) {
                moveGrabbed(state, grabbed, ray)
            }
        }

        val holdingId = state.grab?.id

        val hoverId = if (interactive && holdingId == null) {
            pickTarget(ray)?.obj?.id
        } else {
            holdingId
        }

        handList.add(
            HandPose3D(
                hand = hand.handedness,
                joints = skeleton.joints(hand, transform, camera),
                gesture = gesture,
                tipScreen = tip,
                holdingId = holdingId,
                hoverId = hoverId,
            )
        )
    }

    /**
     * Seleciona o alvo do raio.
     * Botões têm prioridade quando são efetivamente atingidos.
     */
    private fun pickTarget(ray: Ray) =
        world.objects
            .asSequence()
            .filter { it.visible }
            .mapNotNull { obj ->
                obj.hit(ray)?.let { distance ->
                    obj to distance
                }
            }
            .minWithOrNull(
                compareBy<Pair<WorldObject, Float>> {
                    if (it.first.kind == WorldKind.BUTTON) 0 else 1
                }.thenBy {
                    it.second
                }
            )
            ?.let { (obj, distance) ->
                com.handspace.spatial.WorldHit(obj, distance)
            }

    private fun onPinch(
        state: HandState,
        ray: Ray,
        nowMs: Long,
    ) {
        if (state.grab != null) return

        val hit = pickTarget(ray) ?: return
        val obj = hit.obj

        if (obj.kind == WorldKind.BUTTON) {
            pressButton(obj, nowMs)
            return
        }

        state.grab = obj
        state.grabDistance = hit.distance
        state.distance = hit.distance
        state.wristZAtGrab = state.wristZ
        state.offset = obj.center - ray.pointAt(hit.distance)

        if (obj.heldBy == null) {
            obj.heldBy = state.hand
        }
    }

    private fun pressButton(
        button: WorldObject,
        nowMs: Long,
    ) {
        val cooldown = buttonCooldowns.getOrPut(button.id) {
            ActivationCooldown(cooldownMs)
        }

        if (!cooldown.tryFire(nowMs)) return

        button.pressedAtMs = nowMs
        button.action?.let { performAction(it) }
    }

    private fun moveGrabbed(
        state: HandState,
        obj: WorldObject,
        ray: Ray,
    ) {
        state.distance = (
            state.grabDistance +
                (state.wristZ - state.wristZAtGrab) * PULL_GAIN_M
            ).coerceIn(MIN_DISTANCE_M, MAX_DISTANCE_M)

        val target = ray.pointAt(state.distance) + state.offset

        obj.center = Vec3(
            target.x,
            target.y,
            target.z.coerceAtLeast(MIN_OBJECT_Z_M),
        )
    }

    private fun release(state: HandState) {
        val obj = state.grab ?: return

        state.grab = null

        if (obj.heldBy == state.hand) {
            obj.heldBy = handStates.values
                .firstOrNull { it.grab === obj }
                ?.hand

            dragCount++
        }

        scaleStates.remove(obj.id)
    }

    private fun lose(state: HandState) {
        release(state)
        state.recognizer.reset()
        state.gesture = HandGesture.NONE
        state.ray = null
    }

    private fun releaseObject(obj: WorldObject) {
        for (state in handStates.values) {
            if (state.grab === obj) {
                release(state)
            }
        }

        obj.heldBy = null
        scaleStates.remove(obj.id)
    }

    private fun releaseAllObjects() {
        for (state in handStates.values) {
            release(state)
        }

        for (obj in world.objects) {
            obj.heldBy = null
        }

        scaleStates.clear()
    }

    private fun updateTwoHandScale() {
        val held = handStates.values.filter {
            it.grab != null && it.ray != null
        }

        val touched = HashSet<String>()

        for (i in held.indices) {
            for (j in i + 1 until held.size) {
                val a = held[i]
                val b = held[j]

                val obj = a.grab ?: continue

                if (b.grab !== obj) continue

                val tipA = tipWorld(a) ?: continue
                val tipB = tipWorld(b) ?: continue

                val spread = (tipA - tipB).length()

                val state = scaleStates.getOrPut(obj.id) {
                    ScaleState(obj.scale, spread)
                }

                obj.scale = SpatialGeometry.scaleFromSpread(
                    state.baseScale,
                    state.baseSpread,
                    spread,
                    MIN_SCALE,
                    MAX_SCALE,
                )

                touched.add(obj.id)
            }
        }

        scaleStates.keys.retainAll(touched)
    }

    private fun tipWorld(state: HandState): Vec3? {
        return state.ray?.pointAt(state.distance)
    }

    private fun performAction(action: MenuAction) {
        when (action) {
            MenuAction.HOME -> {
                releaseAllObjects()
                world.resetLayout()
            }

            MenuAction.OBJECTS -> {
                val sphere = world.get(DemoWorld.SPHERE_ID) ?: return

                if (sphere.visible) {
                    releaseObject(sphere)
                    sphere.visible = false
                } else {
                    sphere.visible = true
                }
            }

            MenuAction.SETTINGS -> {
                toggleSkeleton()
            }

            MenuAction.TRACKING -> {
                val info = world.get(DemoWorld.INFO_PANEL_ID) ?: return
                info.visible = !info.visible
            }

            MenuAction.CAMERA -> {
                onCameraAction()
            }
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
