package com.handspace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.handspace.camera.FrontCameraController
import com.handspace.databinding.ActivityMainBinding
import com.handspace.tracking.HandFrame
import com.handspace.tracking.HandPresenceTracker
import com.handspace.tracking.HandTracker
import com.handspace.util.FpsCounter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var handTracker: HandTracker
    private lateinit var camera: FrontCameraController

    private val presence = HandPresenceTracker()
    private val trackingFps = FpsCounter()

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
    }

    override fun onStop() {
        camera.stop()
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

    private fun renderFrame(frame: HandFrame) {
        binding.landmarkOverlay.render(frame)
        trackingFps.tick(SystemClock.uptimeMillis())

        presence.update(frame)?.let { Log.i(TAG, "Transição de presença de mão: $it") }

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

    private companion object {
        const val TAG = "HandSpace"
    }
}
