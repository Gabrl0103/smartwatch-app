package com.rumbo.watchapp.infrastructure.tts

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

// Gemini gratis acepta 3 frases por minuto y 10 por día. La precarga solo usa 2 por minuto: así una
// frase que hay que decir ya siempre encuentra lugar, y precargar no le quita la voz a un aviso.
internal const val PREPARE_MAX_REQUESTS = 2
internal const val REQUEST_WINDOW_MS = 60_000L

/**
 * Los audios de Gemini guardados en [dir]. Con tan pocas peticiones por minuto, cada una cuenta:
 * lo guardado no se vuelve a pedir, y si una frase se pide mientras ya viene en camino (la precarga
 * y luego decirla), se espera esa misma descarga. Corre en [scope], no en quien la pidió: si se
 * corta la frase, el audio llega igual y queda para la próxima. [scope] debe ser un SupervisorJob.
 */
class SpeechAudioStore(
    private val dir: File,
    private val download: suspend (String) -> ByteArray,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val downloads = mutableMapOf<String, Deferred<File>>()
    private val requestsAtMs = ArrayDeque<Long>()
    private var staleFilesRemoved = false

    /** El audio para decir [text] ya: si no está, se pide sin mirar el presupuesto. */
    suspend fun get(text: String): File = withContext(io) {
        cached(text) ?: fetch(text).await()
    }

    /** Deja listo el audio si hay presupuesto; si no, se pedirá al decir la frase. */
    suspend fun prepare(text: String) {
        withContext(io) {
            if (cached(text) != null) return@withContext
            startIfThereIsRoom(text)?.await()
        }
    }

    private fun cached(text: String): File? {
        removeStaleFiles()
        return File(dir, speechCacheFileName(text)).takeIf { it.length() > 0 }
    }

    @Synchronized
    private fun startIfThereIsRoom(text: String): Deferred<File>? {
        if (text in downloads) return null
        val now = clock()
        while (requestsAtMs.isNotEmpty() && now - requestsAtMs.first() >= REQUEST_WINDOW_MS) requestsAtMs.removeFirst()
        return if (requestsAtMs.size < PREPARE_MAX_REQUESTS) fetch(text) else null
    }

    @Synchronized
    private fun fetch(text: String): Deferred<File> = downloads.getOrPut(text) {
        requestsAtMs.addLast(clock())
        scope.async(io) {
            try {
                save(text, download(text))
            } finally {
                forget(text)
            }
        }
    }

    @Synchronized
    private fun forget(text: String) {
        downloads.remove(text)
    }

    // Se escribe aparte y se renombra para no dejar un audio a medias con el nombre bueno.
    private fun save(text: String, audio: ByteArray): File {
        val file = File(dir, speechCacheFileName(text))
        dir.mkdirs()
        val partial = File(dir, "${file.name}$SPEECH_PARTIAL_SUFFIX")
        partial.writeBytes(audio)
        if (!partial.renameTo(file)) throw IOException("No pude guardar el audio")
        return file
    }

    // Una vez por proceso: borra los audios de versiones anteriores y los que quedaron a medias.
    private fun removeStaleFiles() {
        if (staleFilesRemoved) return
        staleFilesRemoved = true
        dir.listFiles()?.filter { isStaleSpeechFile(it.name) }?.forEach { it.delete() }
    }
}
