package com.rumbo.watchapp.testing

import com.rumbo.watchapp.domain.geo.LocalProjection
import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.geo.stepAlongDistances
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import java.util.Random
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Los mismos parámetros que tools/route_to_gpx.py.
private const val NOISE_SIGMA_M = 3.0
private const val NOISE_CORRELATION = 0.7
private const val ACCURACY_MEAN_M = 9.0
private const val ACCURACY_SPREAD_M = 3.0
private const val ACCURACY_CORRELATION = 0.9
private const val SPEED_CHANGE_MPS = 0.08

/**
 * Recorrido realista de [route] con el mismo modelo que tools/route_to_gpx.py: una lectura por
 * segundo con ruido de GPS de σ ≈ 3 m por eje correlacionado, velocidad entre 1,0 y 1,6 m/s,
 * pausas de 2 a 5 s en cada paso, precisión de 5 a 15 m, un salto de ~40 m cada ~200 lecturas y
 * una pérdida de señal de 2 a 3 s. La misma [seed] da siempre el mismo recorrido (distinto del
 * de Python con la misma semilla: cada lenguaje tiene su generador).
 */
fun realisticWalk(route: Route, seed: Long, startMs: Long = 0): List<GpxPoint> {
    val random = Random(seed)
    val projection = LocalProjection(route.polyline.first())
    val points = route.polyline.map(projection::toXY)
    val along = points.indices.runningFold(0.0) { total, i ->
        if (i == 0) total else total + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    }.drop(1)
    val total = along.last()
    val crossings = stepAlongDistances(route.steps, route.polyline)
        .filterIndexed { i, s -> route.steps[i].maneuver != Maneuver.ARRIVE && s > 0 && s < total }
        .sorted()
        .toMutableList()

    var s = 0.0
    var second = 0
    var pause = 0
    var speed = 1.1 + 0.3 * random.nextDouble()
    val positions = mutableListOf(s to second)
    while (s < total) {
        second++
        if (pause > 0) {
            pause--
        } else {
            speed = (speed + random.nextGaussian() * SPEED_CHANGE_MPS).coerceIn(1.0, 1.6)
            s = min(total, s + speed)
            if (crossings.isNotEmpty() && s >= crossings.first()) {
                s = crossings.removeAt(0)
                pause = 2 + random.nextInt(4)
                while (crossings.isNotEmpty() && crossings.first() <= s) crossings.removeAt(0)
            }
        }
        positions += s to second
    }

    var noiseX = 0.0
    var noiseY = 0.0
    var accuracyZ = 0.0
    val keep = sqrt(1 - NOISE_CORRELATION * NOISE_CORRELATION)
    val keepAccuracy = sqrt(1 - ACCURACY_CORRELATION * ACCURACY_CORRELATION)
    val readings = positions.map { (distance, t) ->
        noiseX = NOISE_CORRELATION * noiseX + keep * random.nextGaussian() * NOISE_SIGMA_M
        noiseY = NOISE_CORRELATION * noiseY + keep * random.nextGaussian() * NOISE_SIGMA_M
        accuracyZ = ACCURACY_CORRELATION * accuracyZ + keepAccuracy * random.nextGaussian()
        val p = pointAt(points, along, distance)
        val accuracy = (ACCURACY_MEAN_M + ACCURACY_SPREAD_M * accuracyZ).coerceIn(5.0, 15.0)
        Triple(Point2(p.x + noiseX, p.y + noiseY), t, accuracy)
    }.toMutableList()

    // Cada ~200 lecturas una salta ~40 m hacia cualquier lado.
    var i = 150 + random.nextInt(101)
    while (i < readings.lastIndex) {
        val (p, t, accuracy) = readings[i]
        val angle = random.nextDouble() * 2 * Math.PI
        val jump = 35.0 + 10.0 * random.nextDouble()
        readings[i] = Triple(Point2(p.x + jump * cos(angle), p.y + jump * sin(angle)), t, accuracy)
        i += 150 + random.nextInt(101)
    }

    // Una pérdida breve de señal de 2 a 3 s después de la mitad.
    val gapStart = readings[(readings.size * (0.6 + 0.2 * random.nextDouble())).toInt()].second
    val gapSeconds = 2 + random.nextInt(2)
    return readings
        .filterNot { (_, t, _) -> t >= gapStart && t < gapStart + gapSeconds }
        .map { (p, t, accuracy) ->
            val point = projection.toCoordinates(p)
            GpxPoint(point.lat, point.lng, startMs + t * 1_000L, accuracy)
        }
}

private fun pointAt(points: List<Point2>, along: List<Double>, s: Double): Point2 {
    val i = (1 until points.size).firstOrNull { along[it] >= s } ?: points.lastIndex
    val length = along[i] - along[i - 1]
    val f = if (length == 0.0) 0.0 else ((s - along[i - 1]) / length).coerceIn(0.0, 1.0)
    val (a, b) = points[i - 1] to points[i]
    return Point2(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
}
