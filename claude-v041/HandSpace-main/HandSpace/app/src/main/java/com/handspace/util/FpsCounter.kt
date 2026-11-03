package com.handspace.util

/** Mede a taxa de eventos por segundo em janelas de [windowMs]. */
class FpsCounter(private val windowMs: Long = 1000L) {

    private var windowStart = 0L
    private var count = 0

    var fps: Float = 0f
        private set

    fun tick(nowMs: Long) {
        if (windowStart == 0L) windowStart = nowMs
        count++
        val elapsed = nowMs - windowStart
        if (elapsed >= windowMs) {
            fps = count * 1000f / elapsed
            count = 0
            windowStart = nowMs
        }
    }
}
