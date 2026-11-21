package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.map.TileId

/** De dónde salen los tiles del mapa (caché en disco y backend). Solo los usa el mapa, nunca la guía. */
interface MapTileSource {
    /**
     * El PNG de [tile], o null si no llegó (sin red, 429, 502, respuesta rara). Nunca lanza: un
     * tile que falta se ve como fondo oscuro y la ruta se sigue dibujando encima.
     */
    suspend fun load(tile: TileId): ByteArray?
}
