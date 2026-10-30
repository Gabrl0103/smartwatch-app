package com.rumbo.watchapp.application.speech

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.model.Side
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationSnapshot
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.navigation.NavigationThresholds
import com.rumbo.watchapp.domain.port.SpeechPriority
import kotlin.math.ceil
import kotlin.math.roundToInt

const val OFF_ROUTE_PHRASE = "Te saliste de la ruta. Recalculando."
const val SIGNAL_LOST_PHRASE = "Perdí la señal de GPS."
const val SIGNAL_RECOVERED_PHRASE = "Señal recuperada."
const val ARRIVED_PHRASE = "Llegaste a tu destino."
const val KEEP_GOING_PHRASE = "Sigue la ruta."
const val DESTINATION_SAVED_PHRASE = "Este destino está guardado."
const val DESTINATION_NOT_SAVED_PHRASE = "Este destino no está guardado."
const val DESTINATION_ALREADY_SAVED_PHRASE = "Este destino ya estaba guardado."
const val DESTINATION_JUST_SAVED_PHRASE = "Destino guardado."
const val DESTINATION_DROPPED_PHRASE = "Destino cancelado."
const val FINISH_QUESTION_PHRASE = "¿Terminar la ruta? Toca Terminar o Seguir navegando."

// Lo que en un paso desconocido puede ser un peligro para quien camina sin ver.
private val ATTENTION_WORDS = setOf(
    "escalera", "escaleras", "escalón", "escalon", "escalones", "puente", "puentes",
    "túnel", "tunel", "túneles", "tuneles", "cruce", "cruces",
)
private val NOT_A_LETTER = Regex("[^\\p{L}]+")

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

/** "¿Cuánto falta?" en Confirmar, antes de salir: la ruta completa. */
fun routeLengthPhrase(route: Route): String =
    "Son ${spokenDistance(route.distanceM)}, ${spokenMinutes(route.durationS)} a pie."

/** "¿Cuánto falta?" en ruta: los minutos salen del paso medio de la ruta, no del GPS. */
fun remainingPhrase(route: Route, remainingM: Double): String {
    val seconds = if (route.distanceM > 0) route.durationS * remainingM / route.distanceM else 0.0
    return "Faltan ${spokenDistance(remainingM)}, ${spokenMinutes(seconds)} a pie."
}

/**
 * La frase de inicio orienta con la primera instrucción ("Camine hacia el norte") y hace de aviso
 * lejano del primer giro o de aviso del destino si ya está cerca, porque el motor no los repite.
 * Que el destino está guardado va al final: si un aviso corta la frase, se pierde eso y no la guía.
 */
fun startPhrase(route: Route, snapshot: NavigationSnapshot, thresholds: NavigationThresholds): String {
    val first = route.steps.firstOrNull() ?: return "Iniciando ruta."
    val next = snapshot.nextStep?.takeIf { it != first }
    val distance = snapshot.distanceToNextStepM
    val upcoming = when {
        next == null || distance == null -> ""
        next.maneuver == Maneuver.ARRIVE && distance <= thresholds.destinationNearM ->
            " ${destinationNearPhrase(distance, next.destinationSide)}"
        next.maneuver.isTurn && distance > thresholds.turnAheadMinM -> " ${inDistance(distance, next)}"
        else -> ""
    }
    val saved = if (route.destinationSaved) " $DESTINATION_SAVED_PHRASE" else ""
    return "Iniciando ruta. ${instructionText(first)}$upcoming$saved"
}

/**
 * El texto del paso tal como lo da el proveedor, para pantalla y voz. Si es un paso desconocido
 * que menciona un peligro, con "Atención:" delante: "Atención: tome las escaleras."
 */
fun instructionText(step: RouteStep): String = withAttention(step.maneuver, step.instruction, sentence(step.instruction))

private fun lowerFirst(text: String) = text.replaceFirstChar { it.lowercase() }

// Solo en pasos desconocidos: en los demás el texto del proveedor no se dice.
private fun withAttention(maneuver: Maneuver, instruction: String, phrase: String): String {
    val mentionsDanger = instruction.lowercase().split(NOT_A_LETTER).any { it in ATTENTION_WORDS }
    return if (maneuver == Maneuver.UNKNOWN && mentionsDanger) "Atención: ${lowerFirst(phrase)}" else phrase
}

/** "Tu destino está en cien metros, a la derecha." Sin lado si el proveedor no lo da. */
fun destinationNearPhrase(distanceM: Double, side: Side?): String {
    val where = when (side) {
        Side.LEFT -> ", a la izquierda"
        Side.RIGHT -> ", a la derecha"
        null -> ""
    }
    return "Tu destino está en ${spokenDistance(distanceM)}$where."
}

