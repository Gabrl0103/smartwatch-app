package com.rumbo.watchapp.domain.vibration

import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

    /**
     * El patrón con los ajustes del usuario: cada pulso dura [PulseDuration.factor] veces más (las
     * pausas no cambian) y, si el reloj controla la intensidad, su fuerza se multiplica por
     * [VibrationIntensity.factor] (sin bajar de 1 ni pasar de 255).
     */
    fun adjusted(settings: VibrationSettings, supportsAmplitude: Boolean): VibrationPattern {
        val strength = if (supportsAmplitude) settings.intensity.factor else 1.0
        return copy(
            timingsMs = timingsMs.mapIndexed { i, ms ->
                if (amplitudes[i] > 0) (ms * settings.duration.factor).roundToLong() else ms
            },
            amplitudes = amplitudes.map { if (it > 0) (it * strength).roundToInt().coerceIn(1, 255) else 0 },
        )
    }

    /** Este patrón y, tras [gapMs] de pausa, [next], en una sola onda que no se repite. */
    fun then(next: VibrationPattern, gapMs: Long): VibrationPattern = VibrationPattern(
        name = "$name, luego ${next.name.lowercase()}",
        description = "$description y ${next.description}",
        timingsMs = timingsMs + (gapMs + next.timingsMs.first()) + next.timingsMs.drop(1),
        amplitudes = amplitudes + 0 + next.amplitudes.drop(1),
    )
}

/** Un tramo en que el motor vibra: empieza en [startMs], dura [durationMs] y tiene fuerza [amplitude]. */
data class Pulse(val startMs: Long, val durationMs: Long, val amplitude: Int)
