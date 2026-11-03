package com.handspace.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialWorldTest {

    private val forward = Ray(Vec3.ZERO, Vec3(0f, 0f, 1f))

    @Test
    fun pick_returnsNearestVisibleObject() {
        val near = WorldObject("near", WorldKind.SPHERE, Vec3(0f, 0f, 1f), radius = 0.2f)
        val far = WorldObject("far", WorldKind.SPHERE, Vec3(0f, 0f, 3f), radius = 0.2f)
        val world = SpatialWorld(listOf(far, near))
        assertEquals("near", world.pick(forward)?.obj?.id)
    }

    @Test
    fun pick_ignoresHiddenObjects() {
        val only = WorldObject("only", WorldKind.SPHERE, Vec3(0f, 0f, 1f), radius = 0.2f)
        only.visible = false
        assertNull(SpatialWorld(listOf(only)).pick(forward))
    }

    @Test
    fun resetLayout_restoresCenterAndScale() {
        val sphere = WorldObject("s", WorldKind.SPHERE, Vec3(0.1f, 0f, 1f), radius = 0.1f)
        val world = SpatialWorld(listOf(sphere))
        sphere.center = Vec3(0.5f, 0.5f, 2f)
        sphere.scale = 2f
        world.resetLayout()
        assertEquals(Vec3(0.1f, 0f, 1f), sphere.center)
        assertEquals(1f, sphere.scale, 0f)
    }

    @Test
    fun demoWorld_hasOneButtonPerMenuAction_andPanelAndSphere() {
        val world = DemoWorld.create()
        val actions = world.objects.filter { it.kind == WorldKind.BUTTON }.mapNotNull { it.action }
        assertEquals(MenuAction.values().toList(), actions)
        assertTrue(world.get(DemoWorld.INFO_PANEL_ID) != null)
        assertTrue(world.get(DemoWorld.SPHERE_ID) != null)
    }

    @Test
    fun demoWorld_buttonsAreInsidePortraitFrustum() {
        // Meia largura visível a 0,75 m em retrato 1080x2340: 0,75 * tan(30°) * (1080/2340) ≈ 0,199 m.
        val limit = 0.75f * Math.tan(Math.toRadians(30.0)).toFloat() * (1080f / 2340f)
        DemoWorld.create().objects.filter { it.kind == WorldKind.BUTTON }.forEach { b ->
            assertTrue("${b.id} sai da tela", kotlin.math.abs(b.center.x) + b.halfWidth <= limit)
        }
    }
}
