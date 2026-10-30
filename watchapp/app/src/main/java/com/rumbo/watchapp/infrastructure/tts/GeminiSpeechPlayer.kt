package com.rumbo.watchapp.infrastructure.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.rumbo.watchapp.infrastructure.network.SpeechApiClient
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Voz de Gemini (POST /speech) guardada en el cache del reloj y reproducida con MediaPlayer.
 * Es el respaldo cuando el reloj no tiene voz propia; los errores suben a [SpeechOutputImpl].
 */
class GeminiSpeechPlayer(context: Context) : SpeechPlayer {

    private val cacheDir = File(context.applicationContext.cacheDir, "speech")
    private var staleFilesRemoved = false

    override suspend fun play(text: String, onStart: () -> Unit) {
        val file = audioFile(text)
        try {
            playFile(file, onStart)
        } catch (e: IOException) {
            // Un WAV dañado no debe quedarse en el cache.
            file.delete()
            throw e
        }
    }

    override suspend fun prepare(text: String) {
        audioFile(text)
    }

    // Reutiliza el WAV si la frase ya se pidió en esta versión; se escribe aparte y se renombra
    // para no dejar archivos a medias si se cancela. El archivo temporal es propio de cada petición
    // porque la precarga y la reproducción pueden pedir la misma frase a la vez.
    private suspend fun audioFile(text: String): File = withContext(Dispatchers.IO) {
        removeStaleFiles()
        val file = File(cacheDir, speechCacheFileName(text))
        if (file.length() > 0) return@withContext file
        val audio = SpeechApiClient.synthesize(text)
        cacheDir.mkdirs()
        val partial = File.createTempFile(file.name, SPEECH_PARTIAL_SUFFIX, cacheDir)
        partial.writeBytes(audio)
        val saved = partial.renameTo(file)
        partial.delete()
        if (!saved && file.length() == 0L) throw IOException("No pude guardar el audio")
        file
    }

    // Una vez por proceso: borra los audios de versiones anteriores del cache.
    private fun removeStaleFiles() {
        if (staleFilesRemoved) return
        staleFilesRemoved = true
        cacheDir.listFiles()?.filter { isStaleSpeechFile(it.name) }?.forEach { it.delete() }
    }

    private suspend fun playFile(file: File, onStart: () -> Unit) = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        player.setOnCompletionListener {
            it.release()
            if (continuation.isActive) continuation.resume(Unit)
        }
        player.setOnErrorListener { mediaPlayer, what, _ ->
            mediaPlayer.release()
            if (continuation.isActive) continuation.resumeWithException(IOException("MediaPlayer error $what"))
            true
        }
        continuation.invokeOnCancellation {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        try {
            player.setDataSource(file.path)
            player.prepare()
            player.start()
            onStart()
        } catch (e: Exception) {
            player.release()
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    }
}
