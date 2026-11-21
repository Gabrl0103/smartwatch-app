package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates

/**
 * La ruta en píxeles de mundo del zoom del mapa de cerca. Se calcula una vez por ruta, como
 * [RouteGeometry], de la que toma las distancias y los giros.
 */
class TileRouteGeometry(val route: RouteGeometry, val zoom: Int = MAP_TILE_ZOOM) {
    val world: List<Point2> = route.coordinates.map { worldPixel(it, zoom) }

    /** Los tiles del corredor de la ruta, para precargarlos. */
    fun corridor(radius: Double, maxTiles: Int): List<TileId> = corridorTiles(route.coordinates, zoom, radius, maxTiles)
}

/**
 * Lo que dibuja el mapa de cerca, en píxeles de mundo: [center] es la persona y [headingDeg] el
 * rumbo que queda arriba. Quien dibuja resta [center], escala y gira -[headingDeg].
 */
data class TileScene(
    val center: Point2,
    val headingDeg: Double,
    val tiles: List<TileId>,
    val walked: List<Point2>,
    val remaining: List<Point2>,
    val nextTurn: Point2?,
    val destination: Point2?,
    /** Metros de ruta hasta el próximo giro; null si ya no quedan giros. */
    val nextTurnM: Double?,
)

/**
 * Arma la escena del mapa de cerca. Sin [position] (aún no hay lectura) la persona va sobre la
 * ruta en [progressM]; sin [headingDeg] se usa el rumbo de la ruta. [radius] es hasta dónde se ve
 * desde la persona, en píxeles de mundo. Null si la ruta no tiene puntos.
 */
fun tileScene(
    geometry: TileRouteGeometry,
    position: Coordinates?,
    headingDeg: Double?,
    progressM: Double,
    radius: Double,
): TileScene? {
    val route = geometry.route
    val center = position?.let { worldPixel(it, geometry.zoom) }
        ?: pointAlong(geometry.world, route.along, progressM)
        ?: return null
    val (walked, remaining) = splitAlong(geometry.world, route.along, progressM)
    val turn = route.nextTurnAhead(progressM)
    return TileScene(
        center = center,
        headingDeg = headingDeg ?: bearingAlong(route.points, route.along, progressM),
        tiles = tilesAround(center, radius, geometry.zoom),
        walked = walked,
        remaining = remaining,
        nextTurn = turn?.second?.let { worldPixel(it, geometry.zoom) },
        destination = geometry.world.lastOrNull(),
        nextTurnM = turn?.first,
    )
}
