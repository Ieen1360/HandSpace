package com.handspace.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Câmera frontal com preview e análise de frames.
 *
 * Cada frame de análise é convertido para Bitmap já vertical e espelhado, igual ao preview
 * de selfie. Assim, as coordenadas dos landmarks coincidem com o que o usuário vê.
 */
class FrontCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (bitmap: Bitmap, captureTimestampMs: Long) -> Unit,
    private val onError: (message: String) -> Unit,
) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null

    fun start(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                bind(future.get(), previewView)
            } catch (e: Exception) {
                onError(e.message ?: "falha desconhecida")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(cameraProvider: ProcessCameraProvider, previewView: PreviewView) {
        if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            onError(NO_FRONT_CAMERA_MESSAGE)
            return
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            ANALYSIS_SIZE,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            analysis,
        )
        provider = cameraProvider
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val raw = proxy.toBitmap()
            val oriented = orientForSelfieView(raw, proxy.imageInfo.rotationDegrees)
            onFrame(oriented, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao converter frame", e)
        } finally {
            proxy.close()
        }
    }

    /** Gira para vertical e espelha horizontalmente. Retorna um Bitmap novo, sem offsets negativos. */
    private fun orientForSelfieView(source: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(-1f, 1f)
        }
        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)

        val output = Bitmap.createBitmap(
            bounds.width().roundToInt(),
            bounds.height().roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(output).drawBitmap(source, matrix, null)
        return output
    }

    /** Desvincula a câmera. Pode ser chamado em onStop; start() religa. */
    fun stop() {
        provider?.unbindAll()
        provider = null
    }

    fun release() {
        stop()
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "FrontCameraController"
        const val NO_FRONT_CAMERA_MESSAGE = "Este dispositivo não possui câmera frontal disponível."
        val ANALYSIS_SIZE = Size(640, 480)
    }
}
