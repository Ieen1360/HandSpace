package com.handspace.interaction

import com.handspace.tracking.Handedness
import kotlin.math.max

enum class ElementKind { PANEL, OBJECT, BUTTON }

enum class ButtonAction { RESET_LAYOUT, CYCLE_OBJECT_COLOR }

/**
 * Elemento da cena espacial. Posições e tamanhos em pixels da view.
 * [defaultFractionX]/[defaultFractionY] definem a posição inicial como fração da tela,
 * para que o layout se adapte a qualquer resolução e proporção.
 */
class SpatialElement(
    val id: String,
    val kind: ElementKind,
    val widthPx: Float,
    val heightPx: Float,
    val defaultFractionX: Float,
    val defaultFractionY: Float,
    val action: ButtonAction? = null,
) {
    var left: Float = 0f
        internal set

    var top: Float = 0f
        internal set

    /** Mão que está segurando o elemento, ou null. */
    var heldBy: Handedness? = null
        internal set

    /** Instante do último acionamento (botões), ou [NO_PRESS]. */
    var pressedAtMs: Long = NO_PRESS
        internal set

    /** Progresso da animação de elevação, de 0 a 1. Só afeta o desenho. */
    var lift: Float = 0f

    val right: Float get() = left + widthPx
    val bottom: Float get() = top + heightPx

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    companion object {
        const val NO_PRESS = Long.MIN_VALUE
    }
}

/**
 * Modelo da cena: posições, ordem de empilhamento e estado da demonstração.
 * Não depende de Android, então toda a lógica é testada na JVM.
 */
class SpatialScene(initial: List<SpatialElement>) {

    private val all: List<SpatialElement> = initial.toList()
    private val drawOrder: MutableList<SpatialElement> = initial.toMutableList()

    var viewportWidth: Float = 0f
        private set

    var viewportHeight: Float = 0f
        private set

    /** Número de arrastes concluídos (elemento solto). */
    var dragCount: Int = 0
        private set

    /** Índice da cor atual do objeto, de 0 a [OBJECT_COLOR_COUNT] - 1. */
    var colorIndex: Int = 0
        private set

    /** Elementos na ordem de desenho: o último fica por cima. Não modifique a lista retornada. */
    val elementsInDrawOrder: List<SpatialElement> get() = drawOrder

    fun element(id: String): SpatialElement? = all.firstOrNull { it.id == id }

    /**
     * Define o tamanho da tela. Na primeira chamada, posiciona pelas frações padrão;
     * depois, reescala as posições atuais para manter o layout proporcional.
     */
    fun setViewport(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            for (e in all) {
                e.left = e.defaultFractionX * width
                e.top = e.defaultFractionY * height
            }
        } else {
            val sx = width / viewportWidth
            val sy = height / viewportHeight
            for (e in all) {
                e.left *= sx
                e.top *= sy
            }
        }
        viewportWidth = width
        viewportHeight = height
        for (e in all) clampInside(e)
    }

    /** Elemento mais alto sob o ponto, ou null. */
    fun hitTest(x: Float, y: Float): SpatialElement? {
        for (i in drawOrder.indices.reversed()) {
            val e = drawOrder[i]
            if (e.contains(x, y)) return e
        }
        return null
    }

    fun bringToFront(e: SpatialElement) {
        if (drawOrder.remove(e)) drawOrder.add(e)
    }

    fun moveTo(e: SpatialElement, left: Float, top: Float) {
        e.left = left
        e.top = top
        clampInside(e)
    }

    /** Registra o acionamento de um botão e executa a ação associada. */
    fun press(e: SpatialElement, nowMs: Long) {
        e.pressedAtMs = nowMs
        e.action?.let { perform(it) }
    }

    fun perform(action: ButtonAction) {
        when (action) {
            ButtonAction.RESET_LAYOUT -> resetLayout()
            ButtonAction.CYCLE_OBJECT_COLOR -> colorIndex = (colorIndex + 1) % OBJECT_COLOR_COUNT
        }
    }

    /**
     * Volta os elementos às posições iniciais. Elementos seguros por alguma mão não são movidos:
     * a mão que os segura continuaria puxando-os de volta para o cursor.
     */
    fun resetLayout() {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        for (e in all) {
            if (e.heldBy != null) continue
            e.left = e.defaultFractionX * viewportWidth
            e.top = e.defaultFractionY * viewportHeight
            clampInside(e)
        }
    }

    fun completeDrag() {
        dragCount++
    }

    private fun clampInside(e: SpatialElement) {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        e.left = e.left.coerceIn(0f, max(0f, viewportWidth - e.widthPx))
        e.top = e.top.coerceIn(0f, max(0f, viewportHeight - e.heightPx))
    }

    companion object {
        const val OBJECT_COLOR_COUNT = 4
    }
}

/** Layout da demonstração. Tamanhos em dp convertidos para px com [density]. */
object DemoLayout {
    const val PANEL_INFO_ID = "panel_info"
    const val OBJECT_ID = "object_orb"
    const val BUTTON_RESET_ID = "button_reset"
    const val BUTTON_COLOR_ID = "button_color"

    fun create(density: Float): SpatialScene {
        val d = density
        return SpatialScene(
            listOf(
                SpatialElement(PANEL_INFO_ID, ElementKind.PANEL, 260f * d, 170f * d, 0.06f, 0.14f),
                SpatialElement(OBJECT_ID, ElementKind.OBJECT, 110f * d, 110f * d, 0.62f, 0.45f),
                SpatialElement(
                    BUTTON_RESET_ID, ElementKind.BUTTON, 150f * d, 52f * d, 0.10f, 0.72f,
                    ButtonAction.RESET_LAYOUT,
                ),
                SpatialElement(
                    BUTTON_COLOR_ID, ElementKind.BUTTON, 150f * d, 52f * d, 0.56f, 0.72f,
                    ButtonAction.CYCLE_OBJECT_COLOR,
                ),
            )
        )
    }
}
