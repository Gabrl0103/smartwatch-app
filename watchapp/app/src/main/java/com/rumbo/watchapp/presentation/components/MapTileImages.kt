package com.rumbo.watchapp.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rumbo.watchapp.domain.map.MAP_TILE_ZOOM
import com.rumbo.watchapp.domain.map.TileId
import com.rumbo.watchapp.domain.map.WorldStreet
import com.rumbo.watchapp.domain.map.streetNamesTile
import com.rumbo.watchapp.domain.map.toWorldStreets
import com.rumbo.watchapp.domain.port.MapTileSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Unos 24 tiles de 512 px en RGB_565 (512 KB cada uno): de sobra para los 4 a 9 que se ven.
private const val MEMORY_BYTES = 12 * 1024 * 1024

// Tiles de nombres de calle en memoria: cada uno cubre 64 tiles del mapa; casi siempre basta uno.
private const val STREET_TILES_IN_MEMORY = 4

/** Los datos del mapa ya listos para dibujar. Sin él (null) el mapa se ve sin calles. */
val LocalMapTiles = staticCompositionLocalOf<MapTileImages?> { null }

/**
 * Imágenes de los tiles (LruCache por bytes) y nombres de calle en memoria. [request] pide en
 * segundo plano a [source] lo que falta para esos tiles; al llegar algo sube [version] y el mapa se
 * redibuja. Se usa desde el hilo principal; decodificar va en otro hilo. Nada de esto lanza a la
 * pantalla.
 */
class MapTileImages(private val source: MapTileSource, private val scope: CoroutineScope) {
    private val images = object : LruCache<TileId, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: TileId, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }
    private val streetTiles = LruCache<TileId, List<WorldStreet>>(STREET_TILES_IN_MEMORY)
    private val loading = mutableSetOf<TileId>()

    /** Cambia cada vez que llega algo: leerlo al dibujar hace que se redibuje. */
    var version by mutableIntStateOf(0)
        private set

    fun image(tile: TileId): ImageBitmap? = images.get(tile)

    /** Las calles con nombre que tocan [tiles], ya cargadas, en píxeles de mundo del zoom del mapa. */
    fun streets(tiles: List<TileId>): List<WorldStreet> =
        tiles.map(::streetNamesTile).distinct().flatMap { streetTiles.get(it).orEmpty() }

    /** Pide lo que falta para dibujar [tiles]: sus imágenes y los nombres de sus calles. */
    fun request(tiles: List<TileId>) {
        tiles.filter { images.get(it) == null }.forEach { tile ->
            launchOnce(tile) {
                val image = source.load(tile)?.let { decode(it) } ?: return@launchOnce
                images.put(tile, image)
                version++
            }
        }
        tiles.map(::streetNamesTile).distinct().filter { streetTiles.get(it) == null }.forEach { tile ->
            launchOnce(tile) {
                val streets = source.streets(tile) ?: return@launchOnce
                streetTiles.put(tile, withContext(Dispatchers.Default) { toWorldStreets(streets, MAP_TILE_ZOOM) })
                version++
            }
        }
    }

    private fun launchOnce(key: TileId, block: suspend () -> Unit) {
        if (!loading.add(key)) return
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Lo roto no se ve; se volverá a pedir al verlo de nuevo.
            } finally {
                loading.remove(key)
            }
        }
    }

    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
