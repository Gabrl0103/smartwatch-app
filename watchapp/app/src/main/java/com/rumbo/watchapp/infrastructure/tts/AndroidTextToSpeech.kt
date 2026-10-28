package com.rumbo.watchapp.infrastructure.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Rumbo"
private const val INIT_TIMEOUT_MS = 5_000L
private val SPANISH = Locale.forLanguageTag("es-CO")

/**
 * Respaldo sin red: el TextToSpeech del sistema en es-CO. El emulador Wear OS no trae motor;
 * si no hay motor o no habla español, no dice nada y deja un aviso en el log.
 */
class AndroidTextToSpeech(context: Context) {

    private val appContext = context.applicationContext
    private val ready = CompletableDeferred<Boolean>()
    private var engine: TextToSpeech? = null

    suspend fun speak(text: String) {
        val tts = engine ?: TextToSpeech(appContext) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }.also { engine = it }
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: false
        if (!initialized || tts.setLanguage(SPANISH) < TextToSpeech.LANG_AVAILABLE) {
            Log.w(TAG, "No hay voz del sistema en español; no se dijo: $text")
            return
        }
        suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Requerido por la API")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                }
            })
            continuation.invokeOnCancellation { runCatching { tts.stop() } }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TextToSpeech no pudo decir la frase")
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
}
