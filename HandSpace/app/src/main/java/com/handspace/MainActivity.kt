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
import com.handspace.camera.FrontCameraController
import com.handspace.databinding.ActivityMainBinding
import com.handspace.gesture.HandGesture
import com.handspace.interaction.HandInteractionController
import com.handspace.sensors.DeviceSensorManager
import com.handspace.tracking.HandFrame
import com.handspace.tracking.HandPresenceTracker
import com.handspace.tracking.HandTracker
import com.handspace.tracking.ViewTransform
import com.handspace.util.FpsCounter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var handTracker: HandTracker
    private lateinit var camera: FrontCameraController
    private lateinit var interaction: HandInteractionController
    private lateinit var sensors: DeviceSensorManager

    private val presence = HandPresenceTracker()
    private val trackingFps = FpsCounter()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var lastStatusUpdateMs = 0L
    private var lastGesture: HandGesture? = null

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
        if (granted) startCamera() else binding.statusText.text = getString(R.string.camera_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        interaction = HandInteractionController(binding.sceneView.scene)
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
                runOnUiThread { binding.statusText.text = getString(R.string.camera_error, message) }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)

        sensors.start()
        uiHandler.removeCallbacks(sensorTicker)
        uiHandler.post(sensorTicker)
    }

    override fun onStop() {
        camera.stop()
        interaction.reset()
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

    private fun startCamera() {
        // Indicador visível sempre que a câmera estiver em uso, conforme a política de privacidade.
        binding.privacyText.text = getString(R.string.camera_active_privacy)
        binding.privacyText.visibility = View.VISIBLE
        camera.start(binding.previewView)
    }

    /** Roda na thread principal para cada resultado do MediaPipe. */
    private fun renderFrame(frame: HandFrame) {
        binding.landmarkOverlay.render(frame)
        val now = SystemClock.uptimeMillis()
        trackingFps.tick(now)
        presence.update(frame)?.let { Log.i(TAG, "Transição de presença de mão: $it") }

        val sceneView = binding.sceneView
        if (sceneView.width > 0 && sceneView.height > 0) {
            // Mesma transformação do overlay de landmarks: cursor e desenho ficam alinhados.
            val transform = ViewTransform.fillCenter(
                frame.imageWidth,
                frame.imageHeight,
                sceneView.width.toFloat(),
                sceneView.height.toFloat(),
            )
            interaction.onFrame(frame, transform, now)
        }

        val gesture = interaction.primaryGesture()
        sceneView.render(interaction.cursors, gesture, frame.hands.size)
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
        }
    }

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
