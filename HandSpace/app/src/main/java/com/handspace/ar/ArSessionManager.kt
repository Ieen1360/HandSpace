package com.handspace.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException

/**
 * Único ponto do projeto que chama o SDK do ARCore. Mantém a sessão e a máquina de estados.
 *
 * Etapa 1: verificação de suporte (com novas tentativas para respostas transitórias), instalação,
 * criação da sessão e ciclo de vida (resume/pause/close). Sem renderização nem detecção de planos.
 *
 * Quem chama é responsável por:
 * - liberar a câmera do CameraX antes de [resume];
 * - repetir [checkSupport] enquanto [needsRetry] for true, com um intervalo entre as tentativas;
 * - chamar [checkSupport] de novo depois de [requestInstall] retornar COMPLETED.
 */
class ArSessionManager(private val activity: Activity) {

    private val lifecycle = ArSessionLifecycle()
    private var session: Session? = null

    /** Mensagem de erro que não altera o estado (por exemplo, permissão ausente). */
    var lastMessage: String? = null
        private set

    val state: ArLifecycleState get() = lifecycle.state
    val lastError: String? get() = lifecycle.lastError
    val needsRetry: Boolean get() = lifecycle.needsRetry

    /**
     * Uma tentativa de verificação de suporte. Se [needsRetry] for true depois da chamada, o chamador
     * deve repetir a verificação. Em estados já verificados, não chama o SDK.
     */
    fun checkSupport(): ArSupport {
        when (lifecycle.state) {
            ArLifecycleState.READY, ArLifecycleState.RESUMED, ArLifecycleState.PAUSED -> return ArSupport.SUPPORTED
            ArLifecycleState.UNSUPPORTED -> return ArSupport.UNSUPPORTED_DEVICE
            else -> Unit
        }
        if (lifecycle.state != ArLifecycleState.CHECKING && !lifecycle.beginCheck()) {
            lastMessage = "Não é possível verificar o ARCore neste estado: ${lifecycle.state}."
            return ArSupport.UNKNOWN_ERROR
        }
        val support = try {
            ArCoreApk.getInstance().checkAvailability(activity).toSupport()
        } catch (e: Exception) {
            lifecycle.onFailed("Falha ao verificar o ARCore: ${e.message}")
            return ArSupport.UNKNOWN_ERROR
        }
        lifecycle.onSupportResolved(support)
        return support
    }

    /**
     * Pede ao usuário a instalação ou atualização dos serviços do ARCore.
     * Retorna COMPLETED somente se o SDK confirmar a instalação E a máquina aceitar a transição.
     */
    fun requestInstall(userRequested: Boolean): ArInstallOutcome {
        if (lifecycle.state != ArLifecycleState.INSTALL_REQUIRED) return ArInstallOutcome.NOT_REQUIRED
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequested)) {
                ArCoreApk.InstallStatus.INSTALLED ->
                    if (lifecycle.onInstallCompleted()) ArInstallOutcome.COMPLETED else ArInstallOutcome.FAILED
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArInstallOutcome.PENDING
                else -> ArInstallOutcome.PENDING
            }
        } catch (e: UnavailableException) {
            lifecycle.onFailed("Não foi possível instalar o ARCore: ${e.message}")
            ArInstallOutcome.FAILED
        } catch (e: Exception) {
            lifecycle.onFailed("Erro ao instalar o ARCore: ${e.message}")
            ArInstallOutcome.FAILED
        }
    }

    /**
     * Cria a sessão. Exige estado READY e permissão de câmera. Se faltar a permissão, o estado não muda:
     * o chamador deve pedir a permissão e tentar de novo. Falhas do SDK vão para ERROR.
     */
    fun createSession(): Boolean {
        if (lifecycle.state != ArLifecycleState.READY) {
            lastMessage = "A sessão só pode ser criada depois da verificação de suporte."
            return false
        }
        if (session != null) return true
        if (!cameraPermissionGranted()) {
            lastMessage = "Permissão de câmera necessária para o modo AR."
            return false
        }
        return try {
            session = Session(activity)
            lastMessage = null
            true
        } catch (e: SecurityException) {
            lifecycle.onFailed("Permissão negada ao criar a sessão do ARCore: ${e.message}")
            false
        } catch (e: UnavailableException) {
            lifecycle.onFailed("Não foi possível criar a sessão do ARCore: ${e.message}")
            false
        } catch (e: Exception) {
            lifecycle.onFailed("Erro inesperado ao criar a sessão do ARCore: ${e.message}")
            false
        }
    }

    /**
     * Retoma a sessão e abre a câmera traseira para o ARCore. O estado RESUMED só é aplicado depois que
     * Session.resume() retorna sem exceção. Em caso de falha, a sessão é encerrada e o estado vai para ERROR.
     */
    fun resume(): Boolean {
        val s = session
        if (s == null) {
            lastMessage = "A sessão do ARCore ainda não foi criada."
            return false
        }
        if (!lifecycle.canResume()) {
            lastMessage = "A sessão não pode ser retomada no estado ${lifecycle.state}."
            return false
        }
        if (!cameraPermissionGranted()) {
            lastMessage = "Permissão de câmera necessária para o modo AR."
            return false
        }
        return try {
            s.resume()
            lifecycle.onResumeSucceeded()
        } catch (e: CameraNotAvailableException) {
            failResume("A câmera está em uso por outro aplicativo: ${e.message}")
        } catch (e: SecurityException) {
            failResume("Permissão negada ao retomar o ARCore: ${e.message}")
        } catch (e: Exception) {
            failResume("Erro ao retomar o ARCore: ${e.message}")
        }
    }

    /** Pausa a sessão e libera a câmera. Só age se a sessão estiver RESUMED. */
    fun pause() {
        val s = session ?: return
        if (!lifecycle.onPaused()) return
        try {
            s.pause()
        } catch (e: Exception) {
            lifecycle.onFailed("Erro ao pausar o ARCore: ${e.message}")
            closeSessionQuietly()
        }
    }

    /** Encerra a sessão. Seguro chamar em qualquer estado e várias vezes. */
    fun close() {
        closeSessionQuietly()
        lifecycle.onClosed()
    }

    private fun failResume(message: String): Boolean {
        lifecycle.onResumeFailed(message)
        closeSessionQuietly()
        return false
    }

    private fun closeSessionQuietly() {
        val s = session ?: return
        session = null
        try {
            if (lifecycle.state == ArLifecycleState.RESUMED) s.pause()
        } catch (e: Exception) {
            // Encerrando de qualquer forma: falha de pausa não impede o close.
        }
        try {
            s.close()
        } catch (e: Exception) {
            // Sessão já inválida: nada a fazer.
        }
    }

    private fun cameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun ArCoreApk.Availability.toSupport(): ArSupport = when (this) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupport.SUPPORTED
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArSupport.NEEDS_INSTALL
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArSupport.NEEDS_UPDATE
        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArSupport.UNSUPPORTED_DEVICE
        ArCoreApk.Availability.UNKNOWN_CHECKING -> ArSupport.UNKNOWN_CHECKING
        ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> ArSupport.UNKNOWN_TIMED_OUT
        ArCoreApk.Availability.UNKNOWN_ERROR -> ArSupport.UNKNOWN_ERROR
        else -> ArSupport.UNKNOWN_ERROR
    }
}
