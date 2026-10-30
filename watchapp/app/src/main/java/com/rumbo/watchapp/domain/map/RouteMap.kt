package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.LocalProjection
import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.geo.cumulativeDistances
import com.rumbo.watchapp.domain.geo.stepAlongDistances
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Route
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Lo que muestra la vista cercana delante de la persona. */
const val NEARBY_RADIUS_M = 150.0

/** Vista cercana, con la persona al centro y su rumbo hacia arriba, o la ruta entera, con el norte arriba. */
enum class MapZoom { NEARBY, WHOLE_ROUTE }

/**
 * La pantalla, en píxeles. [margin] es lo que se deja al borde redondo. [topInset] y [bottomInset]
 * son lo que tapan la hora y los textos: la ruta entera se ajusta entre ellos; la vista cercana no,
 * porque la persona va al centro y lo que importa queda delante, arriba.
 */
data class MapView(
    val width: Double,
    val height: Double,
    val margin: Double,
    val topInset: Double = 0.0,
    val bottomInset: Double = 0.0,
) {
    val viewport get() = Viewport(0.0, 0.0, width, height)
}

/** Un cuadro listo para dibujar, en píxeles de pantalla (y hacia abajo). */
data class MapFrame(
    val route: List<List<Point2>> = emptyList(),
    val walked: List<List<Point2>> = emptyList(),
    val remaining: List<List<Point2>> = emptyList(),
    val person: Point2? = null,
    /** Hacia dónde apunta la flecha en pantalla: 0 arriba, 90 a la derecha. */
    val personHeadingDeg: Double = 0.0,
    val nextTurn: Point2? = null,
    val destination: Point2? = null,
)

/** La ruta en metros locales. Se calcula una vez por ruta: el mapa se redibuja muchas veces. */
class RouteGeometry(route: Route) {
    private val coordinates = route.polyline.ifEmpty { route.steps.map { Coordinates(it.lat, it.lng) } }
    private val projection = coordinates.firstOrNull()?.let(::LocalProjection)
    private val cumulative = cumulativeDistances(coordinates)

    val points: List<Point2> = coordinates.map(::project)
    val along: List<Double> = cumulative

    private val turns: List<Pair<Double, Point2>> = if (coordinates.isEmpty()) {
        emptyList()
    } else {
        stepAlongDistances(route.steps, coordinates, cumulative).zip(route.steps)
            .filter { (_, step) -> step.maneuver.isTurn }
            .map { (alongM, step) -> alongM to project(Coordinates(step.lat, step.lng)) }
    }

    fun project(point: Coordinates): Point2 = projection?.toXY(point) ?: Point2(0.0, 0.0)

    /** El primer giro que queda por delante de [progressM]. */
    fun nextTurn(progressM: Double): Point2? = turns.firstOrNull { (alongM, _) -> alongM > progressM }?.second
}

private class Camera(
    private val focus: Point2,
    private val rotationDeg: Double,
    private val scale: Double,
    private val centerX: Double,
    private val centerY: Double,
) {
    fun toScreen(point: Point2): Point2 {
        val rotated = rotateToHeading(Point2(point.x - focus.x, point.y - focus.y), rotationDeg)
        return Point2(centerX + rotated.x * scale, centerY - rotated.y * scale)
    }
}

/**
 * Convierte la ruta y la posición en un cuadro. Sin [position] (aún no hay lectura) la persona se
 * pone sobre la ruta, en [progressM]; sin [headingDeg] (está quieta) se usa el rumbo de la ruta.
 */
fun mapFrame(
    geometry: RouteGeometry,
    position: Coordinates?,
    headingDeg: Double?,
    progressM: Double,
    zoom: MapZoom,
    view: MapView,
): MapFrame {
    val person = position?.let(geometry::project)
        ?: pointAlong(geometry.points, geometry.along, progressM)
        ?: return MapFrame()
    val heading = headingDeg ?: bearingAlong(geometry.points, geometry.along, progressM)
    val camera = when (zoom) {
        MapZoom.NEARBY -> nearbyCamera(person, heading, view)
        MapZoom.WHOLE_ROUTE -> wholeRouteCamera(geometry.points + person, view)
    }
    val (walked, remaining) = splitAlong(geometry.points, geometry.along, progressM)
    val visible = { line: List<Point2> -> clipToViewport(line.map(camera::toScreen), view.viewport) }
    val marker = { point: Point2? -> point?.let(camera::toScreen)?.takeIf(view.viewport::contains) }
    return MapFrame(
        route = visible(geometry.points),
        walked = visible(walked),
        remaining = visible(remaining),
        person = camera.toScreen(person),
        personHeadingDeg = if (zoom == MapZoom.NEARBY) 0.0 else heading,
        nextTurn = marker(geometry.nextTurn(progressM)),
        destination = marker(geometry.points.lastOrNull()),
    )
}

private fun nearbyRadiusPx(view: MapView) = min(view.width, view.height) / 2 - view.margin

private fun nearbyCamera(person: Point2, headingDeg: Double, view: MapView) =
    Camera(person, headingDeg, nearbyRadiusPx(view) / NEARBY_RADIUS_M, view.width / 2, view.height / 2)

// Lo más grande que cabe en la franja libre sin salirse del círculo: en pantalla redonda las esquinas
// no existen, y una ruta ancha puede pasar de lado a lado aunque la franja sea baja.
private fun wholeRouteCamera(points: List<Point2>, view: MapView): Camera {
    val south = points.minOf { it.y }
    val north = points.maxOf { it.y }
    val focus = Point2((points.minOf { it.x } + points.maxOf { it.x }) / 2, (south + north) / 2)
    val freeHeight = view.height - view.topInset - view.bottomInset
    val centerY = view.topInset + freeHeight / 2
    val radius = nearbyRadiusPx(view)
    val inCircle = points.minOf { scaleInsideCircle(it.x - focus.x, it.y - focus.y, centerY - view.height / 2, radius) }
    val inBand = if (north > south) freeHeight / (north - south) else Double.MAX_VALUE
    val scale = min(inCircle, inBand).takeIf { it < Double.MAX_VALUE } ?: (radius / NEARBY_RADIUS_M)
    return Camera(focus, 0.0, scale, view.width / 2, centerY)
}

// La mayor escala con la que un punto a (dx, dy) del foco queda en el círculo, si el foco se dibuja
// [shift] px por debajo del centro: la raíz positiva de (s·dx)² + (shift - s·dy)² = radius².
private fun scaleInsideCircle(dx: Double, dy: Double, shift: Double, radius: Double): Double {
    val squared = dx * dx + dy * dy
    if (squared == 0.0) return Double.MAX_VALUE
    val along = shift * dy
    return (along + sqrt(along * along + squared * max(radius * radius - shift * shift, 0.0))) / squared
}
