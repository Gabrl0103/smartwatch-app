package com.rumbo.watchapp.infrastructure.map

import com.rumbo.watchapp.application.map.RequestPacer
import com.rumbo.watchapp.domain.map.StreetLine
import com.rumbo.watchapp.domain.map.TileId
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.port.Logger
import com.rumbo.watchapp.domain.port.MapTileSource
import com.rumbo.watchapp.infrastructure.network.backendRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Tope de tiles guardados en disco (unos 80 KB cada uno). */
const val MAX_DISK_TILES = 200

/** Tope de tiles de nombres de calle en disco (unos 50 KB cada uno; uno cubre 64 tiles). */
const val MAX_DISK_STREET_TILES = 30

// Un tile no debe frenar nada: si tarda más que esto, se deja y se ve el fondo.
private const val TILE_TIMEOUT_MS = 15_000

// Tras un fallo (sin red, 429, 502) ese pedido no se repite hasta pasado este tiempo.
private const val RETRY_AFTER_FAILURE_MS = 30_000L

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
private const val TAG = "Mapa"

/**
 * Datos del mapa: primero del disco ([dir], con tope y se borran los menos usados) y si no, del
 * backend, que guarda la clave de Geoapify: GET /map/tiles (PNG) y GET /map/streets (nombres).
 * Todas las peticiones pasan por [pacer]. Nunca lanza: lo que falla devuelve null.
 */
class BackendMapTileSource(
    private val dir: File,
    private val pacer: RequestPacer,
    private val clock: () -> Long,
    private val logger: Logger,
) : MapTileSource {
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val tiles = DiskFolder(File(dir, "tiles"), "png", MAX_DISK_TILES)
    private val streetTiles = DiskFolder(File(dir, "streets"), "json", MAX_DISK_STREET_TILES)

    override suspend fun load(tile: TileId): ByteArray? =
        cached(tiles, tile, "/map/tiles/${tile.z}/${tile.x}/${tile.y}.png") { it.isPng() }

    override suspend fun streets(tile: TileId): List<StreetLine>? {
        val bytes = cached(streetTiles, tile, "/map/streets/${tile.z}/${tile.x}/${tile.y}.json") { parseStreets(it) != null }
        return bytes?.let(::parseStreets)
    }

    private suspend fun cached(folder: DiskFolder, tile: TileId, path: String, valid: (ByteArray) -> Boolean): ByteArray? = try {
        folder.read(tile, clock)?.takeIf(valid) ?: download(folder, tile, path, valid)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(TAG, "$path no llegó: ${e.message}")
        failedAt[path] = clock()
        null
    }

    private suspend fun download(folder: DiskFolder, tile: TileId, path: String, valid: (ByteArray) -> Boolean): ByteArray? {
        val failed = failedAt[path]
        if (failed != null && clock() - failed < RETRY_AFTER_FAILURE_MS) return null
        val response = pacer.run { backendRequest("GET", path, timeoutMs = TILE_TIMEOUT_MS, retry = false) }
        val bytes = response.bytes
        if (response.code != 200 || !valid(bytes)) {
            logger.warn(TAG, "$path: el servidor respondió ${response.code}")
            failedAt[path] = clock()
            return null
        }
        failedAt.remove(path)
        folder.write(tile, bytes)
        return bytes
    }
}

/** Una carpeta de archivos por tile con tope: al pasarlo se borran los menos usados. */
private class DiskFolder(private val dir: File, private val extension: String, private val maxFiles: Int) {
    private fun fileOf(tile: TileId) = File(dir, "${tile.z}_${tile.x}_${tile.y}.$extension")

    suspend fun read(tile: TileId, clock: () -> Long): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileOf(tile)
        if (!file.isFile) return@withContext null
        file.setLastModified(clock())
        file.readBytes()
    }

    suspend fun write(tile: TileId, bytes: ByteArray) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileOf(tile)
        val temp = File(dir, "${target.name}.tmp")
        temp.writeBytes(bytes)
        temp.renameTo(target)
        val files = dir.listFiles { file -> file.name.endsWith(".$extension") }.orEmpty()
        if (files.size > maxFiles) {
            files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
        }
    }
}

private fun ByteArray.isPng() = size > PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }

/** {"streets": [{"name", "line": [[lat, lng], ...]}]} → calles; null si no tiene esa forma. */
internal fun parseStreets(bytes: ByteArray): List<StreetLine>? = runCatching {
    val streets = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("streets")
    List(streets.length()) { i ->
        val street = streets.getJSONObject(i)
        val line = street.getJSONArray("line")
        StreetLine(
            name = street.getString("name"),
            points = List(line.length()) { j ->
                val point = line.getJSONArray(j)
                Coordinates(point.getDouble(0), point.getDouble(1))
            },
        )
    }
}.getOrNull()
