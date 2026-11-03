package com.handspace.spatial

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Vetor 3D. Eixos: x à direita, y para cima, z para frente (afastando-se da câmera). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    fun length(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val len = length()
        return if (len < SpatialGeometry.EPSILON) ZERO else this * (1f / len)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

/** Raio com origem e direção. A direção não precisa ser unitária: o parâmetro t é o que as interseções devolvem. */
data class Ray(val origin: Vec3, val direction: Vec3) {
    /** Ponto ao longo do raio a uma distância [t] da origem. */
    fun pointAt(t: Float): Vec3 = origin + direction * t
}

/**
 * Painel retangular no espaço. [right] e [up] devem ser unitários e perpendiculares entre si.
 * [halfWidth] e [halfHeight] são metades das dimensões, na mesma unidade do espaço (metros).
 */
data class Panel3D(
    val center: Vec3,
    val right: Vec3,
    val up: Vec3,
    val halfWidth: Float,
    val halfHeight: Float,
) {
    val normal: Vec3 get() = right.cross(up).normalized()
}

/** Impacto do raio em um painel: distância ao longo do raio e coordenadas locais (u, v). */
data class PanelHit(val distance: Float, val u: Float, val v: Float)

/** Geometria pura para seleção por raio, distância e escala. Não depende de Android nem de ARCore. */
object SpatialGeometry {

    internal const val EPSILON = 1e-6f

    /** Distância t > 0 ao longo do raio até o plano, ou null se o raio for paralelo ou o plano estiver atrás. */
    fun intersectPlane(ray: Ray, planePoint: Vec3, planeNormal: Vec3): Float? {
        val denom = planeNormal.dot(ray.direction)
        if (abs(denom) < EPSILON) return null
        val t = (planePoint - ray.origin).dot(planeNormal) / denom
        return if (t > 0f) t else null
    }

    /** Impacto no painel, ou null se o raio não atinge a área retangular. */
    fun hitPanel(ray: Ray, panel: Panel3D): PanelHit? {
        val t = intersectPlane(ray, panel.center, panel.normal) ?: return null
        val offset = ray.pointAt(t) - panel.center
        val u = offset.dot(panel.right)
        val v = offset.dot(panel.up)
        if (abs(u) > panel.halfWidth || abs(v) > panel.halfHeight) return null
        return PanelHit(distance = t, u = u, v = v)
    }

    /**
     * Menor t > 0 da interseção do raio com a esfera, ou null. Vale para qualquer comprimento de direção.
     * Se a origem estiver dentro da esfera, retorna a saída (t positivo).
     */
    fun intersectSphere(ray: Ray, center: Vec3, radius: Float): Float? {
        if (radius <= 0f) return null
        val a = ray.direction.dot(ray.direction)
        if (a < EPSILON) return null
        val oc = ray.origin - center
        val b = oc.dot(ray.direction)
        val c = oc.dot(oc) - radius * radius
        val disc = b * b - a * c
        if (disc < 0f) return null
        val s = sqrt(disc)
        val near = (-b - s) / a
        val far = (-b + s) / a
        return when {
            near > 0f -> near
            far > 0f -> far
            else -> null
        }
    }

    /** Item cujo painel é atingido mais perto da origem do raio, ou null se nenhum for atingido. */
    fun <T> pickNearest(ray: Ray, items: List<T>, panelOf: (T) -> Panel3D): T? {
        var best: T? = null
        var bestDistance = Float.POSITIVE_INFINITY
        for (item in items) {
            val hit = hitPanel(ray, panelOf(item)) ?: continue
            if (hit.distance < bestDistance) {
                bestDistance = hit.distance
                best = item
            }
        }
        return best
    }

    /**
     * Escala proporcional à variação da distância entre as duas mãos (afastar/aproximar).
     * Limitada a [minScale]..[maxScale]. Com distância base zero, mantém a escala base.
     */
    fun scaleFromSpread(
        baseScale: Float,
        baseSpread: Float,
        currentSpread: Float,
        minScale: Float,
        maxScale: Float,
    ): Float {
        require(minScale > 0f && minScale <= maxScale) { "limites de escala inválidos" }
        if (baseSpread < EPSILON) return baseScale.coerceIn(minScale, maxScale)
        val scaled = baseScale * (currentSpread / baseSpread)
        return max(minScale, min(maxScale, scaled))
    }
}
