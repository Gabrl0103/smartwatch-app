package com.rumbo.watchapp.domain.vibration

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.vibration.VibrationCue.ARRIVED
import com.rumbo.watchapp.domain.vibration.VibrationCue.ATTENTION
import com.rumbo.watchapp.domain.vibration.VibrationCue.LEFT
import com.rumbo.watchapp.domain.vibration.VibrationCue.NO_SIGNAL
import com.rumbo.watchapp.domain.vibration.VibrationCue.OFF_ROUTE
import com.rumbo.watchapp.domain.vibration.VibrationCue.ON_ROUTE
import com.rumbo.watchapp.domain.vibration.VibrationCue.RIGHT
import com.rumbo.watchapp.domain.vibration.VibrationCue.TURN_APPROACHING

// "Motor encendido" en la versión sin amplitud: el reloj usa su fuerza por defecto.
private const val ON = 255

/** Pausa entre las dos etapas de un giro cuando vibran juntas: giro próximo y la maniobra. */
const val TWO_STAGE_GAP_MS = 600L

/**
 * Lenguaje de vibraciones de Rumbo. Cada aviso se reconoce solo por su ritmo (número de pulsos,
 * duración y pausas): muy corto 100 ms, corto 150 ms, medio 300 ms, largo 600–700 ms y muy largo 1 s. Hay dos
 * versiones con los mismos ritmos: [WITH_AMPLITUDE] además usa la fuerza (suave en "Recto",
 * creciente en "Llegada") y [WITHOUT_AMPLITUDE] es para relojes que solo encienden y apagan el
 * motor, donde "Recto" pasa a ser un pulso corto para no sentirse como una alerta.
 */
object VibrationPatterns {

    val WITH_AMPLITUDE: Map<VibrationCue, VibrationPattern> = mapOf(
        ON_ROUTE to VibrationPattern(
            ON_ROUTE.title, "1 pulso suave cada 6 s",
            timingsMs = listOf(0, 300),
            amplitudes = listOf(0, 60),
            repeatEveryMs = 6_000,
        ),
        TURN_APPROACHING to VibrationPattern(
            TURN_APPROACHING.title, "2 pulsos muy cortos",
            timingsMs = listOf(0, 100, 150, 100),
            amplitudes = listOf(0, 200, 0, 200),
        ),
        LEFT to VibrationPattern(
            LEFT.title, "2 pulsos medios",
            timingsMs = listOf(0, 300, 200, 300),
            amplitudes = listOf(0, 160, 0, 160),
        ),
        RIGHT to VibrationPattern(
            RIGHT.title, "1 pulso largo",
            timingsMs = listOf(0, 700),
            amplitudes = listOf(0, 160),
        ),
        ATTENTION to VibrationPattern(
            ATTENTION.title, "4 pulsos cortos seguidos",
            timingsMs = listOf(0, 150, 100, 150, 100, 150, 100, 150),
            amplitudes = listOf(0, 220, 0, 220, 0, 220, 0, 220),
        ),
        OFF_ROUTE to VibrationPattern(
            OFF_ROUTE.title, "3 pulsos largos con pausas cortas",
            timingsMs = listOf(0, 600, 150, 600, 150, 600),
            amplitudes = listOf(0, 200, 0, 200, 0, 200),
        ),
        NO_SIGNAL to VibrationPattern(
            NO_SIGNAL.title, "1 pulso muy largo cada 5 s",
            timingsMs = listOf(0, 1_000),
            amplitudes = listOf(0, 100),
            repeatEveryMs = 5_000,
        ),
        ARRIVED to VibrationPattern(
            ARRIVED.title, "5 pulsos crecientes",
            timingsMs = listOf(0, 100, 150, 200, 150, 300, 150, 400, 150, 500),
            amplitudes = listOf(0, 60, 0, 95, 0, 130, 0, 165, 0, 200),
        ),
    )

