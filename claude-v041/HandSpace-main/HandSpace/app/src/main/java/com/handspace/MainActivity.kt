package com.handspace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.handspace.camera.CameraCommand
import com.handspace.camera.CameraMode
import com.handspace.camera.CameraModeManager
import com.handspace.camera.FrontCameraController
import com.handspace.databinding.ActivityMainBinding
import com.handspace.gesture.HandGesture
import com.handspace.interaction.HandInteractionController
import com.handspace.interaction.SpatialWorldController
import com.handspace.sensors.DeviceSensorManager
import com.handspace.spatial.DemoWorld
import com.handspace.spatial.SpatialWorld
import com.handspace.tracking.HandFrame
import com.handspace.tracking.HandPresenceTracker
import com.handspace.tracking.HandTracker
import com.handspace.tracking.ViewTransform
import com.handspace.util.FpsCounter

/**
 * Orquestra câmera, rastreamento, interação 2D (bola e painéis da v0.3, modo frontal),
 * mundo 3D (modo traseiro e mãos em perspectiva) e sensores.
 *
 * Só uma câmera fica aberta por vez. A troca passa por [CameraModeManager].
 * ARCore não está integrado: o modo traseiro é um fallback sem âncoras espaciais.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var handTracker: HandTracker
    private lateinit var camera: FrontCameraController
    private lateinit var interaction2d: HandInteractionController
    private lateinit var world: SpatialWorld
    private lateinit var worldController: SpatialWorldController
    private lateinit var sensors: DeviceSensorManager

    private val cameraManager = CameraModeManager()
    private val presence = HandPresenceTracker()
    private val trackingFps = FpsCounter()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var lastStatusUpdateMs = 0L
    private var lastGesture: HandGesture? = null
    private var infoLines: List<String> = emptyList()

    /** Atualiza o diagnóstico dos sensores em taxa baixa, sem depender dos frames da câmera. */
    private val sensorTicker = object : Runnable {
        override fun run() {
            updateSensorText()
            uiHandler.postDelayed(this, SENSOR_REFRESH_MS)
        }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestCameraMode(cameraManager.mode)
        } else {
            binding.statusText.text = getString(R.string.camera_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        world = DemoWorld.create()
        worldController = SpatialWorldController(world, onCameraAction = { toggleCamera() })
        interaction2d = HandInteractionController(binding.sceneView.scene)
        sensors = DeviceSensorManager(this)

        handTracker = HandTracker(
            context = this,
            listener = object : HandTracker.Listener {
                override fun onHandFrame(frame: HandFrame) = runOnUiThread { renderFrame(frame) }
                override fun onTrackerError(message: String) = runOnUiThread {
                    binding.statusText.text = message
                }
            },
        )

        camera = FrontCameraController(
            context = this,
            lifecycleOwner = this,
            onFrame = { bitmap, timestamp -> handTracker.submitFrame(bitmap, timestamp) },
            onError = { message ->
                runOnUiThread {
                    handleCameraCommand(cameraManager.onCameraFailed(message))
                    binding.statusText.text = getString(R.string.camera_start_failed, message)
                    updateModeUi()
                }
            },
            onStarted = { _ ->
                runOnUiThread {
                    handleCameraCommand(cameraManager.onCameraStarted())
                    binding.privacyText.text = getString(R.string.camera_active_privacy)
                    binding.privacyText.visibility = View.VISIBLE
                    updateModeUi()
                }
            },
        )

        binding.cameraButton.setOnClickListener { toggleCamera() }
        updateModeUi()
    }

    override fun onStart() {
        super.onStart()
        if (cameraPermissionGranted()) {
            requestCameraMode(cameraManager.mode)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        sensors.start()
        uiHandler.removeCallbacks(sensorTicker)
        uiHandler.post(sensorTicker)
    }

    override fun onStop() {
        camera.stop()
        handleCameraCommand(cameraManager.stop())
        interaction2d.reset()
        worldController.reset()
        sensors.stop()
        uiHandler.removeCallbacks(sensorTicker)
        binding.privacyText.visibility = View.GONE
        super.onStop()
    }

    override fun onDestroy() {
        camera.release()
        handTracker.close()
        super.onDestroy()
    }

    private fun cameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraMode(target: CameraMode) {
        if (!cameraPermissionGranted()) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        handleCameraCommand(cameraManager.requestMode(target))
        updateModeUi()
    }

    private fun toggleCamera() {
        val next = if (cameraManager.mode == CameraMode.FRONT_TRACKING) {
            CameraMode.REAR_AR
        } else {
            CameraMode.FRONT_TRACKING
        }
        requestCameraMode(next)
    }

    /** Executa o comando da máquina de estados. Libera antes de abrir, nunca em paralelo. */
    private fun handleCameraCommand(cmd: CameraCommand) {
        when (cmd) {
            is CameraCommand.Open -> {
                binding.statusText.text = getString(R.string.camera_opening)
                camera.start(binding.previewView, cmd.mode)
            }
            CameraCommand.Release -> {
                camera.stop()
                handleCameraCommand(cameraManager.onCameraReleased())
            }
            CameraCommand.None -> Unit
        }
    }

    private fun updateModeUi() {
        val isRear = cameraManager.mode == CameraMode.REAR_AR
        binding.modeText.text = getString(
            if (isRear) R.string.camera_rear_label else R.string.camera_front_label
        )
        binding.sceneView.visibility = if (isRear) View.GONE else View.VISIBLE
        binding.cameraButton.text = getString(R.string.camera_switch)
    }

    /** Roda na thread principal para cada resultado do MediaPipe. */
    private fun renderFrame(frame: HandFrame) {
        binding.landmarkOverlay.render(frame)
        val now = SystemClock.uptimeMillis()
        trackingFps.tick(now)
        presence.update(frame)?.let { Log.i(TAG, "Transição de presença de mão: $it") }

        val mode = cameraManager.activeMode ?: cameraManager.mode
        val isFront = mode == CameraMode.FRONT_TRACKING
        val sceneView = binding.sceneView
        val worldView = binding.worldView
        val worldCamera = worldView.currentCamera()

        if (sceneView.width > 0 && sceneView.height > 0 && worldCamera != null) {
            // A mesma transformação alimenta o overlay 2D, a bola 2D e o mundo 3D.
            val transform = ViewTransform.fillCenter(
                frame.imageWidth,
                frame.imageHeight,
                sceneView.width.toFloat(),
                sceneView.height.toFloat(),
            )
            if (isFront) {
                interaction2d.onFrame(frame, transform, now)
            } else {
                interaction2d.reset()
            }
            worldController.interactive = !isFront
            worldController.onFrame(frame, transform, worldCamera, now)
        }

        sceneView.render(interaction2d.cursors, interaction2d.primaryGesture(), frame.hands.size)
        val gesture = worldController.hands.firstOrNull()?.gesture ?: HandGesture.NONE
        worldView.render(
            world,
            worldController.hands,
            worldController.showSkeleton,
            showObjects = !isFront,
            infoLines = infoLines,
        )

        if (gesture != lastGesture) {
            lastGesture = gesture
            binding.gestureText.text = getString(R.string.gesture_current, gestureLabel(gesture))
        }

        if (now - lastStatusUpdateMs >= STATUS_REFRESH_MS) {
            lastStatusUpdateMs = now
            val handsText = if (frame.hands.isEmpty()) {
                getString(R.string.hands_none)
            } else {
                resources.getQuantityString(R.plurals.hands_detected, frame.hands.size, frame.hands.size)
            }
            binding.statusText.text = getString(
                R.string.status_tracking,
                handsText,
                trackingFps.fps,
                frame.latencyMs.toInt(),
            )
            infoLines = buildInfoLines(gesture, frame.hands.size, mode)
        }
    }

    private fun buildInfoLines(gesture: HandGesture, hands: Int, mode: CameraMode): List<String> = listOf(
        getString(R.string.info_gesture, gestureLabel(gesture)),
        getString(R.string.info_hands, hands),
        getString(R.string.info_drags, worldController.dragCount),
        getString(
            if (mode == CameraMode.REAR_AR) R.string.info_camera_rear else R.string.info_camera_front
        ),
        getString(R.string.info_anchors),
    )

    private fun gestureLabel(gesture: HandGesture): String =
        resources.getStringArray(R.array.gesture_names)[gesture.ordinal]

    private fun updateSensorText() {
        val gyro = sensors.gyroscopeValues()
        val accel = sensors.accelerometerValues()
        val gyroLine = when {
            !sensors.hasGyroscope -> getString(R.string.sensor_gyro_unavailable)
            gyro == null -> getString(R.string.sensor_gyro_waiting)
            else -> getString(R.string.sensor_gyro_values, gyro[0], gyro[1], gyro[2])
        }
        val accelLine = when {
            !sensors.hasAccelerometer -> getString(R.string.sensor_accel_unavailable)
            accel == null -> getString(R.string.sensor_accel_waiting)
            else -> getString(R.string.sensor_accel_values, accel[0], accel[1], accel[2])
        }
        binding.sensorText.text = "$gyroLine\n$accelLine"
    }

    private companion object {
        const val TAG = "HandSpace"
        const val STATUS_REFRESH_MS = 250L
        const val SENSOR_REFRESH_MS = 250L
    }
}
