
package com.handspace.tracking

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

class HandTracker(
    context: Context,
    private val listener: Listener,
    useGpu: Boolean = true,
) : AutoCloseable {

    interface Listener {
        fun onHandFrame(frame: HandFrame)
        fun onTrackerError(message: String)
    }

    private val inFlight = AtomicBoolean(false)
    private var landmarker: HandLandmarker? = null

    init {
        val delegates: List<Delegate> = if (useGpu) {
            listOf(Delegate.GPU, Delegate.CPU)
        } else {
            listOf(Delegate.CPU)
        }

        var lastError: Exception? = null

        for (delegate in delegates) {
            try {
                landmarker = buildLandmarker(context, delegate)
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
            }
        }

        lastError?.let {
            listener.onTrackerError(
                "Falha ao carregar o modelo de mãos (${it.message}). " +
                    "Confirme que hand_landmarker.task está em app/src/main/assets/."
            )
        }
    }

    private fun buildLandmarker(
        context: Context,
        delegate: Delegate
    ): HandLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(MAX_HANDS)
            .setMinHandDetectionConfidence(MIN_CONFIDENCE)
            .setMinHandPresenceConfidence(MIN_CONFIDENCE)
            .setMinTrackingConfidence(MIN_CONFIDENCE)
            .setResultListener { result: HandLandmarkerResult, input: MPImage ->
                onMediaPipeResult(result, input)
            }
            .setErrorListener { error ->
                inFlight.set(false)
                listener.onTrackerError(
                    error.message ?: "erro desconhecido no rastreamento"
                )
            }
            .build()

        return HandLandmarker.createFromOptions(context, options)
    }

    fun submitFrame(bitmap: Bitmap, captureTimestampMs: Long): Boolean {
        val lm = landmarker ?: return false

        if (!inFlight.compareAndSet(false, true)) return false

        return try {
            lm.detectAsync(
                BitmapImageBuilder(bitmap).build(),
                captureTimestampMs
            )
            true
        } catch (e: Exception) {
            inFlight.set(false)
            listener.onTrackerError("Erro ao enviar frame: ${e.message}")
            false
        }
    }

    private fun onMediaPipeResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        inFlight.set(false)

        val latency = SystemClock.uptimeMillis() - result.timestampMs()

        listener.onHandFrame(
            result.toHandFrame(input.width, input.height, latency)
        )
    }

    override fun close() {
        landmarker?.close()
        landmarker = null
    }

    private companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        const val MAX_HANDS = 2
        const val MIN_CONFIDENCE = 0.5f
    }
}

internal fun HandLandmarkerResult.toHandFrame(
    width: Int,
    height: Int,
    latencyMs: Long
): HandFrame {
    val hands = landmarks().mapIndexed { index, points ->
        val category = handedness().getOrNull(index)?.firstOrNull()

        val side = when (category?.categoryName()) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> Handedness.UNKNOWN
        }

        TrackedHand(
            handedness = side,
            score = category?.score() ?: 0f,
            landmarks = points.map {
                Landmark(it.x(), it.y(), it.z())
            },
        )
    }

    return HandFrame(
        timestampMs = timestampMs(),
        hands = hands,
        imageWidth = width,
        imageHeight = height,
        latencyMs = latencyMs,
    )
}
