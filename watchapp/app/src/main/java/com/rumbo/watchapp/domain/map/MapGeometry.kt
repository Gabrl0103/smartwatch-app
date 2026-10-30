package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Rectángulo visible, en las mismas unidades que los puntos que se recortan. */
data class Viewport(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun contains(point: Point2) = point.x in left..right && point.y in top..bottom
}

/**
 * Gira [point] (x al este, y al norte) para que quien avanza con rumbo [headingDeg] vaya hacia
 * arriba: con rumbo 90 lo que queda al este pasa a estar arriba.
 */
fun rotateToHeading(point: Point2, headingDeg: Double): Point2 {
    val angle = Math.toRadians(headingDeg)
    return Point2(
        x = point.x * cos(angle) - point.y * sin(angle),
        y = point.x * sin(angle) + point.y * cos(angle),
    )
}

/**
 * Lo que se ve de [line] dentro de [viewport]: un trozo por cada vez que entra. Una ruta larga
 * tiene muchos puntos fuera de la vista y así no se dibujan.
 */
fun clipToViewport(line: List<Point2>, viewport: Viewport): List<List<Point2>> {
    if (line.size == 1) return if (viewport.contains(line[0])) listOf(line) else emptyList()
    val pieces = mutableListOf<MutableList<Point2>>()
    var open = false
    for (i in 0 until line.size - 1) {
        val clipped = clipSegment(line[i], line[i + 1], viewport)
        if (clipped == null) {
            open = false
            continue
        }
        if (!open) pieces += mutableListOf(clipped.first)
        pieces.last() += clipped.second
        // Si el segmento salió de la vista, lo que siga empieza otro trozo.
        open = clipped.second == line[i + 1]
    }
    return pieces
}

// Liang-Barsky: recorta el segmento contra cada borde, avanzando sus extremos.
private fun clipSegment(a: Point2, b: Point2, viewport: Viewport): Pair<Point2, Point2>? {
    val dx = b.x - a.x
    val dy = b.y - a.y
    var enter = 0.0
    var leave = 1.0
    val edges = listOf(
        -dx to a.x - viewport.left,
        dx to viewport.right - a.x,
        -dy to a.y - viewport.top,
        dy to viewport.bottom - a.y,
    )
    for ((direction, distance) in edges) {
        if (direction == 0.0) {
            if (distance < 0) return null
            continue
        }
        val t = distance / direction
        if (direction < 0) {
            if (t > leave) return null
            if (t > enter) enter = t
        } else {
            if (t < enter) return null
            if (t < leave) leave = t
        }
    }
    val start = if (enter == 0.0) a else Point2(a.x + enter * dx, a.y + enter * dy)
    val end = if (leave == 1.0) b else Point2(a.x + leave * dx, a.y + leave * dy)
    return start to end
}

/**
 * Parte [line] en lo recorrido y lo que falta a [progressM] del inicio. [along] es la distancia
 * desde el inicio hasta cada punto. El punto de corte queda en los dos trozos, para que se toquen.
 */
fun splitAlong(line: List<Point2>, along: List<Double>, progressM: Double): Pair<List<Point2>, List<Point2>> {
    if (line.size < 2 || progressM <= 0) return emptyList<Point2>() to line
    if (progressM >= along.last()) return line to emptyList()
    val next = along.indexOfFirst { it > progressM }
    val cut = pointBetween(line[next - 1], line[next], along[next - 1], along[next], progressM)
    return (line.take(next) + cut) to (listOf(cut) + line.drop(next))
}

/** El punto de [line] a [progressM] del inicio; null si no tiene puntos. */
fun pointAlong(line: List<Point2>, along: List<Double>, progressM: Double): Point2? {
    if (line.size < 2) return line.firstOrNull()
    val (walked, remaining) = splitAlong(line, along, progressM)
    return remaining.firstOrNull() ?: walked.last()
}

/** Rumbo de la ruta a [progressM] del inicio (0 = norte, 90 = este); 0 si no tiene dirección. */
fun bearingAlong(line: List<Point2>, along: List<Double>, progressM: Double): Double {
    if (line.size < 2) return 0.0
    val next = along.indexOfFirst { it > progressM }.takeIf { it > 0 } ?: if (progressM <= 0) 1 else line.lastIndex
    val from = line[next - 1]
    val to = line[next]
    return (Math.toDegrees(atan2(to.x - from.x, to.y - from.y)) + 360) % 360
}

private fun pointBetween(a: Point2, b: Point2, alongA: Double, alongB: Double, alongM: Double): Point2 {
    val fraction = (alongM - alongA) / (alongB - alongA)
    return Point2(a.x + fraction * (b.x - a.x), a.y + fraction * (b.y - a.y))
}
