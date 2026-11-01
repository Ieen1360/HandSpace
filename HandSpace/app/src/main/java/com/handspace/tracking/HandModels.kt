package com.handspace.tracking

/** Lateralidade reportada pelo MediaPipe. "Left"/"Right" são da imagem processada (espelhada). */
enum class Handedness { LEFT, RIGHT, UNKNOWN }

/** Landmark normalizado: x e y em [0, 1] relativos à imagem; z é a profundidade relativa ao punho. */
data class Landmark(val x: Float, val y: Float, val z: Float)

/** Uma mão rastreada com seus 21 landmarks na ordem oficial do MediaPipe. */
data class TrackedHand(
    val handedness: Handedness,
    val score: Float,
    val landmarks: List<Landmark>,
)

/** Resultado de um frame processado. */
data class HandFrame(
    val timestampMs: Long,
    val hands: List<TrackedHand>,
    val imageWidth: Int,
    val imageHeight: Int,
    /** Tempo entre a captura do frame e a entrega do resultado, em ms. */
    val latencyMs: Long,
)

enum class PresenceEvent { ENTERED, EXITED }

/** Índices dos landmarks conforme a documentação do MediaPipe Hand Landmarker. */
object HandLandmarkIndex {
    const val WRIST = 0
    const val THUMB_TIP = 4
    const val INDEX_MCP = 5
    const val INDEX_TIP = 8
    const val MIDDLE_TIP = 12
    const val RING_TIP = 16
    const val PINKY_TIP = 20
    const val LANDMARK_COUNT = 21

    val FINGERTIPS: IntArray = intArrayOf(THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP)

    /** Pares de landmarks ligados por segmentos para desenhar o esqueleto da mão. */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        // Polegar
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        // Indicador
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        // Médio
        9 to 10, 10 to 11, 11 to 12,
        // Anelar
        13 to 14, 14 to 15, 15 to 16,
        // Mindinho
        17 to 18, 18 to 19, 19 to 20,
        // Palma
        5 to 9, 9 to 13, 13 to 17, 0 to 17,
    )
}
