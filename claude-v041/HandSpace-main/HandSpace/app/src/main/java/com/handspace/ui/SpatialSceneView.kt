package com.handspace.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.handspace.R
import com.handspace.gesture.HandGesture
import com.handspace.interaction.ButtonAction
import com.handspace.interaction.DemoLayout
import com.handspace.interaction.ElementKind
import com.handspace.interaction.HandCursor
import com.handspace.interaction.SpatialElement
import com.handspace.interaction.SpatialScene
import kotlin.math.abs
import kotlin.math.min

/**
 * Desenha a cena espacial (painéis, objeto, botões e cursores) sobre o preview.
 *
 * A view não recebe toques: a interação vem dos gestos, que chegam por [render].
 * Desenha com Paints e RectF reutilizados e só se invalida quando há mudança ou animação.
 */
class SpatialSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity
    private val res = context.resources

    val scene: SpatialScene = DemoLayout.create(density)

    private val gestureNames: Array<String> = res.getStringArray(R.array.gesture_names)
    private val buttonResetLabel: String = res.getString(R.string.button_reset)
    private val buttonColorLabel: String = res.getString(R.string.button_color)
    private val infoTitle: String = res.getString(R.string.info_title)

    private var cursors: List<HandCursor> = emptyList()
    private var infoKey = Long.MIN_VALUE
    private var infoGesture = ""
    private var infoHands = ""
    private var infoDrags = ""
    private var infoColor = ""

    private val rect = RectF()
    private val shadowRect = RectF()

    private val panelPaint = paint(0xCC121923.toInt(), Paint.Style.FILL)
    private val buttonPaint = paint(BUTTON_BASE, Paint.Style.FILL)
    private val objectPaint = paint(OBJECT_COLORS[0], Paint.Style.FILL)
    private val shadowPaint = paint(0x55000000, Paint.Style.FILL)
    private val highlightPaint = paint(0x40FFFFFF, Paint.Style.FILL)
    private val borderPaint = paint(0x33FFFFFF, Paint.Style.STROKE, dp(1f))
    private val accentBorderPaint = paint(ACCENT, Paint.Style.STROKE, dp(2f))
    private val cursorRingPaint = paint(0xDDFFFFFF.toInt(), Paint.Style.STROKE, dp(2f))
    private val cursorFillPaint = paint(ACCENT, Paint.Style.FILL)
    private val titlePaint = textPaint(0xFFEAF2F8.toInt(), 13f, medium = true)
    private val bodyPaint = textPaint(0xFFB8C7D6.toInt(), 12f, medium = false)
    private val buttonTextPaint = textPaint(0xFFEAF2F8.toInt(), 13f, medium = true).apply {
        textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Float): Float = value * density

    private fun paint(color: Int, style: Paint.Style, strokeWidthPx: Float = 0f): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
            strokeWidth = strokeWidthPx
        }

    private fun textPaint(color: Int, sizeSp: Float, medium: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizeSp * scaledDensity
            typeface = if (medium) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scene.setViewport(w.toFloat(), h.toFloat())
    }

    /** Recebe o estado mais recente da interação e agenda um redesenho. Chamar na thread principal. */
    fun render(cursors: List<HandCursor>, primaryGesture: HandGesture, handCount: Int) {
        this.cursors = cursors
        refreshInfo(primaryGesture, handCount)
        invalidate()
    }

    private fun refreshInfo(gesture: HandGesture, hands: Int) {
        val drags = scene.dragCount
        val color = scene.colorIndex
        val key = (gesture.ordinal.toLong() shl 48) or
            (hands.toLong() shl 40) or
            (drags.toLong() shl 20) or
            color.toLong()
        if (key == infoKey) return
        infoKey = key
        infoGesture = res.getString(R.string.info_gesture, gestureNames[gesture.ordinal])
        infoHands = res.getString(R.string.info_hands, hands)
        infoDrags = res.getString(R.string.info_drags, drags)
        infoColor = res.getString(R.string.info_color, color + 1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        var animating = false
        for (element in scene.elementsInDrawOrder) {
            if (drawElement(canvas, element, now)) animating = true
        }
        drawCursors(canvas)
        if (animating) postInvalidateOnAnimation()
    }

    /** Desenha um elemento. Retorna true se ele ainda está animando. */
    private fun drawElement(canvas: Canvas, e: SpatialElement, now: Long): Boolean {
        val held = e.heldBy != null
        val liftTarget = if (held) 1f else 0f
        e.lift += (liftTarget - e.lift) * LIFT_STEP
        if (abs(liftTarget - e.lift) < LIFT_EPSILON) e.lift = liftTarget

        val flash = pressFlash(e, now)
        val accent = held || isHovered(e.id)

        rect.set(e.left, e.top, e.right, e.bottom)
        val scale = 1f + LIFT_SCALE * e.lift
        canvas.save()
        canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        when (e.kind) {
            ElementKind.PANEL -> drawPanel(canvas, e, accent)
            ElementKind.BUTTON -> drawButton(canvas, e, flash, accent)
            ElementKind.OBJECT -> drawObject(canvas, accent)
        }
        canvas.restore()

        return e.lift != liftTarget || flash > 0f
    }

    private fun drawPanel(canvas: Canvas, e: SpatialElement, accent: Boolean) {
        val radius = dp(RADIUS_DP)
        shadowRect.set(rect)
        shadowRect.offset(0f, dp(SHADOW_DP))
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        canvas.drawRoundRect(rect, radius, radius, panelPaint)
        canvas.drawRoundRect(rect, radius, radius, if (accent) accentBorderPaint else borderPaint)
        if (e.id == DemoLayout.PANEL_INFO_ID) drawInfo(canvas, e)
    }

    private fun drawInfo(canvas: Canvas, e: SpatialElement) {
        val x = e.left + dp(16f)
        var y = e.top + dp(30f)
        canvas.drawText(infoTitle, x, y, titlePaint)
        y += dp(26f)
        canvas.drawText(infoGesture, x, y, bodyPaint)
        y += dp(20f)
        canvas.drawText(infoHands, x, y, bodyPaint)
        y += dp(20f)
        canvas.drawText(infoDrags, x, y, bodyPaint)
        y += dp(20f)
        canvas.drawText(infoColor, x, y, bodyPaint)
    }

    private fun drawButton(canvas: Canvas, e: SpatialElement, flash: Float, accent: Boolean) {
        val radius = dp(RADIUS_DP)
        shadowRect.set(rect)
        shadowRect.offset(0f, dp(SHADOW_DP))
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        buttonPaint.color = ColorUtils.blendARGB(BUTTON_BASE, ACCENT, flash * FLASH_BLEND)
        canvas.drawRoundRect(rect, radius, radius, buttonPaint)
        canvas.drawRoundRect(rect, radius, radius, if (accent) accentBorderPaint else borderPaint)

        val label = if (e.action == ButtonAction.RESET_LAYOUT) buttonResetLabel else buttonColorLabel
        val fm = buttonTextPaint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, rect.centerX(), baseline, buttonTextPaint)
    }

    private fun drawObject(canvas: Canvas, accent: Boolean) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = min(rect.width(), rect.height()) / 2f
        canvas.drawCircle(cx, cy + dp(SHADOW_DP), radius, shadowPaint)
        objectPaint.color = OBJECT_COLORS[scene.colorIndex % OBJECT_COLORS.size]
        canvas.drawCircle(cx, cy, radius, objectPaint)
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.28f, radius * 0.22f, highlightPaint)
        if (accent) canvas.drawCircle(cx, cy, radius + dp(4f), accentBorderPaint)
    }

    private fun drawCursors(canvas: Canvas) {
        for (c in cursors) {
            if (c.holdingId != null) canvas.drawCircle(c.x, c.y, dp(10f), cursorFillPaint)
            val pinching = c.gesture == HandGesture.PINCH
            val ring = if (c.hoverId != null && c.holdingId == null) accentBorderPaint else cursorRingPaint
            canvas.drawCircle(c.x, c.y, if (pinching) dp(8f) else dp(14f), ring)
        }
    }

    private fun isHovered(id: String): Boolean {
        for (c in cursors) if (c.hoverId == id) return true
        return false
    }

    /** Intensidade do flash de um botão recém-acionado: 1 no acionamento, 0 após [PRESS_FLASH_MS]. */
    private fun pressFlash(e: SpatialElement, now: Long): Float {
        if (e.pressedAtMs == SpatialElement.NO_PRESS) return 0f
        val age = now - e.pressedAtMs
        if (age < 0L || age >= PRESS_FLASH_MS) return 0f
        return 1f - age.toFloat() / PRESS_FLASH_MS
    }

    companion object {
        private const val RADIUS_DP = 16f
        private const val SHADOW_DP = 6f
        private const val LIFT_STEP = 0.25f
        private const val LIFT_EPSILON = 0.01f
        private const val LIFT_SCALE = 0.04f
        private const val FLASH_BLEND = 0.6f
        private const val PRESS_FLASH_MS = 260L
        private val ACCENT: Int = 0xFF7AD7F0.toInt()
        private val BUTTON_BASE: Int = 0xEE22364A.toInt()
        private val OBJECT_COLORS: IntArray = intArrayOf(
            0xFF7AD7F0.toInt(),
            0xFFF2B86B.toInt(),
            0xFFB39DDB.toInt(),
            0xFF8BE3A8.toInt(),
        )
    }
}
