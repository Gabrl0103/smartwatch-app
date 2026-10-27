package com.rumbo.watchapp.domain.vibration

/**
 * Forma de onda de una vibración: [timingsMs] alterna pausa, vibra, pausa, vibra...
 * (empieza con pausa) y [amplitudes] da la fuerza de cada tramo (0 en pausas, 1–255 al vibrar).
 * Si [repeatEveryMs] no es null, se repite con ese periodo mientras dure el estado.
 */
data class VibrationPattern(
    val label: String,
    val timingsMs: List<Long>,
    val amplitudes: List<Int>,
    val repeatEveryMs: Long? = null,
) {
    init {
        require(timingsMs.size == amplitudes.size) { "Cada tramo necesita su amplitud" }
        require(amplitudes.all { it in 0..255 }) { "Amplitudes fuera de 0–255" }
    }

    val pulseDurationsMs: List<Long> get() = timingsMs.filterIndexed { i, _ -> amplitudes[i] > 0 }
    val pulseAmplitudes: List<Int> get() = amplitudes.filter { it > 0 }
    val durationMs: Long get() = timingsMs.sum()
}
