package com.handspace.ar

/** Estados da sessão ARCore, do ponto de vista do aplicativo. */
enum class ArLifecycleState {
    IDLE,
    CHECKING,
    INSTALL_REQUIRED,
    INSTALL_COMPLETED,
    UNSUPPORTED,
    READY,
    RESUMED,
    PAUSED,
    CLOSED,
    ERROR,
}

/**
 * Máquina de estados da sessão ARCore. Cada método retorna true somente se a transição foi aceita.
 * Transições fora de ordem são rejeitadas e não alteram o estado.
 *
 * Fluxo de instalação: INSTALL_REQUIRED → (instalação concluída) → INSTALL_COMPLETED → nova verificação.
 * Fluxo transitório: resposta UNKNOWN_CHECKING mantém CHECKING e pede nova tentativa, até [maxTransientAttempts].
 */
class ArSessionLifecycle(private val maxTransientAttempts: Int = DEFAULT_MAX_TRANSIENT_ATTEMPTS) {

    init {
        require(maxTransientAttempts >= 1) { "maxTransientAttempts deve ser >= 1" }
    }

    var state: ArLifecycleState = ArLifecycleState.IDLE
        private set

    var lastError: String? = null
        private set

    /** Número de tentativas feitas na verificação atual. */
    var checkAttempts: Int = 0
        private set

    /** Verdadeiro se a máquina quer uma nova tentativa de verificação (resposta transitória). */
    val needsRetry: Boolean get() = state == ArLifecycleState.CHECKING

    /** Pode iniciar uma nova verificação a partir do estado atual. */
    fun canBeginCheck(): Boolean = state in CHECK_START_STATES

    /** Inicia uma verificação. Aceito de IDLE, CLOSED, ERROR e INSTALL_COMPLETED. */
    fun beginCheck(): Boolean {
        if (!canBeginCheck()) return false
        state = ArLifecycleState.CHECKING
        checkAttempts = 1
        lastError = null
        return true
    }

    /** Aplica o resultado de uma tentativa de verificação. Só vale durante CHECKING. */
    fun onSupportResolved(support: ArSupport): Boolean {
        if (state != ArLifecycleState.CHECKING) return false
        when (support) {
            ArSupport.SUPPORTED -> state = ArLifecycleState.READY
            ArSupport.NEEDS_INSTALL, ArSupport.NEEDS_UPDATE -> state = ArLifecycleState.INSTALL_REQUIRED
            ArSupport.UNSUPPORTED_DEVICE -> state = ArLifecycleState.UNSUPPORTED
            ArSupport.UNKNOWN_CHECKING -> {
                if (checkAttempts < maxTransientAttempts) {
                    checkAttempts++
                    // Continua em CHECKING: o chamador deve repetir a tentativa.
                } else {
                    fail("O ARCore não concluiu a verificação após $checkAttempts tentativas. Tente novamente.")
                }
            }
            ArSupport.UNKNOWN_ERROR -> fail("Erro desconhecido ao verificar o suporte ao ARCore.")
            ArSupport.UNKNOWN_TIMED_OUT -> fail("Tempo esgotado ao verificar o suporte ao ARCore.")
        }
        return true
    }

    /** Instalação concluída. Só vale durante INSTALL_REQUIRED. Depois disso, é preciso verificar de novo. */
    fun onInstallCompleted(): Boolean {
        if (state != ArLifecycleState.INSTALL_REQUIRED) return false
        state = ArLifecycleState.INSTALL_COMPLETED
        lastError = null
        return true
    }

    /** Pode retomar a sessão? Só a partir de READY ou PAUSED. */
    fun canResume(): Boolean = state == ArLifecycleState.READY || state == ArLifecycleState.PAUSED

    /** A sessão foi retomada com sucesso. Só deve ser chamado depois de Session.resume() funcionar. */
    fun onResumeSucceeded(): Boolean {
        if (!canResume()) return false
        state = ArLifecycleState.RESUMED
        lastError = null
        return true
    }

    /** Session.resume() falhou. Vai para ERROR, porque a sessão não está utilizável. */
    fun onResumeFailed(message: String): Boolean {
        if (!canResume()) return false
        fail(message)
        return true
    }

    /** Sessão pausada. Só vale durante RESUMED. */
    fun onPaused(): Boolean {
        if (state != ArLifecycleState.RESUMED) return false
        state = ArLifecycleState.PAUSED
        return true
    }

    /** Sessão encerrada. Aceito em qualquer estado. */
    fun onClosed(): Boolean {
        state = ArLifecycleState.CLOSED
        return true
    }

    /** Falha em qualquer etapa. Registra a mensagem e vai para ERROR. */
    fun onFailed(message: String): Boolean {
        fail(message)
        return true
    }

    private fun fail(message: String) {
        lastError = message
        state = ArLifecycleState.ERROR
    }

    companion object {
        const val DEFAULT_MAX_TRANSIENT_ATTEMPTS = 3

        private val CHECK_START_STATES = setOf(
            ArLifecycleState.IDLE,
            ArLifecycleState.CLOSED,
            ArLifecycleState.ERROR,
            ArLifecycleState.INSTALL_COMPLETED,
        )
    }
}
