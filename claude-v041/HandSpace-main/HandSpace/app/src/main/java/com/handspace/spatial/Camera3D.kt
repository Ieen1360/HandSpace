package com.handspace.spatial

import com.handspace.tracking.ScreenPoint
import kotlin.math.tan

/**
 * Câmera virtual de projeção em perspectiva, com origem na câmera física.
 * Usada tanto para desenhar objetos e mãos quanto para lançar raios a partir dos dedos,
 * para que desenho e seleção usem exatamente a mesma geometria.
 *
 * Não é ancorada ao ambiente: é apenas um modelo de projeção sobre o preview.
 */
class Camera3D(
    val viewWidth: Float,
    val viewHeight: Float,
    verticalFovDegrees: Float = DEFAULT_FOV_DEG,
) {
    init {
        require(viewWidth > 0f && viewHeight > 0f) { "dimensões da view devem ser positivas" }
        require(verticalFovDegrees in 10f..150f) { "campo de visão fora do intervalo" }
    }

    /** Distância focal em pixels. */
    val focalPx: Float =
        (viewHeight / 2f) / tan(Math.toRadians(verticalFovDegrees.toDouble() / 2.0)).toFloat()

    private val cx = viewWidth / 2f
    private val cy = viewHeight / 2f

    /** Projeta um ponto 3D em pixels da view, ou null se estiver atrás do plano de corte. */
    fun project(p: Vec3): ScreenPoint? {
        if (p.z < NEAR_M) return null
        return ScreenPoint(cx + focalPx * p.x / p.z, cy - focalPx * p.y / p.z)
    }

    /**
     * Raio que sai da câmera e passa pelo pixel (sx, sy). A direção tem z = 1, então o parâmetro t
     * do raio é a profundidade: `pointAt(z)` está à profundidade z, na mesma convenção de [unprojectAtDepth].
     */
    fun unproject(sx: Float, sy: Float): Ray {
        val dir = Vec3((sx - cx) / focalPx, -(sy - cy) / focalPx, 1f)
        return Ray(Vec3.ZERO, dir)
    }

    /** Ponto 3D que aparece no pixel (sx, sy) quando está à profundidade [z]. */
    fun unprojectAtDepth(sx: Float, sy: Float, z: Float): Vec3 =
        Vec3((sx - cx) * z / focalPx, -(sy - cy) * z / focalPx, z)

    /** Pixels por metro na profundidade [z]. */
    fun pixelsPerMeter(z: Float): Float = focalPx / z

    companion object {
        const val DEFAULT_FOV_DEG = 60f
        const val NEAR_M = 0.05f
    }
}
