package com.rumbo.watchapp.domain.port

/** HIGH es para giro, desvío y llegada: interrumpe siempre a las frases NORMAL. */
enum class SpeechPriority { NORMAL, HIGH }

/**
 * Voz hablada. [speak] no bloquea ni lanza errores: si no se puede hablar, se calla.
 * Una frase nueva corta la que está sonando, salvo que sea NORMAL y la actual HIGH.
 */
interface SpeechOutput {
    fun speak(text: String, priority: SpeechPriority = SpeechPriority.NORMAL)

    fun stop()
}
