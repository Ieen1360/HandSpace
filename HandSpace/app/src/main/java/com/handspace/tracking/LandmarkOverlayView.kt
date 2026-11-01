package com.handspace.tracking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Desenha os 21 landmarks e o esqueleto de cada mão sobre o preview da câmera.
 * Fundo transparente: só os traços da mão aparecem.
 */
class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var frame: HandFrame? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.YELLOW
    }

    /** Chamado na thread principal com o resultado mais recente. */
    fun render(newFrame: HandFrame) {
        frame = newFrame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = frame ?: return
        if (current.imageWidth == 0 || current.imageHeight == 0) return

        val transform = ViewTransform.fillCenter(
            imageWidth = current.imageWidth,
            imageHeight = current.imageHeight,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )

        for (hand in current.hands) {
            if (hand.landmarks.size < HandLandmarkIndex.LANDMARK_COUNT) continue
            linePaint.color = colorFor(hand.handedness)

            for ((from, to) in HandLandmarkIndex.CONNECTIONS) {
                val a = transform.map(hand.landmarks[from])
                val b = transform.map(hand.landmarks[to])
                canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
            }

            hand.landmarks.forEachIndexed { index, landmark ->
                val p = transform.map(landmark)
                val isTip = index in HandLandmarkIndex.FINGERTIPS
                canvas.drawCircle(p.x, p.y, if (isTip) 9f else 6f, if (isTip) tipPaint else jointPaint)
            }
        }
    }

    private fun colorFor(handedness: Handedness): Int = when (handedness) {
        Handedness.LEFT -> Color.rgb(0, 229, 255)
        Handedness.RIGHT -> Color.rgb(255, 64, 129)
        Handedness.UNKNOWN -> Color.LTGRAY
    }
}
