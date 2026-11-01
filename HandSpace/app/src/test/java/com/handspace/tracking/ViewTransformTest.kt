package com.handspace.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewTransformTest {

    @Test
    fun fillCenter_coversViewAndCentersOverflow() {
        // Imagem 640x480 em view 1080x1920: escala = max(1.6875, 4) = 4, excedente horizontal de 2560 - 1080.
        val transform = ViewTransform.fillCenter(640, 480, 1080f, 1920f)

        val center = transform.map(Landmark(0.5f, 0.5f, 0f))
        assertEquals(540f, center.x, 0.01f)
        assertEquals(960f, center.y, 0.01f)

        val topLeft = transform.map(Landmark(0f, 0f, 0f))
        assertEquals(-740f, topLeft.x, 0.01f)
        assertEquals(0f, topLeft.y, 0.01f)
    }

    @Test
    fun fillCenter_sameAspectRatio_mapsDirectly() {
        val transform = ViewTransform.fillCenter(640, 480, 640f, 480f)
        val p = transform.map(Landmark(0.25f, 0.5f, 0f))
        assertEquals(160f, p.x, 0.01f)
        assertEquals(240f, p.y, 0.01f)
    }
}
