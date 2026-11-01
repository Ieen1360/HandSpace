package com.handspace.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialSceneTest {

    @Test
    fun firstLayout_usesDefaultFractions() {
        val scene = SpatialScene(listOf(SpatialElement("a", ElementKind.PANEL, 100f, 50f, 0.5f, 0.25f)))
        scene.setViewport(1000f, 800f)
        val a = scene.element("a")!!
        assertEquals(500f, a.left, 0.01f)
        assertEquals(200f, a.top, 0.01f)
    }

    @Test
    fun resize_scalesPositionsProportionally() {
        val scene = SpatialScene(listOf(SpatialElement("a", ElementKind.PANEL, 100f, 100f, 0.5f, 0.5f)))
        scene.setViewport(1000f, 1000f)
        scene.setViewport(500f, 500f)
        val a = scene.element("a")!!
        assertEquals(250f, a.left, 0.01f)
        assertEquals(250f, a.top, 0.01f)
    }

    @Test
    fun hitTest_returnsTopmostElement_andFollowsBringToFront() {
        val back = SpatialElement("back", ElementKind.PANEL, 200f, 200f, 0f, 0f)
        val front = SpatialElement("front", ElementKind.BUTTON, 100f, 100f, 0.05f, 0.05f, ButtonAction.RESET_LAYOUT)
        val scene = SpatialScene(listOf(back, front))
        scene.setViewport(1000f, 1000f)

        assertEquals("front", scene.hitTest(60f, 60f)?.id)
        scene.bringToFront(back)
        assertEquals("back", scene.hitTest(60f, 60f)?.id)
    }

    @Test
    fun hitTest_returnsNullOutsideAllElements() {
        val scene = SpatialScene(listOf(SpatialElement("a", ElementKind.PANEL, 100f, 100f, 0.1f, 0.1f)))
        scene.setViewport(1000f, 1000f)
        assertNull(scene.hitTest(900f, 900f))
    }

    @Test
    fun moveTo_clampsInsideViewport() {
        val scene = SpatialScene(listOf(SpatialElement("a", ElementKind.PANEL, 200f, 200f, 0.1f, 0.1f)))
        scene.setViewport(1000f, 1000f)
        val a = scene.element("a")!!

        scene.moveTo(a, -50f, 2000f)
        assertEquals(0f, a.left, 0.01f)
        assertEquals(800f, a.top, 0.01f)
    }

    @Test
    fun resetLayout_restoresDefaultPositions() {
        val scene = SpatialScene(listOf(SpatialElement("a", ElementKind.PANEL, 100f, 100f, 0.2f, 0.3f)))
        scene.setViewport(1000f, 1000f)
        val a = scene.element("a")!!
        scene.moveTo(a, 700f, 700f)

        scene.perform(ButtonAction.RESET_LAYOUT)

        assertEquals(200f, a.left, 0.01f)
        assertEquals(300f, a.top, 0.01f)
    }

    @Test
    fun resetLayout_doesNotMoveElementHeldByAHand() {
        val scene = SpatialScene(listOf(SpatialElement("a", ElementKind.PANEL, 100f, 100f, 0.2f, 0.3f)))
        scene.setViewport(1000f, 1000f)
        val a = scene.element("a")!!
        scene.moveTo(a, 700f, 700f)
        a.heldBy = com.handspace.tracking.Handedness.LEFT

        scene.perform(ButtonAction.RESET_LAYOUT)

        assertEquals(700f, a.left, 0.01f)
        assertEquals(700f, a.top, 0.01f)
    }

    @Test
    fun colorCycle_wrapsAroundAllColors() {
        val scene = SpatialScene(emptyList())
        val seen = mutableListOf<Int>()
        repeat(SpatialScene.OBJECT_COLOR_COUNT) {
            scene.perform(ButtonAction.CYCLE_OBJECT_COLOR)
            seen.add(scene.colorIndex)
        }
        assertEquals(listOf(1, 2, 3, 0), seen)
    }

    @Test
    fun press_recordsTimestamp_andRunsButtonAction() {
        val button = SpatialElement("b", ElementKind.BUTTON, 100f, 50f, 0f, 0f, ButtonAction.CYCLE_OBJECT_COLOR)
        val scene = SpatialScene(listOf(button))
        scene.setViewport(1000f, 1000f)

        scene.press(button, 123L)

        assertEquals(123L, button.pressedAtMs)
        assertEquals(1, scene.colorIndex)
    }

    @Test
    fun completeDrag_incrementsCounter() {
        val scene = SpatialScene(emptyList())
        scene.completeDrag()
        scene.completeDrag()
        assertEquals(2, scene.dragCount)
    }

    @Test
    fun demoLayout_elementsStayInsideAPhoneSizedViewport() {
        val scene = DemoLayout.create(2.625f)
        scene.setViewport(1080f, 2340f)
        for (e in scene.elementsInDrawOrder) {
            assertTrue("${e.id} sai da tela pela esquerda", e.left >= 0f)
            assertTrue("${e.id} sai da tela pela direita", e.right <= 1080f)
            assertTrue("${e.id} sai da tela por cima", e.top >= 0f)
            assertTrue("${e.id} sai da tela por baixo", e.bottom <= 2340f)
        }
    }
}
