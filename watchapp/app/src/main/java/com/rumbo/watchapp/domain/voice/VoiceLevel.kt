package com.rumbo.watchapp.domain.voice

import kotlin.math.log10

/** Cada cuánto se lee el nivel del micrófono mientras se graba. */
const val LEVEL_SAMPLE_MS = 80L

// Lo más alto que da MediaRecorder.getMaxAmplitude (16 bits).
private const val MAX_AMPLITUDE = 32_767.0

// Por debajo de esto (unas 100 de amplitud) es el ruido de una habitación tranquila: nivel 0.
private const val FLOOR_DB = -50.0

/**
 * Nivel de voz de 0 a 1 a partir de la amplitud máxima del micrófono, en escala logarítmica
 * (decibelios) como la percibe el oído: -50 dB o menos es 0 y la amplitud máxima es 1. Así una
 * voz normal (1 000–20 000) ocupa casi todo el rango en vez de quedarse abajo.
 */
fun amplitudeToLevel(amplitude: Int): Float {
    if (amplitude <= 0) return 0f
    val db = 20 * log10(amplitude / MAX_AMPLITUDE)
    return ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Suaviza el nivel para que las barras no salten: sube rápido cuando se habla ([attack]) y baja
 * más despacio en las pausas entre palabras ([release]). Cada valor es la fracción del camino
 * hacia el nivel nuevo que se recorre en una lectura.
 */
class LevelSmoother(private val attack: Float = 0.6f, private val release: Float = 0.25f) {
    private var value = 0f

    fun next(target: Float): Float {
        val rate = if (target > value) attack else release
        value += (target - value) * rate
        return value
    }
}
