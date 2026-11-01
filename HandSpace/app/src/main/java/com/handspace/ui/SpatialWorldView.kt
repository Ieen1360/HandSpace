package com.handspace.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.handspace.R
import com.handspace.interaction.SpatialWorldController
import com.handspace.spatial.Camera3D
import com.handspace.spatial.DemoWorld
import com.handspace.spatial.SpatialWorld
import com.handspace.spatial.WorldKind
import com.handspace.spatial.WorldObject
import com.handspace.tracking.HandLandmarkIndex
import com.handspace.tracking.Handedness
import kotlin.math.max

/**
 * Desenha o mundo virtual em perspectiva sobre o preview da câmera: objetos, painéis, mãos 3D
 * e a ponta do indicador de cada mão. Transparente; não recebe toques.
 */
class SpatialWorldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity
    private val res = context.resources
    private val menuLabels: Array<String> = res.getStringArray(R.array.menu_labels)

    private var camera: Camera3D? = null
    private var world: SpatialWorld? = null
    private var hands: List<SpatialWorldController.HandPose3D> = emptyList()
    private var showSkeleton = true
    private var showObjects = false
    private var infoLines: List<String> = emptyList()

    private val rect = RectF()

    private val buttonPaint = fill(0xDD1A2230.toInt())
    private val panelPaint = fill(0xCC121923.toInt())
    private val sphereFill = fill(SPHERE_COLOR)
    private val sphereHighlight = fill(0x66FFFFFF)
    private val labelPaint = text(0xFFEAF2F8.toInt(), 11f, medium = true).apply {
        textAlign = Paint.Align.CENTER
    }
    private val infoPaint = text(0xFFB8C7D6.toInt(), 9f, medium = false)
    private val accentStroke = stroke(ACCENT, dp(2f))
    private val borderStroke = stroke(0x33FFFFFF, dp(1f))
    private val leftBone = stroke(LEFT_COLOR, dp(2f)).apply { strokeCap = Paint.Cap.ROUND }
    private val rightBone = stroke(RIGHT_COLOR, dp(2f)).apply { strokeCap = Paint.Cap.ROUND }
    private val jointFill = fill(0xEEFFFFFF.toInt())
    private val tipRing = stroke(0xDDFFFFFF.toInt(), dp(2f))

    private fun dp(value: Float): Float = value * density

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun text(color: Int, sizeSp: Float, medium: Boolean): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizeSp * scaledDensity
            typeface = if (medium) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
        }

    /** Câmera de projeção do tamanho atual da view, ou null antes do primeiro layout. */
    fun currentCamera(): Camera3D? = camera

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        camera = if (w > 0 && h > 0) Camera3D(w.toFloat(), h.toFloat()) else null
    }

    fun render(
        world: SpatialWorld,
        hands: List<SpatialWorldController.HandPose3D>,
        showSkeleton: Boolean,
        showObjects: Boolean,
        infoLines: List<String>,
    ) {
        this.world = world
        this.hands = hands
        this.showSkeleton = showSkeleton
        this.showObjects = showObjects
        this.infoLines = infoLines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cam = camera ?: return
        val w = world

        if (w != null && showObjects) {
            // Do mais longe para o mais perto, para que o que está na frente fique por cima.
            for (o in w.objects.sortedByDescending { it.center.z }) {
                if (o.visible) drawObject(canvas, cam, o)
            }
            drawInfoPanel(canvas, cam, w)
        }
        if (showSkeleton) drawHands(canvas, cam)
        drawTips(canvas)
    }

    private fun drawObject(canvas: Canvas, cam: Camera3D, o: WorldObject) {
        val c = cam.project(o.center) ?: return
        val ppm = cam.pixelsPerMeter(o.center.z)
        val held = o.heldBy != null
        val hovered = isHovered(o.id)
        when (o.kind) {
            WorldKind.SPHERE -> {
                val r = o.radius * o.scale * ppm
                canvas.drawCircle(c.x, c.y, r, sphereFill)
                canvas.drawCircle(c.x - r * 0.3f, c.y - r * 0.3f, r * 0.25f, sphereHighlight)
                if (held || hovered) canvas.drawCircle(c.x, c.y, r + dp(3f), accentStroke)
            }
            WorldKind.PANEL, WorldKind.BUTTON -> {
                val hw = o.halfWidth * o.scale * ppm
                val hh = o.halfHeight * o.scale * ppm
                rect.set(c.x - hw, c.y - hh, c.x + hw, c.y + hh)
                val radius = dp(12f)
                val base = if (o.kind == WorldKind.BUTTON) buttonPaint else panelPaint
                canvas.drawRoundRect(rect, radius, radius, base)
                canvas.drawRoundRect(rect, radius, radius, if (held || hovered) accentStroke else borderStroke)
                val action = o.action
                if (action != null) {
                    val fm = labelPaint.fontMetrics
                    val baseline = c.y - (fm.ascent + fm.descent) / 2f
                    canvas.drawText(menuLabels[action.ordinal], c.x, baseline, labelPaint)
                }
            }
        }
    }

    private fun drawInfoPanel(canvas: Canvas, cam: Camera3D, world: SpatialWorld) {
        val info = world.get(DemoWorld.INFO_PANEL_ID) ?: return
        if (!info.visible || infoLines.isEmpty()) return
        val c = cam.project(info.center) ?: return
        val ppm = cam.pixelsPerMeter(info.center.z)
        val left = c.x - info.halfWidth * info.scale * ppm
        val top = c.y - info.halfHeight * info.scale * ppm
        var y = top + dp(14f)
        for (line in infoLines) {
            canvas.drawText(line, left + dp(10f), y, infoPaint)
            y += dp(13f)
        }
    }

    /** Desenha as duas mãos, a mais distante primeiro. Cada osso tem espessura pela profundidade. */
    private fun drawHands(canvas: Canvas, cam: Camera3D) {
        val ordered = hands.sortedByDescending { hp -> hp.joints.sumOf { it.z.toDouble() } }
        for (hp in ordered) {
            val paint = if (hp.hand == Handedness.LEFT) leftBone else rightBone
            val joints = hp.joints
            if (joints.size < HandLandmarkIndex.LANDMARK_COUNT) continue
            for ((a, b) in HandLandmarkIndex.CONNECTIONS) {
                val pa = cam.project(joints[a]) ?: continue
                val pb = cam.project(joints[b]) ?: continue
                val zAvg = (joints[a].z + joints[b].z) / 2f
                paint.strokeWidth = max(dp(1.5f), BONE_RADIUS_M * cam.focalPx / zAvg * 2f)
                canvas.drawLine(pa.x, pa.y, pb.x, pb.y, paint)
            }
            for (j in joints) {
                val p = cam.project(j) ?: continue
                canvas.drawCircle(p.x, p.y, max(dp(2f), JOINT_RADIUS_M * cam.focalPx / j.z), jointFill)
            }
        }
    }

    private fun drawTips(canvas: Canvas) {
        for (hp in hands) {
            val r = if (hp.holdingId != null) dp(10f) else dp(14f)
            canvas.drawCircle(hp.tipScreen.x, hp.tipScreen.y, r, tipRing)
        }
    }

    private fun isHovered(id: String): Boolean {
        for (hp in hands) if (hp.hoverId == id) return true
        return false
    }

    private companion object {
        val ACCENT: Int = 0xFF7AD7F0.toInt()
        val SPHERE_COLOR: Int = 0xFFB39DDB.toInt()
        val LEFT_COLOR: Int = 0xFF00E5FF.toInt()
        val RIGHT_COLOR: Int = 0xFFFF4081.toInt()
        const val BONE_RADIUS_M = 0.006f
        const val JOINT_RADIUS_M = 0.005f
    }
}
