package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.map.RouteGeometry
import com.rumbo.watchapp.domain.map.TileRouteGeometry
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.port.Logger
import com.rumbo.watchapp.domain.port.MapTileSource

/**
 * Hasta dónde se ve el mapa de cerca desde la flecha, en píxeles de mundo del zoom 17 (un tile de
 * 256 se dibuja en 256 dp): el círculo de 227 dp visto desde un poco abajo del centro.
 */
const val MAP_VIEW_RADIUS_PX = 128.0

/** Tope de tiles a precargar por ruta: cada uno cuesta 0,25 créditos de Geoapify. */
const val PREFETCH_MAX_TILES = 120

/**
 * Precarga los tiles del corredor de [Route] al empezarla, en el orden en que se recorren, para
 * que el mapa se vea aunque luego falle la red. El ritmo lo pone [tiles]. Lo que no llega se
 * salta: el mapa lo pedirá de nuevo al verlo. Nunca toca la guía.
 */
class PrefetchRouteTiles(
    private val tiles: MapTileSource,
    private val logger: Logger,
    private val maxTiles: Int = PREFETCH_MAX_TILES,
) {
    /** Devuelve cuántos tiles quedaron disponibles. */
    suspend operator fun invoke(route: Route): Int {
        val corridor = TileRouteGeometry(RouteGeometry(route)).corridor(MAP_VIEW_RADIUS_PX, maxTiles)
        val loaded = corridor.count { tiles.load(it) != null }
        logger.info("Mapa", "Precarga: $loaded de ${corridor.size} tiles del corredor")
        return loaded
    }
}