/**
 * Lo que hay que hacer en la maniobra, sin distancia ni calle: "gira ligeramente a la derecha". Un
 * paso desconocido solo se sabe por su [instruction], que se dice tal cual: "tome las escaleras".
 */
private fun turnAction(maneuver: Maneuver, roundaboutExit: Int?, instruction: String = ""): String = when (maneuver) {
    Maneuver.LEFT -> "gira a la izquierda"
    Maneuver.RIGHT -> "gira a la derecha"
    Maneuver.SLIGHT_LEFT -> "gira ligeramente a la izquierda"
    Maneuver.SLIGHT_RIGHT -> "gira ligeramente a la derecha"
    Maneuver.SHARP_LEFT -> "gira cerrado a la izquierda"
    Maneuver.SHARP_RIGHT -> "gira cerrado a la derecha"
    Maneuver.U_TURN_LEFT, Maneuver.U_TURN_RIGHT -> "da media vuelta"
    Maneuver.ROUNDABOUT -> when {
        roundaboutExit == null || roundaboutExit < 1 -> "entra a la rotonda"
        else -> "en la rotonda toma ${exitInWords(roundaboutExit)}"
    }
    Maneuver.UNKNOWN -> lowerFirst(instruction.trim().trimEnd('.')).ifEmpty { "sigue con cuidado" }
    Maneuver.STRAIGHT -> "sigue recto"
    Maneuver.ARRIVE -> "llega a tu destino"
}

private fun turnAction(step: RouteStep) = turnAction(step.maneuver, step.roundaboutExit, step.instruction)

private fun straightPhrase(currentStreet: String) =
    if (currentStreet.isBlank()) "Sigue recto." else "Sigue recto por $currentStreet."

private fun inDistance(distanceM: Double, step: RouteStep) =
    withAttention(step.maneuver, step.instruction, "En ${spokenDistance(distanceM)}, ${turnAction(step)}.")

/** Etapa lejana, al empezar el tramo: "Sigue recto por Carrera 25. En cien metros, gira a la derecha." */
fun turnAheadPhrase(distanceM: Double, step: RouteStep, currentStreet: String): String =
    "${straightPhrase(currentStreet)} ${inDistance(distanceM, step)}"

/**
 * Etapa de preparación: "En cincuenta metros, gira a la derecha en Calle 18." En una rotonda no
 * va la calle: la que da el proveedor es el nombre de la propia rotonda. Tampoco en un paso
 * desconocido, que ya tiene su texto. Si el paso une dos giros, la calle es la del segundo: "En
 * cincuenta metros, gira a la derecha. Luego, gira a la izquierda en Calle 18."
 */
fun turnApproachingPhrase(distanceM: Double, step: RouteStep): String {
    val hasStreet = step.street.isNotBlank() && step.maneuver != Maneuver.ROUNDABOUT && step.maneuver != Maneuver.UNKNOWN
    val street = if (hasStreet) " en ${step.street}" else ""
    val start = inDistance(distanceM, step).removeSuffix(".")
    val then = step.thenManeuver ?: return "$start$street."
    return "$start. Luego, ${turnAction(then, null)}$street."
}

/**
 * Etapa final, ya encima del giro, con el giro siguiente si está cerca: "Gira ahora a la derecha.
 * Luego, gira a la izquierda." De un paso desconocido se dice su [instruction].
 */
fun turnNowPhrase(
    maneuver: Maneuver,
    roundaboutExit: Int? = null,
    then: Maneuver? = null,
    instruction: String = "",
): String {
    val action = turnAction(maneuver, roundaboutExit, instruction)
    val now = when (maneuver) {
        Maneuver.STRAIGHT -> "Sigue recto."
        Maneuver.ARRIVE -> ARRIVED_PHRASE
        Maneuver.U_TURN_LEFT, Maneuver.U_TURN_RIGHT -> "Da media vuelta ahora."
        Maneuver.ROUNDABOUT, Maneuver.UNKNOWN -> withAttention(maneuver, instruction, "Ahora, $action.")
        else -> "Gira ahora ${action.removePrefix("gira ")}."
    }
    return if (then == null) now else "$now Luego, ${turnAction(then, null)}."
}

/**
 * Qué decir tras una lectura del motor: solo la frase más importante, porque una nueva
 * cortaría a la anterior. Devuelve null si nada amerita hablar.
 */
