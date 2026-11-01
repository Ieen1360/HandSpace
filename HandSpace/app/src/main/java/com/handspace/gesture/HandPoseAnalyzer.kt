package com.handspace.gesture

import com.handspace.tracking.Landmark
import kotlin.math.hypot

/**
 * Medidas geométricas da mão. Puras e sem dependência de Android, para serem testadas na JVM.
 * As distâncias são calculadas em pixels da imagem, porque as coordenadas normalizadas
 * distorcem a proporção quando largura e altura são diferentes.
 */
object HandPoseAnalyzer {

    private val FINGER_TIPS = intArrayOf(8, 12, 16, 20)   // indicador, médio, anelar, mindinho
    private val FINGER_PIPS = intArrayOf(6, 10, 14, 18)
    private const val THUMB_TIP = 4
    private const val INDEX_TIP = 8
    private const val WRIST = 0
    private const val MIDDLE_MCP = 9

    /** Um dedo é "estendido" se a ponta estiver claramente mais longe do punho que a articulação PIP. */
    private const val EXTENDED_RATIO = 1.1f

    fun distance(a: Landmark, b: Landmark, width: Int, height: Int): Float =
        hypot((a.x - b.x) * width, (a.y - b.y) * height)

    /**
     * Distância entre polegar e indicador dividida pelo tamanho da mão (punho até a base do médio).
     * Dividir pela mão torna o limiar independente da distância até a câmera.
     */
    fun pinchRatio(landmarks: List<Landmark>, width: Int, height: Int): Float {
        val handSize = distance(landmarks[WRIST], landmarks[MIDDLE_MCP], width, height)
        if (handSize <= 0f) return Float.MAX_VALUE
        return distance(landmarks[THUMB_TIP], landmarks[INDEX_TIP], width, height) / handSize
    }

    /** Estado de [indicador, médio, anelar, mindinho]: true se estendido. */
    fun extendedFingers(landmarks: List<Landmark>, width: Int, height: Int): BooleanArray =
        BooleanArray(FINGER_TIPS.size) { i ->
            val tipDistance = distance(landmarks[WRIST], landmarks[FINGER_TIPS[i]], width, height)
            val pipDistance = distance(landmarks[WRIST], landmarks[FINGER_PIPS[i]], width, height)
            tipDistance > pipDistance * EXTENDED_RATIO
        }
}
