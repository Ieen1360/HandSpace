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
 * Câmera ativa (frontal ou traseira) com preview e análise de frames para o MediaPipe.
 *
 * Só uma câmera fica aberta por vez: a troca é feita por [stop] seguido de [start].
 * A imagem analisada é espelhada apenas na câmera frontal, para coincidir com o preview de selfie.
 * Na câmera traseira o rastreamento continua ativo; apenas a orientação não é espelhada.
 */
class FrontCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (bitmap: Bitmap, captureTimestampMs: Long) -> Unit,
    private val onError: (message: String) -> Unit,
    private val onStarted: (mode: CameraMode) -> Unit,
) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null

    /** Incrementado a cada start/stop, para descartar aberturas que já foram superadas. */
    private var generation = 0

    @Volatile
    private var mirror = true

    fun start(previewView: PreviewView, mode: CameraMode) {
        val myGeneration = ++generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (myGeneration != generation) return@addListener
            try {
                bind(future.get(), previewView, mode, myGeneration)
            } catch (e: Exception) {
                onError(e.message ?: "falha desconhecida")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(
        cameraProvider: ProcessCameraProvider,
        previewView: PreviewView,
        mode: CameraMode,
        gen: Int,
    ) {
        val selector = if (mode == CameraMode.REAR_AR) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        if (!cameraProvider.hasCamera(selector)) {
            onError(if (mode == CameraMode.REAR_AR) NO_REAR_CAMERA_MESSAGE else NO_FRONT_CAMERA_MESSAGE)
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

        mirror = mode == CameraMode.FRONT_TRACKING
        analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        if (gen != generation) {
            cameraProvider.unbindAll()
            return
        }
        provider = cameraProvider
        onStarted(mode)
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val raw = proxy.toBitmap()
            val oriented = orient(raw, proxy.imageInfo.rotationDegrees, mirror)
            onFrame(oriented, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao converter frame", e)
        } finally {
            proxy.close()
        }
    }

    /** Gira para vertical e, se [mirrorHorizontal], espelha. Retorna um Bitmap novo, sem offsets negativos. */
    private fun orient(source: Bitmap, rotationDegrees: Int, mirrorHorizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (mirrorHorizontal) postScale(-1f, 1f)
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

    /** Desvincula a câmera. Invalida aberturas pendentes. */
    fun stop() {
        generation++
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
        const val NO_REAR_CAMERA_MESSAGE = "Este dispositivo não possui câmera traseira disponível."
        val ANALYSIS_SIZE = Size(640, 480)
    }
}
