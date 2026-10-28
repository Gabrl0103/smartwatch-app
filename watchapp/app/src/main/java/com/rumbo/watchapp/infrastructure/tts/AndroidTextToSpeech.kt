package com.rumbo.watchapp.infrastructure.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "Voz"

// Se usa el primero que el motor tenga; "es" a secas es el último recurso.
private val SPANISH_CANDIDATES = listOf("es-CO", "es-419", "es-US", "es-MX", "es-ES", "es")
    .map(Locale::forLanguageTag)

/**
 * Voz principal: el TextToSpeech del reloj en español. Arranca al crearse y deja en el log
 * (etiqueta "Voz") el motor, los idiomas y el elegido, o por qué no sirve ninguno.
 */
class AndroidTextToSpeech(context: Context) : LocalSpeechPlayer {

    private val _state = MutableStateFlow(LocalVoiceState.STARTING)
    override val state: StateFlow<LocalVoiceState> = _state.asStateFlow()

    // Sin motor instalado Android llama a onInit dentro del propio constructor, antes de
    // asignar este campo; por eso onInit no lo usa cuando falla.
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext, ::onInit)
    }

    override suspend fun play(text: String) {
        val tts = engine?.takeIf { _state.value == LocalVoiceState.READY }
            ?: throw IllegalStateException("La voz del reloj no está lista")
        suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Requerido por la API")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        continuation.resumeWithException(IOException("TextToSpeech falló al hablar"))
                    }
                }
            })
            continuation.invokeOnCancellation { runCatching { tts.stop() } }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IOException("TextToSpeech no aceptó la frase"))
                }
            }
        }
    }

    private fun onInit(status: Int) {
        val tts = engine
        if (status != TextToSpeech.SUCCESS || tts == null) {
            Log.i(TAG, "No hay motor TextToSpeech en el reloj (onInit=$status); se usará Gemini")
            _state.value = LocalVoiceState.UNAVAILABLE
            return
        }
        val installed = tts.engines.joinToString { it.name }.ifEmpty { "ninguno" }
        Log.i(TAG, "Motor: ${tts.defaultEngine}; instalados: $installed")
        val languages = runCatching { tts.availableLanguages?.map { it.toLanguageTag() }?.sorted() }
            .getOrNull()
        Log.i(TAG, "Idiomas disponibles: ${languages ?: "el motor no los informa"}")

        val results = SPANISH_CANDIDATES.associateWith { tts.isLanguageAvailable(it) }
        val chosen = results.entries.firstOrNull { it.value >= TextToSpeech.LANG_AVAILABLE }?.key
        if (chosen != null && tts.setLanguage(chosen) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            Log.i(TAG, "Idioma elegido: ${chosen.toLanguageTag()} (voz ${tts.voice?.name ?: "por defecto"})")
            _state.value = LocalVoiceState.READY
            return
        }
        val reasons = results.entries.joinToString { "${it.key.toLanguageTag()}=${describe(it.value)}" }
        val missingData = results.values.any { it == TextToSpeech.LANG_MISSING_DATA }
        Log.i(TAG, "Ningún español sirve ($reasons); se usará Gemini")
        _state.value = if (missingData) LocalVoiceState.MISSING_DATA else LocalVoiceState.UNAVAILABLE
    }

    private fun describe(result: Int) = when (result) {
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "disponible (variante)"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "disponible (país)"
        TextToSpeech.LANG_AVAILABLE -> "disponible (idioma)"
        TextToSpeech.LANG_MISSING_DATA -> "falta descargar la voz"
        TextToSpeech.LANG_NOT_SUPPORTED -> "no soportado"
        else -> "código $result"
    }
}
