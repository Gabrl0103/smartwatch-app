package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.application.speech.Phrase
import com.rumbo.watchapp.application.speech.navigationPhrase
import com.rumbo.watchapp.application.speech.repeatPhrase
import com.rumbo.watchapp.application.speech.startPhrase
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.VibrationEvent
import com.rumbo.watchapp.domain.navigation.LocationFilter
import com.rumbo.watchapp.domain.navigation.NavigationEngine
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationSnapshot
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.navigation.NavigationThresholds
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.domain.port.Logger
import com.rumbo.watchapp.domain.port.NoLogger
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.port.VibrationEventsRepository
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "Nav"

/** Lo que muestra la pantalla "En ruta". */
data class TrackingState(
    val status: NavigationStatus = NavigationStatus.ON_ROUTE,
    val destinationName: String = "",
    val nextInstruction: String? = null,
    val nextManeuver: Maneuver? = null,
    val distanceToNextStepM: Double? = null,
    val stepLengthM: Double? = null,
    val remainingM: Double = 0.0,
    val pattern: VibrationPattern? = null,
    val recalculating: Boolean = false,
) {
    /**
     * Avance hacia el siguiente paso, de 0 (recién pasado el anterior) a 1 (encima del giro): la
     * distancia que falta normalizada por el largo del tramo. Al llegar queda en 1.
     */
    val stepProgress: Float
        get() {
            if (status == NavigationStatus.ARRIVED) return 1f
            val toNext = distanceToNextStepM ?: return 0f
            val length = stepLengthM?.takeIf { it > 0 } ?: return 0f
            return (1 - toNext / length).coerceIn(0.0, 1.0).toFloat()
        }
}

/**
 * Une GPS, motor de navegación y vibraciones. Cada aviso vibra con la versión de su patrón que
 * entiende el motor ([VibrationController.supportsAmplitude]) y los ajustes de intensidad y
 * duración de [vibrationSettings], leídos en cada aviso para que un cambio se note enseguida.
 * Cada giro vibra en dos etapas: giro próximo y, en la maniobra, izquierda o derecha. Las
 * lecturas y el ticker corren en el mismo [CoroutineScope]; usar uno de un solo hilo (p. ej.
 * viewModelScope) para no tener carreras.
 * Cada vibración se anota en [vibrationEvents] en segundo plano; si falla, se sigue navegando.
 * La voz ([speech]) solo acompaña: dice el inicio y los avisos importantes sin esperar a que
 * terminen, y si falla las vibraciones siguen igual.
 *
 * Fuera de ruta pide una ruta nueva como mucho cada [NavigationThresholds.recalculateAfterMs] y
 * hasta [NavigationThresholds.maxRecalculations] veces seguidas; si falla, sigue con la anterior
 * y vibra "fuera de ruta". La ruta en curso queda en [activeRoutes] para poder continuarla si el
 * proceso muere, y cada cambio de estado queda en [logger] con la etiqueta "Nav".
 */
