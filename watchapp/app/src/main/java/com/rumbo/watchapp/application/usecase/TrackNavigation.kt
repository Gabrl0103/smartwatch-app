package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.navigation.LocationFilter
import com.rumbo.watchapp.domain.navigation.NavigationEngine
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.navigation.NavigationThresholds
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
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

/** Lo que muestra la pantalla "En ruta". */
data class TrackingState(
    val status: NavigationStatus = NavigationStatus.ON_ROUTE,
    val destinationName: String = "",
    val nextInstruction: String? = null,
    val distanceToNextStepM: Double? = null,
    val remainingM: Double = 0.0,
    val pattern: VibrationPattern? = null,
    val recalculating: Boolean = false,
)

/**
 * Une GPS, motor de navegación y vibraciones. Las lecturas y el ticker corren en el mismo
 * [CoroutineScope]; usar uno de un solo hilo (p. ej. viewModelScope) para no tener carreras.
 */
class TrackNavigation(
    private val fixes: Flow<LocationFix>,
    private val routes: RoutesRepository,
    private val vibration: VibrationController,
    private val clock: () -> Long,
    private val thresholds: NavigationThresholds = NavigationThresholds(),
    private val tickMs: Long = 1_000,
) {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private var job: Job? = null
    private var route: Route? = null
    private var engine: NavigationEngine? = null
    private var lastPosition: Coordinates? = null
    private var lastVibrationMs = 0L
    private var recalculating = false

    fun start(route: Route, scope: CoroutineScope) {
        job?.cancel()
        recalculating = false
        val now = clock()
        useRoute(route, now)
        vibrate(VibrationPatterns.ON_ROUTE, now)
        job = scope.launch {
            launch {
                val filter = LocationFilter(thresholds)
                fixes.collect { raw ->
                    val now = clock()
                    val fix = filter.accept(raw, now) ?: return@collect
                    lastPosition = Coordinates(fix.lat, fix.lng)
                    engine?.let { handle(it.onLocation(fix, now), now) }
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

    fun stop() {
        job?.cancel()
        job = null
        recalculating = false
        engine = null
        route = null
        _state.value = TrackingState()
        vibration.stop()
    }

    private fun CoroutineScope.handle(events: List<NavigationEvent>, now: Long) {
        VibrationPatterns.mostImportant(events)?.let { vibrate(it, now) }
        if (NavigationEvent.RecalculateRequested in events) recalculate()
        publish()
    }

    // Patrones que se repiten mientras dure el estado (en ruta y sin señal).
    private fun heartbeat(now: Long) {
        val pattern = when (engine?.snapshot?.status) {
            NavigationStatus.ON_ROUTE -> VibrationPatterns.ON_ROUTE
            NavigationStatus.NO_SIGNAL -> VibrationPatterns.NO_SIGNAL
            else -> null
        } ?: return
        val period = pattern.repeatEveryMs ?: return
        if (now - lastVibrationMs >= period) vibrate(pattern, now)
    }

    private fun vibrate(pattern: VibrationPattern, now: Long) {
        vibration.play(pattern)
        lastVibrationMs = now
        _state.value = _state.value.copy(pattern = pattern)
    }

    private fun CoroutineScope.recalculate() {
        val from = lastPosition ?: return
        val current = route ?: return
        if (recalculating) return
        recalculating = true
        launch {
            try {
                val end = current.polyline.lastOrNull()
                    ?: current.steps.last().let { Coordinates(it.lat, it.lng) }
                val destination = Place(current.destinationName, end.lat, end.lng)
                useRoute(routes.create(from, destination), clock())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Se sigue con la ruta anterior; el motor vuelve a pedir el recálculo en 10 s.
            } finally {
                recalculating = false
                publish()
            }
        }
    }

    private fun useRoute(newRoute: Route, now: Long) {
        route = newRoute
        engine = NavigationEngine(newRoute, now, thresholds)
        publish()
    }

    private fun publish() {
        val snapshot = engine?.snapshot ?: return
        _state.value = _state.value.copy(
            status = snapshot.status,
            destinationName = route?.destinationName.orEmpty(),
            nextInstruction = snapshot.nextStep?.instruction,
            distanceToNextStepM = snapshot.distanceToNextStepM,
            remainingM = snapshot.remainingM,
            recalculating = recalculating,
        )
    }
}
