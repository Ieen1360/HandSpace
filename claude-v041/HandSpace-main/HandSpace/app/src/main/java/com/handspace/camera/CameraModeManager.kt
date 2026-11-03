package com.handspace.camera

enum class CameraMode { FRONT_TRACKING, REAR_AR }

/** Ação que a camada de câmera deve executar. */
sealed class CameraCommand {
    data class Open(val mode: CameraMode) : CameraCommand()
    object Release : CameraCommand()
    object None : CameraCommand()
}

/**
 * Máquina de estados que garante no máximo uma câmera aberta por vez.
 *
 * Toda troca de modo segue a mesma ordem: liberar a câmera atual, confirmar a liberação e só então
 * abrir a nova. Não toca em hardware; quem executa os comandos é a camada de CameraX.
 * As confirmações (onCameraStarted, onCameraReleased, onCameraFailed) devem vir da thread principal.
 */
class CameraModeManager(initialMode: CameraMode = CameraMode.FRONT_TRACKING) {

    enum class State { STOPPED, STARTING, ACTIVE, RELEASING }

    var state: State = State.STOPPED
        private set

    /** Modo da câmera aberta ou em abertura. */
    var mode: CameraMode = initialMode
        private set

    /** Erro da última tentativa de abertura, ou null. */
    var lastError: String? = null
        private set

    private var pending: CameraMode? = null
    private var stopRequested = false

    /** Modo aberto e pronto para uso, ou null. */
    val activeMode: CameraMode?
        get() = if (state == State.ACTIVE) mode else null

    fun requestMode(target: CameraMode): CameraCommand {
        stopRequested = false
        return when (state) {
            State.STOPPED -> {
                mode = target
                state = State.STARTING
                CameraCommand.Open(target)
            }
            State.ACTIVE -> if (target == mode) {
                CameraCommand.None
            } else {
                beginRelease(target)
            }
            // Não é possível cancelar uma abertura ou liberação em andamento: o pedido fica pendente.
            State.STARTING, State.RELEASING -> {
                pending = target
                CameraCommand.None
            }
        }
    }

    fun stop(): CameraCommand {
        stopRequested = true
        pending = null
        return if (state == State.ACTIVE) beginRelease(null) else CameraCommand.None
    }

    fun onCameraStarted(): CameraCommand {
        if (state != State.STARTING) return CameraCommand.None
        state = State.ACTIVE
        lastError = null
        if (stopRequested) return beginRelease(null)
        val next = pending ?: return CameraCommand.None
        pending = null
        return if (next == mode) CameraCommand.None else beginRelease(next)
    }

    fun onCameraReleased(): CameraCommand {
        if (state != State.RELEASING) return CameraCommand.None
        state = State.STOPPED
        if (stopRequested) {
            stopRequested = false
            pending = null
            return CameraCommand.None
        }
        val next = pending ?: return CameraCommand.None
        pending = null
        mode = next
        state = State.STARTING
        return CameraCommand.Open(next)
    }

    /** Falha ao abrir ou liberar. A câmera é considerada fechada; o próximo requestMode tenta de novo. */
    fun onCameraFailed(message: String): CameraCommand {
        lastError = message
        state = State.STOPPED
        pending = null
        stopRequested = false
        return CameraCommand.None
    }

    private fun beginRelease(next: CameraMode?): CameraCommand {
        pending = next
        state = State.RELEASING
        return CameraCommand.Release
    }
}