fun navigationPhrase(events: List<NavigationEvent>, snapshot: NavigationSnapshot): Phrase? {
    if (NavigationEvent.Arrived in events) return Phrase(ARRIVED_PHRASE, SpeechPriority.HIGH)
    if (NavigationEvent.OffRoute in events) return Phrase(OFF_ROUTE_PHRASE, SpeechPriority.HIGH)
    events.filterIsInstance<NavigationEvent.TurnNow>().firstOrNull()?.let {
        return Phrase(turnNowPhrase(it.maneuver, it.roundaboutExit, it.then, it.instruction), SpeechPriority.HIGH)
    }
    val step = snapshot.nextStep
    val distance = snapshot.distanceToNextStepM
    if (step != null && distance != null) {
        if (events.any { it is NavigationEvent.TurnApproaching }) {
            return Phrase(turnApproachingPhrase(distance, step), SpeechPriority.HIGH)
        }
        if (NavigationEvent.DestinationNear in events) {
            return Phrase(destinationNearPhrase(distance, step.destinationSide), SpeechPriority.HIGH)
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

/**
 * La frase de la etapa que viene después de la actual, para tener su audio listo cuando toque.
 * Lleva la distancia a la que se dirá: si un salto del GPS hace que suene otra, esa se pide al
 * decirla, como si no hubiera precarga. Devuelve null si no hay nada previsible que preparar.
 */
fun upcomingPhrase(snapshot: NavigationSnapshot, thresholds: NavigationThresholds): String? {
    val step = snapshot.nextStep ?: return null
    val distance = snapshot.distanceToNextStepM ?: return null
    val turnNow = turnNowPhrase(step.maneuver, step.roundaboutExit, snapshot.thenManeuver, step.instruction)
    return when (snapshot.status) {
        NavigationStatus.ON_ROUTE -> when {
            step.maneuver == Maneuver.ARRIVE && distance <= thresholds.destinationNearM -> ARRIVED_PHRASE
            // Un giro a pocos metros del inicio pasa directo a "Gira ahora".
            step.maneuver.isTurn && distance <= thresholds.maneuverM -> turnNow
            else -> firstNoticeOf(step, distance, thresholds)
        }
        NavigationStatus.TURN_APPROACHING -> turnNow
        NavigationStatus.MANEUVER -> {
            val following = snapshot.followingStep ?: return null
            val legM = snapshot.followingLegM ?: return null
            if (following.maneuver.isTurn && legM > thresholds.turnAheadMinM) {
                turnAheadPhrase(legM, following, step.street)
            } else {
                firstNoticeOf(following, legM, thresholds)
            }
        }
        NavigationStatus.OFF_ROUTE, NavigationStatus.NO_SIGNAL, NavigationStatus.ARRIVED -> null
    }
}

// El primer aviso que sonará de [step] al acercarse a él, sin el lejano.
private fun firstNoticeOf(step: RouteStep, distanceM: Double, thresholds: NavigationThresholds): String? = when {
    step.maneuver == Maneuver.ARRIVE ->
        destinationNearPhrase(minOf(distanceM, thresholds.destinationNearM), step.destinationSide)
    step.maneuver.isTurn -> turnApproachingPhrase(minOf(distanceM, thresholds.turnApproachMinM), step)
    else -> null
}

/**
 * Lo que dice "Repetir": la indicación de la etapa en la que se va, con la distancia de este
 * momento. No se guarda la última frase tal cual porque envejece: un "Gira ahora" repetido después
 * de doblar haría girar otra vez, y un "En cincuenta metros" puede estar ya a veinte.
 */
fun repeatPhrase(snapshot: NavigationSnapshot): Phrase {
    val step = snapshot.nextStep
    val distance = snapshot.distanceToNextStepM
    val text = when {
        snapshot.status == NavigationStatus.ARRIVED -> ARRIVED_PHRASE
        snapshot.status == NavigationStatus.OFF_ROUTE -> OFF_ROUTE_PHRASE
        snapshot.status == NavigationStatus.NO_SIGNAL -> SIGNAL_LOST_PHRASE
        step == null || distance == null -> KEEP_GOING_PHRASE
        step.maneuver == Maneuver.ARRIVE -> destinationNearPhrase(distance, step.destinationSide)
        !step.maneuver.isTurn -> straightPhrase(snapshot.currentStreet)
        snapshot.status == NavigationStatus.MANEUVER ->
            turnNowPhrase(step.maneuver, step.roundaboutExit, snapshot.thenManeuver, step.instruction)
        snapshot.status == NavigationStatus.TURN_APPROACHING -> turnApproachingPhrase(distance, step)
        else -> turnAheadPhrase(distance, step, snapshot.currentStreet)
    }
    return Phrase(text, SpeechPriority.HIGH)
}

private fun roundTo(value: Double, step: Int): Int = (value / step).roundToInt() * step

// Quita espacios y asegura el punto final para que la voz haga la pausa.
private fun sentence(text: String): String {
    val trimmed = text.trim()
    return if (trimmed.isEmpty() || trimmed.last() in ".?!") trimmed else "$trimmed."
}
