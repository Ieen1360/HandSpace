package com.handspace.tracking

import kotlin.math.max

/** Ponto em coordenadas de tela (pixels da view). */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Converte landmarks normalizados da imagem analisada para pixels da view.
 * Assume o mesmo comportamento de PreviewView.ScaleType.FILL_CENTER: a imagem cobre a view
 * mantendo a proporção e o excedente é cortado igualmente dos dois lados.
 */
class ViewTransform private constructor(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val scale: Float,
    private val dx: Float,
    private val dy: Float,
) {
    fun map(landmark: Landmark): ScreenPoint = ScreenPoint(
        x = landmark.x * imageWidth * scale + dx,
        y = landmark.y * imageHeight * scale + dy,
    )

    companion object {
        fun fillCenter(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): ViewTransform {
            val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
            val dx = (viewWidth - imageWidth * scale) / 2f
            val dy = (viewHeight - imageHeight * scale) / 2f
            return ViewTransform(imageWidth, imageHeight, scale, dx, dy)
        }
    }
}
