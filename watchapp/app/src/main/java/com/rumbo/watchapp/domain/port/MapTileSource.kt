package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.map.StreetLine
import com.rumbo.watchapp.domain.map.TileId

/**
 * De dónde salen los datos del mapa (caché en disco y backend). Solo los usa el mapa, nunca la
 * guía. Nada lanza: lo que no llega (sin red, 429, 502, respuesta rara) devuelve null; un tile que
 * falta se ve como fondo oscuro y la ruta se sigue dibujando encima.
 */
interface MapTileSource {
    /** El PNG de [tile], sin nombres de calle. */
    suspend fun load(tile: TileId): ByteArray?

    /** Las calles con nombre de [tile] (zoom 14), para escribirlos derechos sobre el mapa. */
    suspend fun streets(tile: TileId): List<StreetLine>?
}
