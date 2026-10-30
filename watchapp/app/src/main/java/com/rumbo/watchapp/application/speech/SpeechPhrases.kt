package com.rumbo.watchapp.application.speech

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationSnapshot
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.SpeechPriority
import kotlin.math.ceil
import kotlin.math.roundToInt

const val OFF_ROUTE_PHRASE = "Te saliste de la ruta. Recalculando."
const val SIGNAL_LOST_PHRASE = "Perdí la señal de GPS."
const val SIGNAL_RECOVERED_PHRASE = "Señal recuperada."
const val ARRIVED_PHRASE = "Llegaste a tu destino."
const val KEEP_GOING_PHRASE = "Sigue la ruta."

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

/**
 * La frase de inicio orienta con la primera instrucción ("Camine hacia el norte") y, si el primer
 * giro queda a más de [aheadMinM], ya hace de su aviso lejano.
 */
fun startPhrase(route: Route, snapshot: NavigationSnapshot, aheadMinM: Double): String {
    val first = route.steps.firstOrNull() ?: return "Iniciando ruta."
    val next = snapshot.nextStep
    val distance = snapshot.distanceToNextStepM
    val ahead = next != null && next != first && next.maneuver.isTurn && distance != null && distance > aheadMinM
    val turn = if (ahead) " En ${spokenDistance(distance!!)}, ${turnAction(next!!.maneuver)}." else ""
    return "Iniciando ruta. ${sentence(first.instruction)}$turn"
}

/** Lo que hay que hacer en la maniobra, sin distancia ni calle: "gira a la derecha". */
private fun turnAction(maneuver: Maneuver): String = when (maneuver) {
    Maneuver.LEFT -> "gira a la izquierda"
    Maneuver.RIGHT -> "gira a la derecha"
    Maneuver.STRAIGHT -> "sigue recto"
    Maneuver.ARRIVE -> "llega a tu destino"
}

/** Etapa lejana, al empezar el tramo: "Sigue recto por Carrera 25. En cien metros, gira a la derecha." */
fun turnAheadPhrase(distanceM: Double, step: RouteStep, currentStreet: String): String {
    val straight = if (currentStreet.isBlank()) "Sigue recto." else "Sigue recto por $currentStreet."
    return "$straight En ${spokenDistance(distanceM)}, ${turnAction(step.maneuver)}."
}

/** Etapa de preparación: "En cincuenta metros, gira a la derecha en Calle 18." */
fun turnApproachingPhrase(distanceM: Double, step: RouteStep): String {
    val street = if (step.street.isBlank()) "" else " en ${step.street}"
    return "En ${spokenDistance(distanceM)}, ${turnAction(step.maneuver)}$street."
}

/** Etapa final, ya encima del giro: "Gira ahora a la derecha." */
fun turnNowPhrase(maneuver: Maneuver): String = when (maneuver) {
    Maneuver.LEFT -> "Gira ahora a la izquierda."
    Maneuver.RIGHT -> "Gira ahora a la derecha."
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
    val step = snapshot.nextStep
    val distance = snapshot.distanceToNextStepM
    if (step != null && distance != null) {
        if (events.any { it is NavigationEvent.TurnApproaching }) {
            return Phrase(turnApproachingPhrase(distance, step), SpeechPriority.HIGH)
        }
        // Prioridad normal: no urge, y si aún suena "Gira ahora" no debe cortarlo.
        if (events.any { it is NavigationEvent.TurnAhead }) {
            return Phrase(turnAheadPhrase(distance, step, snapshot.currentStreet))
        }
    }
    if (NavigationEvent.SignalLost in events) return Phrase(SIGNAL_LOST_PHRASE)
    if (NavigationEvent.SignalRecovered in events) return Phrase(SIGNAL_RECOVERED_PHRASE)
    return null
}

/** Lo que dice "Repetir": la situación actual completa, aunque ya se haya dicho antes. */
fun repeatPhrase(snapshot: NavigationSnapshot): Phrase {
    val step = snapshot.nextStep
    val distance = snapshot.distanceToNextStepM
    val text = when {
        snapshot.status == NavigationStatus.ARRIVED -> ARRIVED_PHRASE
        snapshot.status == NavigationStatus.OFF_ROUTE -> OFF_ROUTE_PHRASE
        snapshot.status == NavigationStatus.NO_SIGNAL -> SIGNAL_LOST_PHRASE
        step == null || distance == null -> KEEP_GOING_PHRASE
        step.maneuver == Maneuver.ARRIVE -> "Tu destino está a ${spokenDistance(distance)}."
        else -> turnApproachingPhrase(distance, step)
    }
    return Phrase(text, SpeechPriority.HIGH)
}

private fun roundTo(value: Double, step: Int): Int = (value / step).roundToInt() * step

// Quita espacios y asegura el punto final para que la voz haga la pausa.
private fun sentence(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.isEmpty() || trimmed.last() in ".?!") trimmed else "$trimmed."
}
