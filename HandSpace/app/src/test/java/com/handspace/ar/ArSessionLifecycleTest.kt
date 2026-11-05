package com.handspace.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArSessionLifecycleTest {

    private fun checking(max: Int = 3) = ArSessionLifecycle(max).also { it.beginCheck() }

    private fun ready(): ArSessionLifecycle {
        val m = checking()
        m.onSupportResolved(ArSupport.SUPPORTED)
        return m
    }

    // ---- ciclo completo ----

    @Test
    fun supportedDevice_fullCycle_readyResumedPausedClosed() {
        val m = ready()
        assertEquals(ArLifecycleState.READY, m.state)
        assertTrue(m.onResumeSucceeded())
        assertEquals(ArLifecycleState.RESUMED, m.state)
        assertTrue(m.onPaused())
        assertEquals(ArLifecycleState.PAUSED, m.state)
        assertTrue(m.onResumeSucceeded())
        assertTrue(m.onClosed())
        assertEquals(ArLifecycleState.CLOSED, m.state)
    }

    // ---- fluxo de instalação com nova verificação real ----

    @Test
    fun installFlow_installCompleted_allowsNewRealCheck_andReachesReady() {
        val m = checking()
        m.onSupportResolved(ArSupport.NEEDS_INSTALL)
        assertEquals(ArLifecycleState.INSTALL_REQUIRED, m.state)
        assertFalse("sem instalação concluída não há nova verificação", m.canBeginCheck())

        assertTrue(m.onInstallCompleted())
        assertEquals(ArLifecycleState.INSTALL_COMPLETED, m.state)
        assertTrue("após a instalação, uma nova verificação deve ser aceita", m.beginCheck())
        assertEquals(ArLifecycleState.CHECKING, m.state)

        m.onSupportResolved(ArSupport.SUPPORTED)
        assertEquals(ArLifecycleState.READY, m.state)
    }

    @Test
    fun installFlow_needsUpdate_behavesLikeNeedsInstall() {
        val m = checking()
        m.onSupportResolved(ArSupport.NEEDS_UPDATE)
        assertEquals(ArLifecycleState.INSTALL_REQUIRED, m.state)
        assertTrue(m.onInstallCompleted())
        assertTrue(m.beginCheck())
    }

    @Test
    fun installCompleted_rejectedOutsideInstallRequired() {
        val m = ready()
        assertFalse(m.onInstallCompleted())
        assertEquals(ArLifecycleState.READY, m.state)
    }

    @Test
    fun installCompleted_thenCheckReturnsUnsupported_isRespected() {
        val m = checking()
        m.onSupportResolved(ArSupport.NEEDS_INSTALL)
        m.onInstallCompleted()
        m.beginCheck()
        m.onSupportResolved(ArSupport.UNSUPPORTED_DEVICE)
        assertEquals(ArLifecycleState.UNSUPPORTED, m.state)
        assertFalse("dispositivo não suportado não reinicia verificação", m.canBeginCheck())
    }

    // ---- respostas transitórias e erros separados ----

    @Test
    fun unknownChecking_keepsCheckingAndRequestsRetry() {
        val m = checking(max = 3)
        m.onSupportResolved(ArSupport.UNKNOWN_CHECKING)
        assertEquals(ArLifecycleState.CHECKING, m.state)
        assertTrue(m.needsRetry)
        assertEquals(2, m.checkAttempts)
        assertNull("transitório não deve registrar erro", m.lastError)
    }

    @Test
    fun unknownChecking_successAfterRetry_reachesReady() {
        val m = checking(max = 3)
        m.onSupportResolved(ArSupport.UNKNOWN_CHECKING)
        m.onSupportResolved(ArSupport.UNKNOWN_CHECKING)
        m.onSupportResolved(ArSupport.SUPPORTED)
        assertEquals(ArLifecycleState.READY, m.state)
        assertFalse(m.needsRetry)
    }

    @Test
    fun unknownChecking_exhaustsAttempts_thenError() {
        val m = checking(max = 3)
        m.onSupportResolved(ArSupport.UNKNOWN_CHECKING)
        m.onSupportResolved(ArSupport.UNKNOWN_CHECKING)
        m.onSupportResolved(ArSupport.UNKNOWN_CHECKING)
        assertEquals(ArLifecycleState.ERROR, m.state)
        assertNotNull(m.lastError)
        assertTrue(m.lastError!!.contains("3 tentativas"))
    }

    @Test
    fun unknownError_andTimedOut_areSeparateErrorsWithDistinctMessages() {
        val error = checking()
        error.onSupportResolved(ArSupport.UNKNOWN_ERROR)
        val timeout = checking()
        timeout.onSupportResolved(ArSupport.UNKNOWN_TIMED_OUT)

        assertEquals(ArLifecycleState.ERROR, error.state)
        assertEquals(ArLifecycleState.ERROR, timeout.state)
        assertNotEquals(error.lastError, timeout.lastError)
        assertTrue(error.lastError!!.contains("Erro"))
        assertTrue(timeout.lastError!!.contains("Tempo"))
    }

    @Test
    fun errorState_canStartNewCheck() {
        val m = checking()
        m.onSupportResolved(ArSupport.UNKNOWN_ERROR)
        assertTrue(m.beginCheck())
        assertNull(m.lastError)
        assertEquals(1, m.checkAttempts)
    }

    // ---- resume ----

    @Test
    fun resumeFailure_movesToErrorAndCannotResume_untilRecheck() {
        val m = ready()
        assertTrue(m.onResumeFailed("câmera ocupada"))
        assertEquals(ArLifecycleState.ERROR, m.state)
        assertEquals("câmera ocupada", m.lastError)
        assertFalse(m.canResume())
    }

    @Test
    fun resumeSucceeded_isRejectedFromErrorState() {
        val m = checking()
        m.onSupportResolved(ArSupport.UNKNOWN_ERROR)
        assertFalse(m.onResumeSucceeded())
        assertEquals(ArLifecycleState.ERROR, m.state)
    }

    // ---- transições inválidas ----

    @Test
    fun invalidTransitions_areRejectedAndKeepState() {
        val idle = ArSessionLifecycle()
        assertFalse(idle.onSupportResolved(ArSupport.SUPPORTED))
        assertFalse(idle.onInstallCompleted())
        assertFalse(idle.onResumeSucceeded())
        assertFalse(idle.onPaused())
        assertEquals(ArLifecycleState.IDLE, idle.state)

        val r = ready()
        assertFalse("não se inicia verificação a partir de READY", r.beginCheck())
        assertFalse(r.onPaused())
        assertFalse(r.onInstallCompleted())
        assertEquals(ArLifecycleState.READY, r.state)
    }

    @Test
    fun checkingState_rejectsResumeAndPause() {
        val m = checking()
        assertFalse(m.onResumeSucceeded())
        assertFalse(m.onPaused())
        assertEquals(ArLifecycleState.CHECKING, m.state)
    }

    @Test
    fun resultsArrivingOutOfOrder_areIgnored() {
        val m = ArSessionLifecycle()
        assertFalse("resultado sem verificação em andamento", m.onSupportResolved(ArSupport.SUPPORTED))
        assertEquals(ArLifecycleState.IDLE, m.state)
    }

    @Test
    fun pauseOnlyAfterResumed() {
        val m = ready()
        assertFalse(m.onPaused())
        m.onResumeSucceeded()
        assertTrue(m.onPaused())
    }

    @Test
    fun closeIsAlwaysAccepted_andAllowsNewCheck() {
        val m = ready()
        m.onResumeSucceeded()
        assertTrue(m.onClosed())
        assertEquals(ArLifecycleState.CLOSED, m.state)
        assertTrue(m.beginCheck())
    }

    @Test
    fun failureFromAnyStateRecordsMessage() {
        val m = ready()
        m.onResumeSucceeded()
        assertTrue(m.onFailed("falha"))
        assertEquals(ArLifecycleState.ERROR, m.state)
        assertEquals("falha", m.lastError)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroAttemptsIsRejected() {
        ArSessionLifecycle(maxTransientAttempts = 0)
    }
}
