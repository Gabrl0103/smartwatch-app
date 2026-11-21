package com.rumbo.watchapp.infrastructure.map

import com.rumbo.watchapp.application.map.RequestPacer
import com.rumbo.watchapp.domain.map.TileId
import com.rumbo.watchapp.domain.port.Logger
import com.rumbo.watchapp.domain.port.MapTileSource
import com.rumbo.watchapp.infrastructure.network.backendRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tope de tiles guardados en disco (unos 60 KB cada uno). */
const val MAX_DISK_TILES = 200

// Un tile no debe frenar nada: si tarda más que esto, se deja y se ve el fondo.
private const val TILE_TIMEOUT_MS = 15_000

// Tras un fallo (sin red, 429, 502) ese tile no se vuelve a pedir hasta pasado este tiempo.
private const val RETRY_AFTER_FAILURE_MS = 30_000L

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
private const val TAG = "Mapa"

/**
 * Tiles del mapa: primero del disco ([dir], hasta [maxTiles], se borran los menos usados) y si no,
 * de GET /map/tiles en el backend, que guarda la clave de Geoapify. Todas las peticiones pasan por
 * [pacer]. Nunca lanza: lo que falla devuelve null.
 */
class BackendMapTileSource(
    private val dir: File,
    private val pacer: RequestPacer,
    private val clock: () -> Long,
    private val logger: Logger,
    private val maxTiles: Int = MAX_DISK_TILES,
) : MapTileSource {
    private val failedAt = ConcurrentHashMap<TileId, Long>()

    override suspend fun load(tile: TileId): ByteArray? = try {
        readDisk(tile) ?: download(tile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(TAG, "Tile ${tile.z}/${tile.x}/${tile.y} no llegó: ${e.message}")
        failedAt[tile] = clock()
        null
    }

    private suspend fun download(tile: TileId): ByteArray? {
        val failed = failedAt[tile]
        if (failed != null && clock() - failed < RETRY_AFTER_FAILURE_MS) return null
        val response = pacer.run {
            backendRequest("GET", "/map/tiles/${tile.z}/${tile.x}/${tile.y}.png", timeoutMs = TILE_TIMEOUT_MS, retry = false)
        }
        val bytes = response.bytes
        if (response.code != 200 || !bytes.isPng()) {
            logger.warn(TAG, "Tile ${tile.z}/${tile.x}/${tile.y}: el servidor respondió ${response.code}")
            failedAt[tile] = clock()
            return null
        }
        failedAt.remove(tile)
        writeDisk(tile, bytes)
        return bytes
    }

    private suspend fun readDisk(tile: TileId): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileOf(tile)
        if (!file.isFile) return@withContext null
        file.setLastModified(clock())
        file.readBytes().takeIf { it.isPng() }
    }

    private suspend fun writeDisk(tile: TileId, bytes: ByteArray) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val temp = File(dir, "${fileOf(tile).name}.tmp")
        temp.writeBytes(bytes)
        temp.renameTo(fileOf(tile))
        val files = dir.listFiles { file -> file.name.endsWith(".png") }.orEmpty()
        if (files.size > maxTiles) {
            files.sortedBy { it.lastModified() }.take(files.size - maxTiles).forEach { it.delete() }
        }
    }

    private fun fileOf(tile: TileId) = File(dir, "${tile.z}_${tile.x}_${tile.y}.png")
}

private fun ByteArray.isPng() = size > PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }
