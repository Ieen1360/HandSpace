package com.handspace.interaction

/** Impede ativações consecutivas em intervalo menor que [minIntervalMs]. */
class ActivationCooldown(private val minIntervalMs: Long = DEFAULT_INTERVAL_MS) {

    init {
        require(minIntervalMs >= 0L) { "minIntervalMs não pode ser negativo" }
    }

    private var lastFiredMs: Long? = null

    /** Retorna true e registra o disparo se o intervalo já passou desde o último. */
    fun tryFire(nowMs: Long): Boolean {
        val last = lastFiredMs
        if (last != null && nowMs - last < minIntervalMs) return false
        lastFiredMs = nowMs
        return true
    }

    fun reset() {
        lastFiredMs = null
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 400L
    }
}
