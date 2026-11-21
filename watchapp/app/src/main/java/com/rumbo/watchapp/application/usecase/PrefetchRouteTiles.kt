package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.map.RouteGeometry
import com.rumbo.watchapp.domain.map.TileRouteGeometry
import com.rumbo.watchapp.domain.map.streetNamesTile
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.port.Logger
import com.rumbo.watchapp.domain.port.MapTileSource

/**
 * Hasta dónde se ve el mapa de cerca desde la flecha, en píxeles de mundo del zoom 17 (un tile de
 * 256 se dibuja en 256 dp): el círculo de 227 dp visto desde un poco abajo del centro.
 */
const val MAP_VIEW_RADIUS_PX = 128.0

/** Tope de tiles a precargar por ruta. */
const val PREFETCH_MAX_TILES = 120

/** Lo que dejó listo la precarga: tiles del mapa y tiles de nombres de calle. */
data class PrefetchResult(val tiles: Int, val streetTiles: Int)

/**
 * Precarga el corredor de [Route] al empezarla, en el orden en que se recorre, para que el mapa se
 * vea aunque luego falle la red: los tiles y los nombres de sus calles (pocos: uno cubre 64
 * tiles). El ritmo lo pone [tiles]. Lo que no llega se salta: el mapa lo pedirá de nuevo al verlo.
 * Nunca toca la guía.
 */
class PrefetchRouteTiles(
    private val tiles: MapTileSource,
    private val logger: Logger,
    private val maxTiles: Int = PREFETCH_MAX_TILES,
) {
    suspend operator fun invoke(route: Route): PrefetchResult {
        val corridor = TileRouteGeometry(RouteGeometry(route)).corridor(MAP_VIEW_RADIUS_PX, maxTiles)
        val names = corridor.map(::streetNamesTile).distinct()
        val loadedNames = names.count { tiles.streets(it) != null }
        val loaded = corridor.count { tiles.load(it) != null }
        logger.info("Mapa", "Precarga: $loaded de ${corridor.size} tiles y $loadedNames de ${names.size} de nombres")
        return PrefetchResult(loaded, loadedNames)
    }
}
