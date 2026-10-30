package com.rumbo.watchapp.domain.port

import kotlinx.coroutines.flow.StateFlow

/** HIGH es para giro, desvío y llegada: interrumpe siempre a las frases NORMAL. */
enum class SpeechPriority { NORMAL, HIGH }

/** Qué voz se usaría ahora: la del reloj, la de Gemini (backend) o ninguna. */
enum class SpeechEngine { DEVICE, GEMINI, NONE }

/** [deviceVoiceMissing]: el reloj tiene motor de voz pero le falta descargar el español. */
data class SpeechStatus(
    val engine: SpeechEngine = SpeechEngine.GEMINI,
    val deviceVoiceMissing: Boolean = false,
)

/**
 * Voz hablada. [speak] no bloquea ni lanza errores: si no se puede hablar, se calla.
 * Una frase nueva corta la que está sonando, salvo que sea NORMAL y la actual HIGH.
 */
interface SpeechOutput {
    val status: StateFlow<SpeechStatus>

    /** true mientras suena una frase (desde que empieza el audio, no mientras se descarga). */
    val speaking: StateFlow<Boolean>

    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL)

    /**
     * Deja lista una frase que se dirá pronto para que suene sin espera. No suena ni interrumpe
     * nada; si no se puede, no pasa nada: se pedirá al decirla.
     */
    fun prepare(text: String) = Unit

    /** Calla lo que suena y descarta lo que esperaba por [pause]. */
    fun stop()

    /**
     * Mientras se graba una orden: calla lo que suena y lo que se pida decir espera (solo la
     * frase más importante, la última entre las de igual prioridad) hasta [resume].
     */
    fun pause()

    fun resume()
}
