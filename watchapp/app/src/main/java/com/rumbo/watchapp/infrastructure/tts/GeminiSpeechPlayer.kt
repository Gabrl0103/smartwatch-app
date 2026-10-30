package com.rumbo.watchapp.infrastructure.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.rumbo.watchapp.infrastructure.network.SpeechApiClient
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Voz de Gemini (POST /speech) guardada en el cache del reloj y reproducida con MediaPlayer.
 * Es el respaldo cuando el reloj no tiene voz propia; los errores suben a [SpeechOutputImpl].
 */
class GeminiSpeechPlayer(context: Context, scope: CoroutineScope, clock: () -> Long) : SpeechPlayer {

    private val audio = SpeechAudioStore(
        File(context.applicationContext.cacheDir, "speech"),
        SpeechApiClient::synthesize,
        scope,
        clock,
    )

    override suspend fun play(text: String, onStart: () -> Unit) {
        val file = audio.get(text)
        try {
            playFile(file, onStart)
        } catch (e: IOException) {
            // Un WAV dañado no debe quedarse en el cache.
            file.delete()
            throw e
        }
    }

    override suspend fun prepare(text: String) = audio.prepare(text)

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
