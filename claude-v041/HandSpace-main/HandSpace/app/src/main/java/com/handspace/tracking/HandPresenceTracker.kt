package com.handspace.tracking

/**
 * Detecta quando alguma mão entra ou sai do campo de visão.
 * Emite apenas nas transições, nunca a cada frame.
 */
class HandPresenceTracker {

    private var handsVisible = false

    /** Retorna o evento de transição deste frame, ou null se o estado não mudou. */
    fun update(frame: HandFrame): PresenceEvent? {
        val visibleNow = frame.hands.isNotEmpty()
        if (visibleNow == handsVisible) return null
        handsVisible = visibleNow
        return if (visibleNow) PresenceEvent.ENTERED else PresenceEvent.EXITED
    }
}
