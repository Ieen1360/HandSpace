package com.handspace.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Camera3DTest {

    private val camera = Camera3D(1000f, 1000f)

    @Test
    fun pointOnAxis_projectsToViewCenter() {
        val p = camera.project(Vec3(0f, 0f, 1f))!!
        assertEquals(500f, p.x, 0.01f)
        assertEquals(500f, p.y, 0.01f)
    }

    @Test
    fun pointBehindNearPlane_isNotProjected() {
        assertNull(camera.project(Vec3(0f, 0f, -1f)))
    }

    @Test
    fun unprojectThenProject_roundTrips() {
        val original = Vec3(0.1f, -0.05f, 1.2f)
        val p = camera.project(original)!!
        val ray = camera.unproject(p.x, p.y)
        val back = ray.pointAt(1.2f)
        assertEquals(original.x, back.x, 0.0005f)
        assertEquals(original.y, back.y, 0.0005f)
        assertEquals(original.z, back.z, 0.0005f)
    }

    @Test
    fun unprojectAtDepth_matchesRayPointAtSameDepth() {
        val fromDepth = camera.unprojectAtDepth(300f, 700f, 0.8f)
        val fromRay = camera.unproject(300f, 700f).pointAt(0.8f)
        assertEquals(fromRay.x, fromDepth.x, 0.0005f)
        assertEquals(fromRay.y, fromDepth.y, 0.0005f)
        assertEquals(0.8f, fromDepth.z, 0.0001f)
    }

    @Test
    fun focalLength_matchesVerticalFov() {
        // 60° verticais em 1000 px: f = 500 / tan(30°) ≈ 866.03
        assertEquals(866.03f, camera.focalPx, 0.05f)
    }
}
