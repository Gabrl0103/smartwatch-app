package com.rumbo.watchapp.domain.vibration

/**
 * Forma de onda de una vibración: [timingsMs] alterna pausa, vibra, pausa, vibra...
 * (empieza con pausa) y [amplitudes] da la fuerza de cada tramo (0 en pausas, 1–255 al vibrar).
 * Si [repeatEveryMs] no es null, se repite con ese periodo mientras dure el estado.
 * [name] es el aviso ("Izquierda") y [description] cómo se siente ("2 pulsos medios").
 */
data class VibrationPattern(
    val name: String,
    val description: String,
    val timingsMs: List<Long>,
    val amplitudes: List<Int>,
    val repeatEveryMs: Long? = null,
) {
    init {
        require(timingsMs.size == amplitudes.size) { "Cada tramo necesita su amplitud" }
        require(amplitudes.all { it in 0..255 }) { "Amplitudes fuera de 0–255" }
    }

    /** "Izquierda: 2 pulsos medios": lo que se muestra, se registra y lee TalkBack. */
    val label: String get() = "$name: $description"

    val pulseDurationsMs: List<Long> get() = timingsMs.filterIndexed { i, _ -> amplitudes[i] > 0 }
    val pulseAmplitudes: List<Int> get() = amplitudes.filter { it > 0 }
    val durationMs: Long get() = timingsMs.sum()

    /** Cada pulso con su inicio dentro del patrón; sirve para dibujarlo al ritmo real. */
    val pulses: List<Pulse>
        get() {
            var startMs = 0L
            return timingsMs.indices.mapNotNull { i ->
                val pulse = if (amplitudes[i] > 0) Pulse(startMs, timingsMs[i], amplitudes[i]) else null
                startMs += timingsMs[i]
                pulse
            }
        }

    /** Pausas entre un pulso y el siguiente (sin la pausa inicial); junto con los pulsos, el ritmo. */
    val gapsMs: List<Long>
        get() = pulses.zipWithNext { a, b -> b.startMs - (a.startMs + a.durationMs) }
}

/** Un tramo en que el motor vibra: empieza en [startMs], dura [durationMs] y tiene fuerza [amplitude]. */
data class Pulse(val startMs: Long, val durationMs: Long, val amplitude: Int)