    val WITHOUT_AMPLITUDE: Map<VibrationCue, VibrationPattern> = mapOf(
        ON_ROUTE to VibrationPattern(
            ON_ROUTE.title, "1 pulso corto cada 6 s",
            timingsMs = listOf(0, 150),
            amplitudes = listOf(0, ON),
            repeatEveryMs = 6_000,
        ),
        TURN_APPROACHING to VibrationPattern(
            TURN_APPROACHING.title, "2 pulsos muy cortos",
            timingsMs = listOf(0, 100, 150, 100),
            amplitudes = listOf(0, ON, 0, ON),
        ),
        LEFT to VibrationPattern(
            LEFT.title, "2 pulsos medios",
            timingsMs = listOf(0, 300, 200, 300),
            amplitudes = listOf(0, ON, 0, ON),
        ),
        RIGHT to VibrationPattern(
            RIGHT.title, "1 pulso largo",
            timingsMs = listOf(0, 700),
            amplitudes = listOf(0, ON),
        ),
        ATTENTION to VibrationPattern(
            ATTENTION.title, "4 pulsos cortos seguidos",
            timingsMs = listOf(0, 150, 100, 150, 100, 150, 100, 150),
            amplitudes = listOf(0, ON, 0, ON, 0, ON, 0, ON),
        ),
        OFF_ROUTE to VibrationPattern(
            OFF_ROUTE.title, "3 pulsos largos con pausas cortas",
            timingsMs = listOf(0, 600, 150, 600, 150, 600),
            amplitudes = listOf(0, ON, 0, ON, 0, ON),
        ),
        NO_SIGNAL to VibrationPattern(
            NO_SIGNAL.title, "1 pulso muy largo cada 5 s",
            timingsMs = listOf(0, 1_000),
            amplitudes = listOf(0, ON),
            repeatEveryMs = 5_000,
        ),
        ARRIVED to VibrationPattern(
            ARRIVED.title, "5 pulsos cada vez más largos",
            timingsMs = listOf(0, 100, 150, 200, 150, 300, 150, 400, 150, 500),
            amplitudes = listOf(0, ON, 0, ON, 0, ON, 0, ON, 0, ON),
        ),
    )

    // Si una lectura dispara varios eventos, vibra solo el más importante.
    private val PRIORITY = listOf(ARRIVED, OFF_ROUTE, LEFT, RIGHT, ATTENTION, TURN_APPROACHING, NO_SIGNAL, ON_ROUTE)

    /** La versión que entiende el motor del reloj. */
    fun forDevice(supportsAmplitude: Boolean): Map<VibrationCue, VibrationPattern> =
        if (supportsAmplitude) WITH_AMPLITUDE else WITHOUT_AMPLITUDE

    fun of(cue: VibrationCue, supportsAmplitude: Boolean): VibrationPattern =
        forDevice(supportsAmplitude).getValue(cue)

    /** El patrón de [cue] como se siente en este reloj con los ajustes del usuario. */
    fun of(cue: VibrationCue, supportsAmplitude: Boolean, settings: VibrationSettings): VibrationPattern =
        of(cue, supportsAmplitude).adjusted(settings, supportsAmplitude)

    fun forDevice(supportsAmplitude: Boolean, settings: VibrationSettings): Map<VibrationCue, VibrationPattern> =
        VibrationCue.entries.associateWith { of(it, supportsAmplitude, settings) }

    /** Los avisos de [cues] seguidos en una sola onda (giro en dos etapas), con los ajustes aplicados. */
    fun forCues(cues: List<VibrationCue>, supportsAmplitude: Boolean, settings: VibrationSettings): VibrationPattern =
        cues.map { of(it, supportsAmplitude, settings) }.reduce { first, second -> first.then(second, TWO_STAGE_GAP_MS) }

    fun forEvent(event: NavigationEvent): VibrationCue? = when (event) {
        // Un paso desconocido no es un giro: vibra "atención" en sus dos etapas.
        is NavigationEvent.TurnApproaching -> if (event.maneuver == Maneuver.UNKNOWN) ATTENTION else TURN_APPROACHING
        is NavigationEvent.TurnNow -> when (event.maneuver) {
            Maneuver.LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT, Maneuver.U_TURN_LEFT -> LEFT
            Maneuver.RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT, Maneuver.U_TURN_RIGHT -> RIGHT
            // La rotonda no tiene lado: se repite el aviso para que la maniobra no pase sin vibrar.
            Maneuver.ROUNDABOUT -> TURN_APPROACHING
            Maneuver.UNKNOWN -> ATTENTION
            Maneuver.STRAIGHT, Maneuver.ARRIVE -> null
        }
        NavigationEvent.OffRoute -> OFF_ROUTE
        NavigationEvent.SignalLost -> NO_SIGNAL
        NavigationEvent.Arrived -> ARRIVED
        NavigationEvent.BackOnRoute, NavigationEvent.SignalRecovered -> ON_ROUTE
        is NavigationEvent.NextStepChanged,
        is NavigationEvent.TurnAhead,
        NavigationEvent.DestinationNear,
        NavigationEvent.RecalculateRequested,
        -> null
    }

    fun mostImportant(events: List<NavigationEvent>): VibrationCue? =
        events.mapNotNull(::forEvent).minByOrNull { PRIORITY.indexOf(it) }

    /**
     * Qué vibrar tras una lectura: el aviso más importante. Cada giro vibra en dos etapas; si el
     * aviso de giro próximo y la maniobra llegan en la misma lectura (un salto del GPS o un tramo
     * corto), vibran los dos, primero el corto de giro próximo y luego izquierda o derecha.
     */
    fun cuesFor(events: List<NavigationEvent>): List<VibrationCue> {
        val main = mostImportant(events) ?: return emptyList()
        val approaching = events.any { it is NavigationEvent.TurnApproaching }
        return if ((main == LEFT || main == RIGHT) && approaching) listOf(TURN_APPROACHING, main) else listOf(main)
    }
}
