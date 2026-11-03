package com.handspace.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpatialGeometryTest {

    private val forward = Ray(Vec3.ZERO, Vec3(0f, 0f, 1f))

    private fun panelAt(z: Float, x: Float = 0f, y: Float = 0f) = Panel3D(
        center = Vec3(x, y, z),
        right = Vec3(1f, 0f, 0f),
        up = Vec3(0f, 1f, 0f),
        halfWidth = 1f,
        halfHeight = 0.5f,
    )

    @Test
    fun intersectPlane_returnsDistanceToPlaneInFront() {
        val t = SpatialGeometry.intersectPlane(forward, Vec3(0f, 0f, 2f), Vec3(0f, 0f, 1f))
        assertEquals(2f, t!!, 0.0001f)
    }

    @Test
    fun intersectPlane_returnsNullForParallelRay() {
        val parallel = Ray(Vec3.ZERO, Vec3(1f, 0f, 0f))
        assertNull(SpatialGeometry.intersectPlane(parallel, Vec3(0f, 0f, 2f), Vec3(0f, 0f, 1f)))
    }

    @Test
    fun intersectPlane_returnsNullWhenPlaneIsBehindOrigin() {
        assertNull(SpatialGeometry.intersectPlane(forward, Vec3(0f, 0f, -2f), Vec3(0f, 0f, 1f)))
    }

    @Test
    fun hitPanel_insideBounds_returnsLocalCoordinates() {
        val hit = SpatialGeometry.hitPanel(forward, panelAt(z = 3f, x = 0.5f, y = -0.2f))!!
        assertEquals(3f, hit.distance, 0.0001f)
        assertEquals(-0.5f, hit.u, 0.0001f)
        assertEquals(0.2f, hit.v, 0.0001f)
    }

    @Test
    fun hitPanel_outsideBounds_returnsNull() {
        assertNull(SpatialGeometry.hitPanel(forward, panelAt(z = 3f, x = 1.5f)))
        assertNull(SpatialGeometry.hitPanel(forward, panelAt(z = 3f, y = 0.6f)))
    }

    @Test
    fun hitPanel_respectsPanelOrientation() {
        val rotated = Panel3D(
            center = Vec3(0f, 0f, 3f),
            right = Vec3(0f, 0f, 1f),
            up = Vec3(0f, 1f, 0f),
            halfWidth = 1f,
            halfHeight = 0.5f,
        )
        val hit = SpatialGeometry.hitPanel(Ray(Vec3(-2f, 0f, 3f), Vec3(1f, 0f, 0f)), rotated)
        assertEquals(2f, hit!!.distance, 0.0001f)
    }

    @Test
    fun intersectSphere_returnsEntryDistance() {
        val t = SpatialGeometry.intersectSphere(forward, Vec3(0f, 0f, 5f), 1f)
        assertEquals(4f, t!!, 0.0001f)
    }

    @Test
    fun intersectSphere_missReturnsNull_andSphereBehindReturnsNull() {
        assertNull(SpatialGeometry.intersectSphere(forward, Vec3(3f, 0f, 5f), 1f))
        assertNull(SpatialGeometry.intersectSphere(forward, Vec3(0f, 0f, -5f), 1f))
    }

    @Test
    fun pickNearest_returnsClosestPanelHit() {
        val far = panelAt(z = 5f)
        val near = panelAt(z = 2f)
        val picked = SpatialGeometry.pickNearest(forward, listOf("far" to far, "near" to near)) { it.second }
        assertEquals("near", picked?.first)
    }

    @Test
    fun pickNearest_returnsNullWhenNothingIsHit() {
        assertNull(SpatialGeometry.pickNearest(forward, listOf(panelAt(z = 2f, x = 4f))) { it })
    }

    @Test
    fun pointAt_movesAlongRay() {
        assertEquals(Vec3(0f, 0f, 4f), forward.pointAt(4f))
    }

    @Test
    fun scaleFromSpread_isProportionalAndClamped() {
        assertEquals(2f, SpatialGeometry.scaleFromSpread(1f, 0.1f, 0.2f, 0.25f, 4f), 0.0001f)
        assertEquals(4f, SpatialGeometry.scaleFromSpread(1f, 0.1f, 1f, 0.25f, 4f), 0.0001f)
        assertEquals(0.25f, SpatialGeometry.scaleFromSpread(1f, 0.1f, 0.01f, 0.25f, 4f), 0.0001f)
    }

    @Test
    fun scaleFromSpread_zeroBaseSpread_keepsBaseScale() {
        assertEquals(1f, SpatialGeometry.scaleFromSpread(1f, 0f, 0.5f, 0.25f, 4f), 0.0001f)
    }

    @Test
    fun vec3_normalizedHasUnitLength() {
        assertEquals(1f, Vec3(3f, 4f, 0f).normalized().length(), 0.0001f)
        assertEquals(Vec3.ZERO, Vec3.ZERO.normalized())
    }

    @Test
    fun intersectSphere_isConsistentForAnyDirectionLength() {
        // Dobrar o comprimento da direção deve reduzir o parâmetro t pela metade, sem mudar o ponto.
        val unit = SpatialGeometry.intersectSphere(forward, Vec3(0f, 0f, 5f), 1f)!!
        val doubled = SpatialGeometry.intersectSphere(Ray(Vec3.ZERO, Vec3(0f, 0f, 2f)), Vec3(0f, 0f, 5f), 1f)!!
        assertEquals(unit, doubled * 2f, 0.0001f)
    }
}
