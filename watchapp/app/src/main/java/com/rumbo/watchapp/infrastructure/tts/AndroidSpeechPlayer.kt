package com.rumbo.watchapp.infrastructure.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.rumbo.watchapp.infrastructure.network.SpeechApiClient
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TAG = "Rumbo"

/**
 * Voz de Gemini (POST /speech) guardada en el cache del reloj y reproducida con MediaPlayer.
 * Si el servidor o la red fallan, usa [fallback] (TextToSpeech de Android).
 */
class AndroidSpeechPlayer(
    context: Context,
    private val fallback: AndroidTextToSpeech,
) : SpeechPlayer {

    private val cacheDir = File(context.applicationContext.cacheDir, "speech")

    override suspend fun play(text: String) {
        val file = try {
            audioFile(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No pude traer la voz del servidor: ${e.message}")
            null
        }
        if (file != null) {
            try {
                playFile(file)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No pude reproducir la voz guardada: ${e.message}")
                file.delete()
            }
        }
        fallback.speak(text)
    }

    // Reutiliza el WAV si la frase ya se pidió; se escribe aparte y se renombra para no dejar
    // archivos a medias si se cancela.
    private suspend fun audioFile(text: String): File = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "${sha256(text)}.wav")
        if (file.length() > 0) return@withContext file
        val audio = SpeechApiClient.synthesize(text)
        cacheDir.mkdirs()
        val partial = File(cacheDir, "${file.name}.part")
        partial.writeBytes(audio)
        if (!partial.renameTo(file)) throw IOException("No pude guardar el audio")
        file
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine { continuation ->
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
        } catch (e: Exception) {
            player.release()
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    }
}

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
