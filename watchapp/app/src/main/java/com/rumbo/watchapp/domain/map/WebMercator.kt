package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.tan

/** Lado de un tile en píxeles de mundo (Web Mercator). Los @2x del backend traen el doble de detalle. */
const val TILE_SIZE = 256.0

/** Zoom fijo del mapa de cerca: a esta escala se leen las calles y su nombre. */
const val MAP_TILE_ZOOM = 17

// Web Mercator no llega a los polos: más allá de esta latitud el mapa no tiene tiles.
private const val MAX_LATITUDE = 85.05112878

private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

/** Un tile del mapa: zoom [z], columna [x] (hacia el este) y fila [y] (hacia el sur). */
data class TileId(val z: Int, val x: Int, val y: Int)

private fun tilesPerSide(zoom: Int) = 1 shl zoom

/**
 * La posición de [point] en píxeles de mundo del [zoom]: x de 0 a 256·2^zoom de oeste a este e y
 * de norte a sur (hacia abajo, como en pantalla).
 */
fun worldPixel(point: Coordinates, zoom: Int): Point2 {
    val size = TILE_SIZE * tilesPerSide(zoom)
    val lat = point.lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
    val x = (point.lng + 180.0) / 360.0 * size
    val y = (1 - asinh(tan(Math.toRadians(lat))) / PI) / 2 * size
    return Point2(x, y)
}

/** El tile del [zoom] que contiene el píxel de mundo [world] (la columna da la vuelta al mundo). */
fun tileAt(world: Point2, zoom: Int): TileId {
    val side = tilesPerSide(zoom)
    val x = Math.floorMod(floor(world.x / TILE_SIZE).toInt(), side)
    val y = floor(world.y / TILE_SIZE).toInt().coerceIn(0, side - 1)
    return TileId(zoom, x, y)
}

/** Esquina de arriba a la izquierda de [tile], en píxeles de mundo. */
fun tileOrigin(tile: TileId) = Point2(tile.x * TILE_SIZE, tile.y * TILE_SIZE)

/** Metros que mide un píxel de mundo del [zoom] a la latitud [lat]. */
fun metersPerWorldPixel(lat: Double, zoom: Int): Double =
    EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(lat)) / (TILE_SIZE * tilesPerSide(zoom))

/**
 * Los tiles que tocan el cuadrado de lado 2·[radius] centrado en [center] (píxeles de mundo):
 * bastan para cubrir un mapa que gira alrededor de [center] y llega hasta [radius] de él. Los
 * más cercanos al centro van primero, para pedirlos antes.
 */
fun tilesAround(center: Point2, radius: Double, zoom: Int): List<TileId> {
    val side = tilesPerSide(zoom)
    val firstX = floor((center.x - radius) / TILE_SIZE).toInt()
    val lastX = ceil((center.x + radius) / TILE_SIZE).toInt() - 1
    val firstY = floor((center.y - radius) / TILE_SIZE).toInt().coerceAtLeast(0)
    val lastY = (ceil((center.y + radius) / TILE_SIZE).toInt() - 1).coerceAtMost(side - 1)
    val cells = mutableListOf<Pair<Int, Int>>()
    for (y in firstY..lastY) for (x in firstX..lastX) cells += x to y
    return cells
        .sortedBy { (x, y) -> hypot((x + 0.5) * TILE_SIZE - center.x, (y + 0.5) * TILE_SIZE - center.y) }
        .map { (x, y) -> TileId(zoom, Math.floorMod(x, side), y) }
        .distinct()
}

/**
 * Los tiles del corredor de la ruta [points]: todo lo que queda a [radius] píxeles de mundo de
 * algún punto de la línea, en el orden en que se recorre y sin repetir, hasta [maxTiles].
 */
fun corridorTiles(points: List<Coordinates>, zoom: Int, radius: Double, maxTiles: Int): List<TileId> {
    if (points.isEmpty() || maxTiles <= 0) return emptyList()
    val world = points.map { worldPixel(it, zoom) }
    // Muestras a menos de un tile entre sí: ninguna celda del corredor queda sin tocar.
    val stepPx = max(radius, 1.0).coerceAtMost(TILE_SIZE / 2)
    val tiles = LinkedHashSet<TileId>()
    fun addAround(point: Point2): Boolean {
        for (tile in tilesAround(point, radius, zoom).sortedWith(compareBy({ it.y }, { it.x }))) {
            tiles += tile
            if (tiles.size >= maxTiles) return false
        }
        return true
    }
    if (!addAround(world.first())) return tiles.toList()
    for (i in 1 until world.size) {
        val from = world[i - 1]
        val to = world[i]
        val samples = max(1, ceil(hypot(to.x - from.x, to.y - from.y) / stepPx).toInt())
        for (s in 1..samples) {
            val t = s.toDouble() / samples
            if (!addAround(Point2(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))) return tiles.toList()
        }
    }
    return tiles.toList()
}

/**
 * Acerca el rumbo mostrado [previousDeg] al nuevo [targetDeg] en la fracción [factor] (0 a 1),
 * por el lado más corto del círculo: de 350 a 10 pasa por 0, no por 180. Así el mapa no salta
 * con cada lectura ruidosa del GPS. Sin rumbo previo toma el nuevo.
 */
fun smoothHeading(previousDeg: Double?, targetDeg: Double, factor: Double): Double {
    if (previousDeg == null) return normalizeDeg(targetDeg)
    val delta = ((targetDeg - previousDeg) % 360 + 540) % 360 - 180
    return normalizeDeg(previousDeg + delta * factor.coerceIn(0.0, 1.0))
}

private fun normalizeDeg(deg: Double) = (deg % 360 + 360) % 360
