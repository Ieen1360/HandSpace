package com.handspace.interaction

import com.handspace.spatial.Camera3D
import com.handspace.spatial.DemoWorld
import com.handspace.spatial.MenuAction
import com.handspace.spatial.SpatialWorld
import com.handspace.spatial.Vec3
import com.handspace.spatial.WorldKind
import com.handspace.spatial.WorldObject
import com.handspace.testutil.Pose
import com.handspace.testutil.SyntheticHands
import com.handspace.tracking.HandFrame
import com.handspace.tracking.Handedness
import com.handspace.tracking.TrackedHand
import com.handspace.tracking.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mãos sintéticas em tela 1000x1000 com transformação identidade. A ponta do indicador de cada
 * mão cai no pixel indicado; o helper calcula o deslocamento normalizado a partir dele.
 */
class SpatialWorldControllerTest {

    private val transform = ViewTransform.fillCenter(1000, 1000, 1000f, 1000f)
    private val camera = Camera3D(1000f, 1000f)

    private fun pinchAt(side: Handedness, sx: Float, sy: Float): TrackedHand =
        SyntheticHands.trackedHand(side, Pose.PINCH, sx / 1000f - 0.35f, sy / 1000f - 0.2f)

    private fun openAt(side: Handedness, sx: Float, sy: Float): TrackedHand =
        SyntheticHands.trackedHand(side, Pose.OPEN, sx / 1000f - 0.35f, sy / 1000f - 0.2f)

    private fun frame(t: Long, vararg hands: TrackedHand): HandFrame = SyntheticHands.frame(t, *hands)

    private fun screenOf(center: Vec3): Pair<Float, Float> {
        val p = camera.project(center)!!
        return p.x to p.y
    }

    private fun sphereWorld(radius: Float = 0.1f): SpatialWorld = SpatialWorld(
        listOf(WorldObject("ball", WorldKind.SPHERE, Vec3(0f, 0f, 1f), radius = radius))
    )

    @Test
    fun pinchOnSphere_grabs_moves_andReleasesOnOpenHand() {
        val world = sphereWorld()
        val ball = world.get("ball")!!
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true

        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, 500f, 500f)), transform, camera, 0)
        assertEquals(Handedness.RIGHT, ball.heldBy)

        c.onFrame(frame(33, pinchAt(Handedness.RIGHT, 600f, 500f)), transform, camera, 33)
        assertTrue("a esfera deve acompanhar a mão para a direita", ball.center.x > 0.05f)

        c.onFrame(frame(66, openAt(Handedness.RIGHT, 600f, 500f)), transform, camera, 66)
        assertNull(ball.heldBy)
        assertEquals(1, c.dragCount)
    }

    @Test
    fun pinchWhenNotInteractive_grabsNothing() {
        val world = sphereWorld()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = false
        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, 500f, 500f)), transform, camera, 0)
        assertNull(world.get("ball")!!.heldBy)
    }

    @Test
    fun pinchInEmptySpace_grabsNothing() {
        val world = sphereWorld()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true
        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, 50f, 50f)), transform, camera, 0)
        assertNull(c.hands.single().holdingId)
        assertNull(world.get("ball")!!.heldBy)
    }

    @Test
    fun handLeavingView_releasesObject() {
        val world = sphereWorld()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true
        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, 500f, 500f)), transform, camera, 0)
        c.onFrame(frame(33), transform, camera, 33)
        assertNull(world.get("ball")!!.heldBy)
        assertEquals(1, c.dragCount)
    }

    @Test
    fun pinchOnCameraButton_triggersActionOnce() {
        val world = DemoWorld.create()
        var cameraActions = 0
        val c = SpatialWorldController(world, onCameraAction = { cameraActions++ }, stableFrames = 1)
        c.interactive = true
        val button = world.get("menu_${MenuAction.CAMERA.name.lowercase()}")!!
        val (sx, sy) = screenOf(button.center)

        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, sx, sy)), transform, camera, 0)
        c.onFrame(frame(33, pinchAt(Handedness.RIGHT, sx, sy)), transform, camera, 33)

        assertEquals(1, cameraActions)
    }

    @Test
    fun homeButton_resetsMovedObject() {
        val world = DemoWorld.create()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true
        val sphere = world.get(DemoWorld.SPHERE_ID)!!
        val defaultCenter = sphere.defaultCenter
        val (sx, sy) = screenOf(sphere.center)

        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, sx, sy)), transform, camera, 0)
        c.onFrame(frame(33, pinchAt(Handedness.RIGHT, sx - 80f, sy)), transform, camera, 33)
        c.onFrame(frame(66, openAt(Handedness.RIGHT, sx - 80f, sy)), transform, camera, 66)
        assertTrue(sphere.center != defaultCenter)

        val (hx, hy) = screenOf(world.get("menu_${MenuAction.HOME.name.lowercase()}")!!.center)
        c.onFrame(frame(200, pinchAt(Handedness.RIGHT, hx, hy)), transform, camera, 200)

        assertEquals(defaultCenter, sphere.center)
    }

    @Test
    fun objectsButton_hidesSphere_andReleasesIt() {
        val world = DemoWorld.create()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true
        val sphere = world.get(DemoWorld.SPHERE_ID)!!
        val (sx, sy) = screenOf(sphere.center)
        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, sx, sy)), transform, camera, 0)
        assertEquals(Handedness.RIGHT, sphere.heldBy)

        c.onFrame(frame(33, openAt(Handedness.RIGHT, sx, sy)), transform, camera, 33)
        val (ox, oy) = screenOf(world.get("menu_${MenuAction.OBJECTS.name.lowercase()}")!!.center)
        c.onFrame(frame(300, pinchAt(Handedness.RIGHT, ox, oy)), transform, camera, 300)

        assertEquals(false, sphere.visible)
    }

    @Test
    fun settingsButton_togglesSkeleton() {
        val world = DemoWorld.create()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true
        assertTrue(c.showSkeleton)
        val (sx, sy) = screenOf(world.get("menu_${MenuAction.SETTINGS.name.lowercase()}")!!.center)
        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, sx, sy)), transform, camera, 0)
        assertEquals(false, c.showSkeleton)
    }

    @Test
    fun twoHands_grabbingSameObject_scaleWithSpread() {
        // Esfera grande o bastante para que as duas pontas caiam dentro dela.
        val world = sphereWorld(radius = 0.5f)
        val ball = world.get("ball")!!
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true

        c.onFrame(
            frame(0, pinchAt(Handedness.LEFT, 400f, 500f), pinchAt(Handedness.RIGHT, 600f, 500f)),
            transform, camera, 0,
        )
        assertEquals(1f, ball.scale, 0.0001f)

        c.onFrame(
            frame(33, pinchAt(Handedness.LEFT, 400f, 500f), pinchAt(Handedness.RIGHT, 720f, 500f)),
            transform, camera, 33,
        )
        assertTrue("afastar as mãos deve aumentar a escala", ball.scale > 1f)
    }

    @Test
    fun reset_releasesEverything() {
        val world = sphereWorld()
        val c = SpatialWorldController(world, onCameraAction = {}, stableFrames = 1)
        c.interactive = true
        c.onFrame(frame(0, pinchAt(Handedness.RIGHT, 500f, 500f)), transform, camera, 0)
        c.reset()
        assertNull(world.get("ball")!!.heldBy)
        assertTrue(c.hands.isEmpty())
    }
}
