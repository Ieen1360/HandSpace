package com.handspace.camera

import com.handspace.camera.CameraCommand.None
import com.handspace.camera.CameraCommand.Open
import com.handspace.camera.CameraCommand.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraModeManagerTest {

    @Test
    fun firstRequest_opensCamera_andBecomesActiveWhenStarted() {
        val m = CameraModeManager()
        assertEquals(Open(CameraMode.FRONT_TRACKING), m.requestMode(CameraMode.FRONT_TRACKING))
        assertNull(m.activeMode)
        assertEquals(None, m.onCameraStarted())
        assertEquals(CameraMode.FRONT_TRACKING, m.activeMode)
    }

    @Test
    fun switchingMode_releasesBeforeOpeningNewCamera() {
        val m = activeFront()
        assertEquals(Release, m.requestMode(CameraMode.REAR_AR))
        assertNull("nenhuma câmera deve aparecer como ativa durante a troca", m.activeMode)
        assertEquals(Open(CameraMode.REAR_AR), m.onCameraReleased())
        assertEquals(None, m.onCameraStarted())
        assertEquals(CameraMode.REAR_AR, m.activeMode)
    }

    @Test
    fun requestDuringRelease_isDeferred_andNeverOpensTwoCameras() {
        val m = activeFront()
        assertEquals(Release, m.requestMode(CameraMode.REAR_AR))
        assertEquals(None, m.requestMode(CameraMode.FRONT_TRACKING))
        assertEquals(None, m.requestMode(CameraMode.REAR_AR))
        assertEquals(Open(CameraMode.REAR_AR), m.onCameraReleased())
    }

    @Test
    fun requestDuringStart_isQueued_andAppliedAfterStartCompletes() {
        val m = CameraModeManager()
        m.requestMode(CameraMode.FRONT_TRACKING)
        assertEquals(None, m.requestMode(CameraMode.REAR_AR))
        assertEquals(Release, m.onCameraStarted())
        assertEquals(Open(CameraMode.REAR_AR), m.onCameraReleased())
    }

    @Test
    fun requestSameMode_whileActive_isNoOp() {
        val m = activeFront()
        assertEquals(None, m.requestMode(CameraMode.FRONT_TRACKING))
        assertEquals(CameraMode.FRONT_TRACKING, m.activeMode)
    }

    @Test
    fun startFailure_leavesCameraClosed_andNextRequestRetries() {
        val m = CameraModeManager()
        m.requestMode(CameraMode.REAR_AR)
        assertEquals(None, m.onCameraFailed("câmera ocupada"))
        assertEquals("câmera ocupada", m.lastError)
        assertNull(m.activeMode)
        assertEquals(Open(CameraMode.REAR_AR), m.requestMode(CameraMode.REAR_AR))
    }

    @Test
    fun stopWhileActive_releasesAndEndsStopped() {
        val m = activeFront()
        assertEquals(Release, m.stop())
        assertEquals(None, m.onCameraReleased())
        assertEquals(CameraModeManager.State.STOPPED, m.state)
    }

    @Test
    fun stopWhileStarting_releasesAsSoonAsStartCompletes() {
        val m = CameraModeManager()
        m.requestMode(CameraMode.FRONT_TRACKING)
        assertEquals(None, m.stop())
        assertEquals(Release, m.onCameraStarted())
        assertEquals(None, m.onCameraReleased())
        assertEquals(CameraModeManager.State.STOPPED, m.state)
    }

    @Test
    fun stopCancelsPendingSwitch() {
        val m = activeFront()
        m.requestMode(CameraMode.REAR_AR)
        m.stop()
        assertEquals(None, m.onCameraReleased())
        assertEquals(CameraModeManager.State.STOPPED, m.state)
    }

    private fun activeFront(): CameraModeManager {
        val m = CameraModeManager()
        m.requestMode(CameraMode.FRONT_TRACKING)
        m.onCameraStarted()
        return m
    }
}
