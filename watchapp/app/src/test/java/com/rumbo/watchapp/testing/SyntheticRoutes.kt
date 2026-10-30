package com.rumbo.watchapp.testing

import com.rumbo.watchapp.domain.geo.LocalProjection
import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep

/** Punto de referencia de las rutas sintéticas (centro de Pasto). */
val BASE = Coordinates(1.2136, -77.2811)

private val projection = LocalProjection(BASE)

/** Punto a [eastM] metros al este y [northM] metros al norte de [BASE]. */
fun at(eastM: Double, northM: Double): Coordinates = projection.toCoordinates(Point2(eastM, northM))

fun fixAt(
    eastM: Double,
    northM: Double,
    timeMs: Long,
    accuracyM: Double = 5.0,
    speedMps: Double? = 1.4,
): LocationFix {
    val point = at(eastM, northM)
    return LocationFix(point.lat, point.lng, accuracyM, timeMs, speedMps)
}

/** Ruta en L: 200 m al norte, giro a la derecha y 200 m al este; un punto cada 10 m. */
fun lRoute(): Route {
    val north = (0..20).map { at(0.0, it * 10.0) }
    val east = (1..20).map { at(it * 10.0, 200.0) }
    fun step(order: Int, maneuver: Maneuver, text: String, distance: Double, point: Coordinates) =
        RouteStep(order, maneuver, text, distance, point.lat, point.lng)
    return Route(
        destinationName = "Parque de prueba",
        distanceM = 400.0,
        durationS = 300.0,
        steps = listOf(
            step(1, Maneuver.STRAIGHT, "Camine hacia el norte.", 200.0, at(0.0, 0.0)),
            step(2, Maneuver.RIGHT, "Gire a la derecha.", 200.0, at(0.0, 200.0)),
            step(3, Maneuver.ARRIVE, "Ha llegado.", 0.0, at(200.0, 200.0)),
        ),
        polyline = north + east,
    )
}

/**
 * Ruta con dos giros: 200 m al norte por Carrera 1, derecha, [eastM] al este por Carrera 2,
 * izquierda y 200 m más al norte por Calle 3 hasta el destino; un punto cada 5 o 10 m.
 */
fun uRoute(eastM: Double = 400.0): Route {
    val north = (0..20).map { at(0.0, it * 10.0) }
    val east = (1..(eastM / 5).toInt()).map { at(it * 5.0, 200.0) }
    val last = (1..20).map { at(eastM, 200.0 + it * 10.0) }
    fun step(order: Int, maneuver: Maneuver, text: String, distance: Double, point: Coordinates, street: String) =
        RouteStep(order, maneuver, text, distance, point.lat, point.lng, street)
    return Route(
        destinationName = "Parque de prueba",
        distanceM = 400.0 + eastM,
        durationS = 300.0,
        steps = listOf(
            step(1, Maneuver.STRAIGHT, "Camine hacia el norte.", 200.0, at(0.0, 0.0), "Carrera 1"),
            step(2, Maneuver.RIGHT, "Gire a la derecha.", eastM, at(0.0, 200.0), "Carrera 2"),
            step(3, Maneuver.LEFT, "Gire a la izquierda.", 200.0, at(eastM, 200.0), "Calle 3"),
            step(4, Maneuver.ARRIVE, "Ha llegado.", 0.0, at(eastM, 400.0), ""),
        ),
        polyline = north + east + last,
    )
}
