package com.rumbo.watchapp.testing

import com.rumbo.watchapp.domain.geo.bearingDeg
import com.rumbo.watchapp.domain.geo.cumulativeDistances
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.navigation.LocationFilter
import com.rumbo.watchapp.domain.navigation.NavigationEngine
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import java.io.File
import java.time.Instant
import kotlin.math.abs

/** Un punto del GPX con su hora en milisegundos. */
data class GpxPoint(val lat: Double, val lng: Double, val timeMs: Long)

private val TRKPT = Regex("""<trkpt lat="([-\d.]+)" lon="([-\d.]+)"><time>([^<]+)</time></trkpt>""")

// Un giro de calle cambia el rumbo al menos esto entre ~10 m antes y ~10 m después.
private const val TURN_MIN_DEG = 60.0

// Puntos antes y después del vértice para medir el giro (el GPX tiene uno cada ~5 m).
private const val TURN_WINDOW_POINTS = 2

/**
 * Carpeta docs/demo. Gradle la pasa en la propiedad "rumbo.demoDir"; si se corre el test desde
 * el IDE sin ella, se busca relativa al módulo app.
 */
fun demoDir(): File = File(System.getProperty("rumbo.demoDir") ?: "../../docs/demo")

/** Lee los puntos del recorrido (trkpt) de un GPX de docs/demo. */
fun readGpx(name: String): List<GpxPoint> {
    val text = File(demoDir(), name).readText()
    return TRKPT.findAll(text).map { match ->
        val (lat, lng, time) = match.destructured
        GpxPoint(lat.toDouble(), lng.toDouble(), Instant.parse(time).toEpochMilli())
    }.toList().also { require(it.isNotEmpty()) { "$name no tiene puntos" } }
}

/**
 * La ruta del backend detrás de los GPX: el GPX normal es su polilínea (un punto cada ~5 m), y
 * los pasos son los vértices donde la calle gira (izquierda o derecha según el signo), más el
 * inicio y la llegada, como los que devuelve POST /routes.
 */
fun routeFromGpx(points: List<GpxPoint>, destinationName: String = "Parque Bolívar"): Route {
    val polyline = points.map { Coordinates(it.lat, it.lng) }
    val angles = polyline.indices.map { i -> turnAngle(polyline, i) }
    val turns = polyline.indices.filter { i ->
        abs(angles[i]) >= TURN_MIN_DEG &&
            abs(angles[i]) >= abs(angles.getOrElse(i - 1) { 0.0 }) &&
            abs(angles[i]) > abs(angles.getOrElse(i + 1) { 0.0 })
    }
    val stepIndices = listOf(0) + turns + polyline.lastIndex
    val along = cumulativeDistances(polyline)
    val steps = stepIndices.mapIndexed { order, i ->
        val maneuver = when {
            i == 0 -> Maneuver.STRAIGHT
            i == polyline.lastIndex -> Maneuver.ARRIVE
            angles[i] > 0 -> Maneuver.RIGHT
            else -> Maneuver.LEFT
        }
        val next = stepIndices.getOrNull(order + 1)
        RouteStep(
            order = order + 1,
            maneuver = maneuver,
            instruction = "Paso ${order + 1}",
            distanceM = next?.let { along[it] - along[i] } ?: 0.0,
            lat = polyline[i].lat,
            lng = polyline[i].lng,
        )
    }
    return Route(destinationName, along.last(), along.last() / 1.4, steps, polyline)
}

// Cambio de rumbo en el vértice i, de -180 a 180 (positivo = a la derecha); 0 en los extremos.
private fun turnAngle(polyline: List<Coordinates>, i: Int): Double {
    val before = polyline.getOrNull(i - TURN_WINDOW_POINTS) ?: return 0.0
    val after = polyline.getOrNull(i + TURN_WINDOW_POINTS) ?: return 0.0
    val delta = bearingDeg(polyline[i], after) - bearingDeg(before, polyline[i])
    return (delta + 540) % 360 - 180
}

/** Un estado que duró de [fromMs] a [toMs] (inclusive, en segundos enteros). */
data class StatusRun(val status: NavigationStatus, val fromMs: Long, val toMs: Long) {
    val durationMs: Long get() = toMs - fromMs + 1_000
}

/** Lo que pasó al recorrer un GPX: estados segundo a segundo y todos los eventos. */
class Replay(val runs: List<StatusRun>, val events: List<NavigationEvent>) {
    val statuses: List<NavigationStatus> get() = runs.map { it.status }

    fun count(event: NavigationEvent) = events.count { it == event }

    inline fun <reified T : NavigationEvent> countOf() = events.count { it is T }
}

/**
 * Recorre [track] como lo vería el reloj: cada segundo un tic del motor y, si en ese segundo hay
 * un punto del GPX, una lectura (precisión 5 m, sin velocidad ni rumbo: los calcula el filtro).
 * Sigue unos segundos después del último punto para que se vea la llegada.
 */
fun replay(route: Route, track: List<GpxPoint>, accuracyM: Double = 5.0): Replay {
    val startMs = track.first().timeMs
    val endMs = track.last().timeMs + 5_000
    val byTime = track.associateBy { it.timeMs }
    val engine = NavigationEngine(route, startMs)
    val filter = LocationFilter()
    val events = mutableListOf<NavigationEvent>()
    val runs = mutableListOf<StatusRun>()
    for (now in startMs..endMs step 1_000) {
        byTime[now]?.let { point ->
            val raw = LocationFix(point.lat, point.lng, accuracyM, now)
            filter.accept(raw, now)?.let { events += engine.onLocation(it, now) }
        }
        events += engine.onTick(now)
        val status = engine.snapshot.status
        val last = runs.lastOrNull()
        if (last != null && last.status == status) {
            runs[runs.lastIndex] = last.copy(toMs = now)
        } else {
            runs += StatusRun(status, now, now)
        }
    }
    return Replay(runs, events)
}
