package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Zoom de los tiles de nombres de calle: uno cubre 64 tiles del mapa de cerca. */
const val STREET_NAMES_ZOOM = 14

/** Una calle con nombre, como la manda el backend. */
data class StreetLine(val name: String, val points: List<Coordinates>)

/** Una calle con nombre en píxeles de mundo del zoom del mapa. */
data class WorldStreet(val name: String, val points: List<Point2>)

/**
 * Un nombre listo para escribir: [position] es su centro en píxeles de pantalla respecto de la
 * flecha y [angleDeg] su giro, siempre entre -90 y 90: el texto nunca queda al revés.
 */
data class StreetLabel(val name: String, val position: Point2, val angleDeg: Double)

/** Un rectángulo de pantalla: lo que ocupa un texto o un botón sobre el mapa. */
data class ScreenBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun overlaps(other: ScreenBox) = left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * Dónde caben los nombres, en píxeles de pantalla respecto de la flecha: dentro del círculo de
 * radio [radius] centrado en [circleCenter], sin tocar lo que ya hay encima ([blocked]: etiqueta,
 * distancia, atribución, botones) y a [personClearance] o más de la flecha.
 */
data class LabelArea(
    val circleCenter: Point2,
    val radius: Double,
    val personClearance: Double,
    val blocked: List<ScreenBox> = emptyList(),
)

/** El tile de nombres de calle que contiene a [tile]. */
fun streetNamesTile(tile: TileId): TileId {
    val shift = (tile.z - STREET_NAMES_ZOOM).coerceAtLeast(0)
    return TileId(tile.z - shift, tile.x shr shift, tile.y shr shift)
}

/** Pasa las calles a píxeles de mundo del [zoom]. */
fun toWorldStreets(streets: List<StreetLine>, zoom: Int): List<WorldStreet> =
    streets.map { street -> WorldStreet(street.name, street.points.map { worldPixel(it, zoom) }) }

/**
 * Elige dónde escribir el nombre de cada calle visible: en el lugar más cercano a la persona
 * donde quepa en [area], girado como la calle en pantalla pero nunca al revés, y sin pisar otro
 * nombre ni la flecha. Una vez por calle y hasta [maxLabels], las más cercanas a la persona primero. [center] y
 * [headingDeg] son los del mapa; [scale], píxeles de pantalla por píxel de mundo; [width] el
 * ancho en pantalla de cada nombre y [height] su alto.
 */
fun placeStreetLabels(
    streets: List<WorldStreet>,
    center: Point2,
    headingDeg: Double,
    scale: Double,
    area: LabelArea,
    width: (String) -> Double,
    height: Double,
    maxLabels: Int,
): List<StreetLabel> {
    val angle = Math.toRadians(headingDeg)
    val cosA = cos(angle)
    val sinA = sin(angle)
    // Gira lo que el mapa: el rumbo queda arriba (y de pantalla hacia abajo).
    fun toScreen(p: Point2): Point2 {
        val dx = (p.x - center.x) * scale
        val dy = (p.y - center.y) * scale
        return Point2(dx * cosA + dy * sinA, -dx * sinA + dy * cosA)
    }

    // Por calle, todos los lugares donde su nombre cabe, del más cercano a la persona al más lejano.
    val options = streets.groupBy { it.name }.map { (name, parts) ->
        val labelWidth = width(name)
        parts.flatMap { part -> part.points.map(::toScreen).zipWithNext() }
            .flatMap { (a, b) -> positionsAlong(a, b, labelWidth) }
            .map { (position, angleDeg) -> StreetLabel(name, position, angleDeg) }
            .map { it to box(it, labelWidth, height) }
            .filter { (_, labelBox) -> fits(labelBox, area) }
            .sortedBy { (label, _) -> hypot(label.position.x, label.position.y) }
    }.filter { it.isNotEmpty() }

    // Primero las calles más cercanas; cada una en su lugar más cercano que no pise otro nombre.
    val placed = mutableListOf<Pair<StreetLabel, ScreenBox>>()
    for (streetOptions in options.sortedBy { hypot(it.first().first.position.x, it.first().first.position.y) }) {
        if (placed.size >= maxLabels) break
        streetOptions.firstOrNull { (_, labelBox) -> placed.none { (_, other) -> other.overlaps(labelBox) } }
            ?.let { placed += it }
    }
    return placed.map { it.first }
}

// Dónde puede ir el centro del nombre a lo largo del tramo a→b sin salirse de él: varias posiciones
// separadas por medio nombre (como mucho 24), con el giro que lo deja derecho.
private fun positionsAlong(a: Point2, b: Point2, labelWidth: Double): List<Pair<Point2, Double>> {
    val length = hypot(b.x - a.x, b.y - a.y)
    val margin = labelWidth * 0.55
    if (length < margin * 2) return emptyList()
    val angle = readableAngle(a, b)
    val usable = length - margin * 2
    val steps = (usable / (labelWidth / 2)).toInt().coerceIn(0, 23)
    return (0..steps).map { i ->
        val t = (margin + if (steps == 0) usable / 2 else usable * i / steps) / length
        Point2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) to angle
    }
}

private fun readableAngle(a: Point2, b: Point2): Double {
    var deg = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
    if (deg > 90) deg -= 180
    if (deg <= -90) deg += 180
    return deg
}

// Caja alineada a los ejes que envuelve el nombre girado.
private fun box(label: StreetLabel, width: Double, height: Double): ScreenBox {
    val rad = Math.toRadians(label.angleDeg)
    val halfW = abs(cos(rad)) * width / 2 + abs(sin(rad)) * height / 2
    val halfH = abs(sin(rad)) * width / 2 + abs(cos(rad)) * height / 2
    val c = label.position
    return ScreenBox(c.x - halfW, c.y - halfH, c.x + halfW, c.y + halfH)
}

private fun fits(box: ScreenBox, area: LabelArea): Boolean {
    if (area.blocked.any(box::overlaps)) return false
    val corners = listOf(box.left to box.top, box.right to box.top, box.left to box.bottom, box.right to box.bottom)
    if (corners.any { (x, y) -> hypot(x - area.circleCenter.x, y - area.circleCenter.y) > area.radius }) return false
    // La flecha (en 0, 0) queda libre.
    val nearestX = 0.0.coerceIn(box.left, box.right)
    val nearestY = 0.0.coerceIn(box.top, box.bottom)
    return hypot(nearestX, nearestY) >= area.personClearance
}
