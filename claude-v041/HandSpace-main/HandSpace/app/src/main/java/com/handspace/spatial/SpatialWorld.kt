package com.handspace.spatial

import com.handspace.tracking.Handedness

enum class WorldKind { BUTTON, PANEL, SPHERE }

/** Ações do menu espacial. A ordem define a ordem dos botões. */
enum class MenuAction { HOME, OBJECTS, SETTINGS, TRACKING, CAMERA }

/**
 * Objeto do mundo virtual, em metros. [defaultCenter] é a posição de reset.
 * Botões e painéis são planos paralelos ao sensor; esferas são esferas.
 */
class WorldObject(
    val id: String,
    val kind: WorldKind,
    val defaultCenter: Vec3,
    val halfWidth: Float = 0f,
    val halfHeight: Float = 0f,
    val radius: Float = 0f,
    val action: MenuAction? = null,
) {
    var center: Vec3 = defaultCenter
    var scale: Float = 1f
    var visible: Boolean = true

    /** Mão que está segurando o objeto como principal (ver SpatialWorldController). */
    var heldBy: Handedness? = null
    var pressedAtMs: Long = NO_PRESS

    val draggable: Boolean get() = kind != WorldKind.BUTTON

    /** Retorna a distância ao longo do raio até o objeto, ou null se o raio não o atinge. */
    fun hit(ray: Ray): Float? = when (kind) {
        WorldKind.SPHERE -> SpatialGeometry.intersectSphere(ray, center, radius * scale)
        WorldKind.PANEL, WorldKind.BUTTON -> SpatialGeometry.hitPanel(ray, panel())?.distance
    }

    fun panel(): Panel3D = Panel3D(center, RIGHT, UP, halfWidth * scale, halfHeight * scale)

    companion object {
        const val NO_PRESS = Long.MIN_VALUE
        private val RIGHT = Vec3(1f, 0f, 0f)
        private val UP = Vec3(0f, 1f, 0f)
    }
}

data class WorldHit(val obj: WorldObject, val distance: Float)

class SpatialWorld(initial: List<WorldObject>) {

    private val all: List<WorldObject> = initial.toList()

    val objects: List<WorldObject> get() = all

    fun get(id: String): WorldObject? = all.firstOrNull { it.id == id }

    /** Objeto visível mais próximo atingido pelo raio, ou null. */
    fun pick(ray: Ray): WorldHit? {
        var best: WorldHit? = null
        for (o in all) {
            if (!o.visible) continue
            val t = o.hit(ray) ?: continue
            if (best == null || t < best.distance) best = WorldHit(o, t)
        }
        return best
    }

    /** Volta posições e escalas ao padrão. Não altera visibilidade. */
    fun resetLayout() {
        for (o in all) {
            o.center = o.defaultCenter
            o.scale = 1f
        }
    }
}

/** Mundo da demonstração. Posições calculadas para caber na tela em formato retrato. */
object DemoWorld {
    const val INFO_PANEL_ID = "world_info"
    const val SPHERE_ID = "world_sphere"

    fun create(): SpatialWorld {
        val buttonZ = 0.75f
        val buttons = MenuAction.values().mapIndexed { i, action ->
            WorldObject(
                id = "menu_${action.name.lowercase()}",
                kind = WorldKind.BUTTON,
                defaultCenter = Vec3(-0.16f + i * 0.08f, -0.22f, buttonZ),
                halfWidth = 0.034f,
                halfHeight = 0.03f,
                action = action,
            )
        }
        val info = WorldObject(
            id = INFO_PANEL_ID,
            kind = WorldKind.PANEL,
            defaultCenter = Vec3(-0.10f, 0.22f, 0.9f),
            halfWidth = 0.10f,
            halfHeight = 0.10f,
        )
        val sphere = WorldObject(
            id = SPHERE_ID,
            kind = WorldKind.SPHERE,
            defaultCenter = Vec3(0.12f, 0.02f, 0.85f),
            radius = 0.06f,
        )
        return SpatialWorld(buttons + listOf(info, sphere))
    }
}
