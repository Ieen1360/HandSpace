package com.handspace.interaction

import com.handspace.tracking.ScreenPoint

/**
 * Suavização exponencial do cursor: `novo = anterior + (alvo - anterior) * fator`.
 * Fator alto segue o dedo rápido e treme mais; fator baixo é suave e atrasa mais.
 */
class CursorSmoother(private val smoothingFactor: Float = DEFAULT_FACTOR) {

    init {
        require(smoothingFactor > 0f && smoothingFactor <= 1f) { "smoothingFactor deve estar em (0, 1]" }
    }

    private var x = Float.NaN
    private var y = Float.NaN

    fun update(targetX: Float, targetY: Float): ScreenPoint {
        if (x.isNaN() || y.isNaN()) {
            x = targetX
            y = targetY
        } else {
            x += (targetX - x) * smoothingFactor
            y += (targetY - y) * smoothingFactor
        }
        return ScreenPoint(x, y)
    }

    fun reset() {
        x = Float.NaN
        y = Float.NaN
    }

    companion object {
        const val DEFAULT_FACTOR = 0.35f
    }
}