class TrackNavigation(
    private val fixes: Flow<LocationFix>,
    private val routes: RoutesRepository,
    private val vibration: VibrationController,
    private val vibrationEvents: VibrationEventsRepository,
    private val speech: SpeechOutput,
    private val clock: () -> Long,
    private val thresholds: NavigationThresholds = NavigationThresholds(),
    private val tickMs: Long = 1_000,
    private val activeRoutes: ActiveRouteStore? = null,
    private val logger: Logger = NoLogger,
    private val vibrationSettings: () -> VibrationSettings = { VibrationSettings() },
) {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private var job: Job? = null
    private var scope: CoroutineScope? = null
    private var route: Route? = null
    private var engine: NavigationEngine? = null
    private var lastPosition: Coordinates? = null
    private var lastVibrationMs = 0L
    private var lastCues: List<VibrationCue> = emptyList()
    private var lastStatus: NavigationStatus? = null
    private var recalculating = false
    private var recalculations = 0
    private var lastRecalculationMs: Long? = null

    fun start(route: Route, scope: CoroutineScope) {
        job?.cancel()
        this.scope = scope
        recalculating = false
        recalculations = 0
        lastRecalculationMs = null
        lastStatus = null
        val now = clock()
        logger.info(TAG, "Navegación iniciada hacia «${route.destinationName}»: ${describe(route)}")
        useRoute(route, now)
        vibrate(VibrationCue.ON_ROUTE, now)
        say(Phrase(startPhrase(route)))
        job = scope.launch {
            launch {
                val filter = LocationFilter(thresholds)
                fixes.collect { raw ->
                    val now = clock()
                    val fix = filter.accept(raw, now) ?: return@collect
                    lastPosition = Coordinates(fix.lat, fix.lng)
                    val current = engine ?: return@collect
                    handle(current.onLocation(fix, now), now)
                    if (current.snapshot.isBackOnRoute()) recalculations = 0
                }
            }
            launch {
                while (isActive) {
                    delay(tickMs)
                    val now = clock()
                    engine?.let { handle(it.onTick(now), now) }
                    heartbeat(now)
                }
            }
        }
    }

    /** Detiene GPS, vibración y voz y olvida la ruta guardada. Llamarlo dos veces no hace nada. */
    fun stop() {
        if (job == null && engine == null) return
        job?.cancel()
        job = null
        scope = null
        recalculating = false
        engine = null
        route = null
        lastStatus = null
        lastCues = emptyList()
        _state.value = TrackingState()
        vibration.stop()
        speech.stop()
        activeRoutes?.clear()
        logger.info(TAG, "Navegación terminada: GPS, vibraciones y voz detenidos")
    }

    /**
     * Botón "Repetir": vuelve a vibrar el último patrón y dice la indicación actual completa,
     * con prioridad alta porque la pidió el usuario. No hace nada si no se está navegando.
     */
    fun repeat() {
        val snapshot = engine?.snapshot ?: return
        logger.info(TAG, "Repetir indicación")
        vibrate(lastCues.ifEmpty { listOf(VibrationCue.ON_ROUTE) }, clock())
        say(repeatPhrase(snapshot))
    }

    private fun CoroutineScope.handle(events: List<NavigationEvent>, now: Long) {
        VibrationPatterns.cuesFor(events).takeIf { it.isNotEmpty() }?.let { vibrate(it, now) }
        engine?.let { navigationPhrase(events, it.snapshot) }?.let(::say)
        events.filterIsInstance<NavigationEvent.NextStepChanged>().forEach {
            logger.info(TAG, "Siguiente paso ${it.step.order}: ${it.step.instruction}")
        }
        // Ya llegó: si el proceso muere ahora no tiene sentido ofrecer continuar.
        if (NavigationEvent.Arrived in events) activeRoutes?.clear()
        if (NavigationEvent.RecalculateRequested in events) recalculate(now)
        publish()
    }

    private fun say(phrase: Phrase) {
        try {
            speech.speak(phrase.text, phrase.priority)
        } catch (e: Exception) {
            // La voz nunca debe detener la navegación.
        }
    }

    // Patrones que se repiten mientras dure el estado (en ruta y sin señal).
    private fun heartbeat(now: Long) {
        val cue = when (engine?.snapshot?.status) {
            NavigationStatus.ON_ROUTE -> VibrationCue.ON_ROUTE
            NavigationStatus.NO_SIGNAL -> VibrationCue.NO_SIGNAL
            else -> null
        } ?: return
        val period = patternFor(listOf(cue)).repeatEveryMs ?: return
        if (now - lastVibrationMs >= period) vibrate(cue, now)
    }

    private fun patternFor(cues: List<VibrationCue>): VibrationPattern =
        VibrationPatterns.forCues(cues, vibration.supportsAmplitude, vibrationSettings())

    private fun vibrate(cue: VibrationCue, now: Long) = vibrate(listOf(cue), now)

    private fun vibrate(cues: List<VibrationCue>, now: Long) {
        val pattern = patternFor(cues)
        vibration.play(pattern)
        lastCues = cues
        lastVibrationMs = now
        _state.value = _state.value.copy(pattern = pattern)
        report(pattern)
    }

    private fun report(pattern: VibrationPattern) {
        val event = VibrationEvent(pattern.label, route?.id, engine?.snapshot?.nextStep?.order)
        scope?.launch {
            try {
                vibrationEvents.record(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Es solo un log para la demo: perderlo no afecta la navegación.
            }
        }
    }

    // De nuevo pegado a la ruta: el próximo desvío vuelve a tener todos sus intentos.
    private fun NavigationSnapshot.isBackOnRoute(): Boolean {
        val distance = distanceToRouteM ?: return false
        return status != NavigationStatus.OFF_ROUTE && distance < thresholds.backOnRouteM
    }

    private fun CoroutineScope.recalculate(now: Long) {
        val from = lastPosition ?: return
        val current = route ?: return
        if (recalculating || recalculations >= thresholds.maxRecalculations) return
        val last = lastRecalculationMs
        if (last != null && now - last < thresholds.recalculateAfterMs) return
        recalculating = true
        recalculations++
        lastRecalculationMs = now
        val attempt = recalculations
        val max = thresholds.maxRecalculations
        logger.info(TAG, "Recalculando ruta (intento $attempt de $max)")
        launch {
            try {
                val end = current.end
                val destination = Place(current.destinationName, end.lat, end.lng)
                val newRoute = routes.create(from, destination)
                useRoute(newRoute, clock())
                logger.info(TAG, "Ruta recalculada: ${describe(newRoute)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val next = if (attempt < max) "el motor lo vuelve a pedir en 10 s" else "no se intenta más"
                logger.warn(TAG, "No se pudo recalcular (intento $attempt de $max): ${e.message}. Sigo con la ruta anterior; $next")
                vibrate(VibrationCue.OFF_ROUTE, clock())
            } finally {
                recalculating = false
                publish()
            }
        }
    }

    private fun useRoute(newRoute: Route, now: Long) {
        route = newRoute
        engine = NavigationEngine(newRoute, now, thresholds)
        activeRoutes?.save(newRoute)
        publish()
    }

    private fun publish() {
        val snapshot = engine?.snapshot ?: return
        if (snapshot.status != lastStatus) {
            logger.info(TAG, "Estado ${lastStatus ?: "-"} → ${snapshot.status} (${describe(snapshot)})")
            lastStatus = snapshot.status
        }
        _state.value = _state.value.copy(
            status = snapshot.status,
            destinationName = route?.destinationName.orEmpty(),
            nextInstruction = snapshot.nextStep?.instruction,
            nextManeuver = snapshot.nextStep?.maneuver,
            distanceToNextStepM = snapshot.distanceToNextStepM,
            stepLengthM = snapshot.stepLengthM,
            remainingM = snapshot.remainingM,
            recalculating = recalculating,
        )
    }

    private fun describe(route: Route) = "${route.steps.size} pasos, ${route.distanceM.roundToInt()} m"

    private fun describe(snapshot: NavigationSnapshot): String = listOfNotNull(
        snapshot.nextStep?.let { step ->
            "paso ${step.order}" + (snapshot.distanceToNextStepM?.let { " a ${it.roundToInt()} m" } ?: "")
        },
        "faltan ${snapshot.remainingM.roundToInt()} m",
        snapshot.distanceToRouteM?.let { "a ${it.roundToInt()} m de la ruta" },
    ).joinToString(", ")
}
