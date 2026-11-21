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
import com.rumbo.watchapp.domain.map.TileId
import com.rumbo.watchapp.domain.port.MapTileSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Unos 24 tiles de 512 px en RGB_565 (512 KB cada uno): de sobra para los 4 a 9 que se ven.
private const val MEMORY_BYTES = 12 * 1024 * 1024

/** Los tiles del mapa ya listos para dibujar. Sin él (null) el mapa se ve sin calles. */
val LocalMapTiles = staticCompositionLocalOf<MapTileImages?> { null }

/**
 * Imágenes de los tiles en memoria (LruCache por bytes). [request] pide en segundo plano a [source]
 * los que faltan; al llegar uno sube [version] y el mapa se redibuja. Se usa desde el hilo
 * principal; decodificar va en otro hilo. Nada de esto lanza a la pantalla.
 */
class MapTileImages(private val source: MapTileSource, private val scope: CoroutineScope) {
    private val images = object : LruCache<TileId, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: TileId, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }
    private val loading = mutableSetOf<TileId>()

    /** Cambia cada vez que llega un tile: leerlo al dibujar hace que se redibuje. */
    var version by mutableIntStateOf(0)
        private set

    fun image(tile: TileId): ImageBitmap? = images.get(tile)

    fun request(tiles: List<TileId>) {
        tiles.filter { images.get(it) == null && loading.add(it) }.forEach { tile ->
            scope.launch {
                try {
                    val image = source.load(tile)?.let { decode(it) }
                    if (image != null) {
                        images.put(tile, image)
                        version++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Un tile roto se ve como fondo; se volverá a pedir al verlo de nuevo.
                } finally {
                    loading.remove(tile)
                }
            }
        }
    }

    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
