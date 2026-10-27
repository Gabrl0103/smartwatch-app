package com.rumbo.watchapp.domain.geo

import kotlin.math.hypot

/** Punto más cercano de un segmento, su posición (0 = inicio, 1 = fin) y la distancia a él. */
data class SegmentProjection(val point: Point2, val fraction: Double, val distanceM: Double)

fun projectOntoSegment(p: Point2, a: Point2, b: Point2): SegmentProjection {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSq = dx * dx + dy * dy
    val t = if (lengthSq == 0.0) {
        0.0
    } else {
        (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
    }
    val closest = Point2(a.x + t * dx, a.y + t * dy)
    return SegmentProjection(closest, t, hypot(p.x - closest.x, p.y - closest.y))
}
