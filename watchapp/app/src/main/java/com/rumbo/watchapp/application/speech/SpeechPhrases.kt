package com.rumbo.watchapp.application.speech

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationSnapshot
import com.rumbo.watchapp.domain.port.SpeechPriority
import kotlin.math.ceil
import kotlin.math.roundToInt

const val OFF_ROUTE_PHRASE = "Te saliste de la ruta. Recalculando."
const val SIGNAL_LOST_PHRASE = "Perdí la señal de GPS."
const val SIGNAL_RECOVERED_PHRASE = "Señal recuperada."
const val ARRIVED_PHRASE = "Llegaste a tu destino."

/** Una frase y con qué prioridad se dice. */
data class Phrase(val text: String, val priority: SpeechPriority = SpeechPriority.NORMAL)

/**
 * Distancia hablada, redondeada para que suene natural: de 10 en 10 bajo 100 m, de 50 en 50
 * bajo 1 km y en décimas de kilómetro después. 34 → "treinta metros"; 1649 → "uno coma seis
 * kilómetros"; 1500 → "un kilómetro y medio".
 */
fun spokenDistance(meters: Double): String {
    val rounded = if (meters < 100) roundTo(meters, 10).coerceAtLeast(10) else roundTo(meters, 50)
    if (rounded < 1000) return countOf(rounded, "metro", "metros")
    val tenths = (meters / 100).roundToInt().coerceAtLeast(10)
    val km = tenths / 10
    return when (val decimal = tenths % 10) {
        0 -> countOf(km, "kilómetro", "kilómetros")
        5 -> countOf(km, "kilómetro", "kilómetros") + " y medio"
        else -> "${numberInWords(km)} coma ${numberInWords(decimal)} kilómetros"
    }
}

/** Minutos a pie redondeados hacia arriba, como en pantalla: 1556 s → "veintiséis minutos". */
fun spokenMinutes(seconds: Double): String =
    countOf(ceil(seconds / 60).toInt().coerceAtLeast(1), "minuto", "minutos")

fun confirmPhrase(route: Route): String =
    "${route.destinationName}, a ${spokenDistance(route.distanceM)}, " +
        "${spokenMinutes(route.durationS)} a pie. ¿Vamos?"

fun startPhrase(route: Route): String {
    val first = route.steps.firstOrNull()?.instruction?.let(::sentence)
    return if (first == null) "Iniciando ruta." else "Iniciando ruta. $first"
}

fun turnApproachingPhrase(distanceM: Double, instruction: String): String =
    "En ${spokenDistance(distanceM)}, ${sentence(instruction).replaceFirstChar { it.lowercase() }}"

fun turnNowPhrase(maneuver: Maneuver): String = when (maneuver) {
    Maneuver.LEFT -> "Gira a la izquierda."
    Maneuver.RIGHT -> "Gira a la derecha."
    Maneuver.STRAIGHT -> "Sigue recto."
    Maneuver.ARRIVE -> ARRIVED_PHRASE
}

/**
 * Qué decir tras una lectura del motor: solo la frase más importante, porque una nueva
 * cortaría a la anterior. Devuelve null si nada amerita hablar.
 */
fun navigationPhrase(events: List<NavigationEvent>, snapshot: NavigationSnapshot): Phrase? {
    if (NavigationEvent.Arrived in events) return Phrase(ARRIVED_PHRASE, SpeechPriority.HIGH)
    if (NavigationEvent.OffRoute in events) return Phrase(OFF_ROUTE_PHRASE, SpeechPriority.HIGH)
    events.filterIsInstance<NavigationEvent.TurnNow>().firstOrNull()?.let {
        return Phrase(turnNowPhrase(it.maneuver), SpeechPriority.HIGH)
    }
    if (events.any { it is NavigationEvent.TurnApproaching }) {
        val step = snapshot.nextStep
        val distance = snapshot.distanceToNextStepM
        if (step != null && distance != null) {
            return Phrase(turnApproachingPhrase(distance, step.instruction), SpeechPriority.HIGH)
        }
    }
    if (NavigationEvent.SignalLost in events) return Phrase(SIGNAL_LOST_PHRASE)
    if (NavigationEvent.SignalRecovered in events) return Phrase(SIGNAL_RECOVERED_PHRASE)
    return null
}

private fun roundTo(value: Double, step: Int): Int = (value / step).roundToInt() * step

// Quita espacios y asegura el punto final para que la voz haga la pausa.
private fun sentence(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.isEmpty() || trimmed.last() in ".?!") trimmed else "$trimmed."
}
