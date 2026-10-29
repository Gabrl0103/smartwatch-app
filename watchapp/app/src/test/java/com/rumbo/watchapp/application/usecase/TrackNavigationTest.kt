package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.application.speech.OFF_ROUTE_PHRASE
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.model.VibrationEvent
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.domain.port.Logger
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechPriority
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.port.VibrationEventsRepository
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.testing.FakeVibration
import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.fixAt
import com.rumbo.watchapp.testing.lRoute
import com.rumbo.watchapp.testing.pattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackNavigationTest {


    private class FakeVibrationEvents(private val error: Exception? = null) : VibrationEventsRepository {
        val recorded = mutableListOf<VibrationEvent>()

        override suspend fun record(event: VibrationEvent) {
            error?.let { throw it }
            recorded += event
        }
    }

    private class FakeSpeech(private val error: Exception? = null) : SpeechOutput {
        val spoken = mutableListOf<Pair<String, SpeechPriority>>()
        var stops = 0
        override val status = MutableStateFlow(SpeechStatus())

        override fun speak(text: String, priority: SpeechPriority) {
            error?.let { throw it }
            spoken += text to priority
        }

        override fun stop() {
            stops++
        }
    }

    private class FakeRoutes(private val route: Route) : RoutesRepository {
        var origin: Coordinates? = null
        var destination: Place? = null

        override suspend fun create(origin: Coordinates, destination: Place): Route {
            this.origin = origin
            this.destination = destination
            return route
        }
    }

    /** Ruta nueva que nunca llega: sin internet o con el servidor caído. */
    private class FailingRoutes(private val clock: () -> Long) : RoutesRepository {
        val calledAtMs = mutableListOf<Long>()

        override suspend fun create(origin: Coordinates, destination: Place): Route {
            calledAtMs += clock()
            throw AppError("Sin conexión. Revisa el internet del reloj")
        }
    }

    private class FakeStore : ActiveRouteStore {
        var saved: Route? = null

        override fun save(route: Route) {
            saved = route
        }

        override fun load() = saved

        override fun clear() {
            saved = null
        }
    }

    private class FakeLogger : Logger {
        val lines = mutableListOf<String>()

        override fun info(tag: String, message: String) {
            lines += "I/$tag: $message"
        }

        override fun warn(tag: String, message: String) {
            lines += "W/$tag: $message"
        }
    }

    private val detour = Route(
        destinationName = "Parque de prueba",
        distanceM = 50.0,
        durationS = 40.0,
        steps = listOf(
            at(40.0, 100.0).let { RouteStep(1, Maneuver.STRAIGHT, "Vuelva hacia el oeste.", 50.0, it.lat, it.lng) },
            at(200.0, 200.0).let { RouteStep(2, Maneuver.ARRIVE, "Ha llegado.", 0.0, it.lat, it.lng) },
        ),
        polyline = listOf(at(40.0, 100.0), at(200.0, 200.0)),
    )

    @Test
    fun startVibratesOnRouteAndPublishesNextStep() = runBlocking {
        val vibration = FakeVibration()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { 0L },
        )

        tracking.start(lRoute(), this)
        try {
            val state = tracking.state.value
            assertEquals(listOf(pattern(VibrationCue.ON_ROUTE)), vibration.played)
            assertEquals(NavigationStatus.ON_ROUTE, state.status)
            assertEquals("Gire a la derecha.", state.nextInstruction)
            assertEquals(pattern(VibrationCue.ON_ROUTE), state.pattern)
            assertEquals(400.0, state.remainingM, 0.5)
            assertEquals(200.0, state.stepLengthM!!, 0.5)
            assertEquals(0f, state.stepProgress, 0.01f)
        } finally {
            tracking.stop()
        }
        assertEquals(1, vibration.stops)
        assertEquals(TrackingState(), tracking.state.value)
    }

    @Test
    fun watchWithoutAmplitudeControlVibratesTheRhythmOnlyVersion() = runBlocking {
        val vibration = FakeVibration(supportsAmplitude = false)
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { 0L },
        )

        tracking.start(lRoute(), this)
        tracking.stop()

        assertEquals(listOf(pattern(VibrationCue.ON_ROUTE, supportsAmplitude = false)), vibration.played)
    }

    @Test
    fun offRouteVibratesAndRecalculatesFromCurrentPosition() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val vibration = FakeVibration()
        val routes = FakeRoutes(detour)
        val speech = FakeSpeech()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            routes,
            vibration,
            FakeVibrationEvents(),
            speech,
            clock = { now },
            tickMs = 60_000,
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            listOf(0L, 1_000L, 2_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }
            assertEquals(NavigationStatus.OFF_ROUTE, tracking.state.value.status)
            assertEquals(pattern(VibrationCue.OFF_ROUTE), vibration.played.last())
            assertEquals(OFF_ROUTE_PHRASE to SpeechPriority.HIGH, speech.spoken.last())

            now = 12_000
            fixes.emit(fixAt(40.0, 100.0, now))
            delay(50)

            assertEquals(0.0, haversineM(routes.origin!!, at(40.0, 100.0)), 0.1)
            assertEquals("Parque de prueba", routes.destination?.name)
            assertEquals("Ha llegado.", tracking.state.value.nextInstruction)
            assertTrue(!tracking.state.value.recalculating)
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun eachVibrationIsRecordedWithRouteAndNextStep() = runBlocking {
        val events = FakeVibrationEvents()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            FakeVibration(),
            events,
            FakeSpeech(),
            clock = { 0L },
        )

        tracking.start(lRoute().copy(id = 9), this)
        try {
            delay(20)
            assertEquals(listOf(VibrationEvent(pattern(VibrationCue.ON_ROUTE).label, 9, 2)), events.recorded)
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun failingToRecordVibrationDoesNotStopNavigation() = runBlocking {
        val vibration = FakeVibration()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(AppError("No pude conectar con el servidor")),
            FakeSpeech(),
            clock = { 0L },
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            assertEquals(listOf(pattern(VibrationCue.ON_ROUTE)), vibration.played)
            assertEquals(NavigationStatus.ON_ROUTE, tracking.state.value.status)
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun startSaysTheFirstInstructionAndStopSilences() = runBlocking {
        val speech = FakeSpeech()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            speech,
            clock = { 0L },
        )

        tracking.start(lRoute(), this)
        tracking.stop()

        assertEquals(listOf("Iniciando ruta. Camine hacia el norte." to SpeechPriority.NORMAL), speech.spoken)
        assertEquals(1, speech.stops)
    }

    @Test
    fun repeatVibratesAgainAndSaysTheCurrentInstruction() = runBlocking {
        val vibration = FakeVibration()
        val speech = FakeSpeech()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            speech,
            clock = { 0L },
        )

        tracking.repeat()
        assertTrue(vibration.played.isEmpty())

        tracking.start(lRoute(), this)
        try {
            tracking.repeat()

            assertEquals(listOf(pattern(VibrationCue.ON_ROUTE), pattern(VibrationCue.ON_ROUTE)), vibration.played)
            assertEquals("En doscientos metros, gire a la derecha." to SpeechPriority.HIGH, speech.spoken.last())
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun failedRecalculationsAreSpacedTenSecondsCappedAtFiveAndKeepTheOldRoute() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val vibration = FakeVibration()
        val logger = FakeLogger()
        var now = 0L
        val routes = FailingRoutes { now }
        val tracking = TrackNavigation(
            fixes,
            routes,
            vibration,
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { now },
            tickMs = 600_000,
            logger = logger,
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            // Parado a 40 m de la ruta durante 80 s, una lectura cada 2 s.
            (0L..80_000L step 2_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }

            // Fuera de ruta desde la tercera lectura lejana (4 s): intentos a los 14, 24, 34, 44 y 54 s.
            assertEquals(listOf(14_000L, 24_000L, 34_000L, 44_000L, 54_000L), routes.calledAtMs)
            // Un aviso al salirse y uno por cada intento fallido.
            assertEquals(6, vibration.played.count { it == pattern(VibrationCue.OFF_ROUTE) })
            val state = tracking.state.value
            assertEquals(NavigationStatus.OFF_ROUTE, state.status)
            assertEquals("Gire a la derecha.", state.nextInstruction)
            assertTrue(!state.recalculating)
            assertTrue(logger.lines.any { it.startsWith("W/Nav: No se pudo recalcular (intento 5 de 5)") })
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun backOnRouteGivesTheNextDeviationAllItsAttemptsAgain() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        var now = 0L
        val routes = FailingRoutes { now }
        val tracking = TrackNavigation(
            fixes,
            routes,
            FakeVibration(),
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { now },
            tickMs = 600_000,
        )

        suspend fun at(t: Long, east: Double, north: Double) {
            now = t
            fixes.emit(fixAt(east, north, t))
            delay(20)
        }

        tracking.start(lRoute(), this)
        try {
            delay(20)
            (0L..60_000L step 2_000L).forEach { at(it, 40.0, 100.0) }
            assertEquals(5, routes.calledAtMs.size)

            (62_000L..66_000L step 2_000L).forEach { at(it, 0.0, 100.0) }
            assertEquals(NavigationStatus.ON_ROUTE, tracking.state.value.status)

            (68_000L..86_000L step 2_000L).forEach { at(it, 40.0, 110.0) }
            assertEquals(6, routes.calledAtMs.size)
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun routeIsSavedWhileNavigatingAndForgottenOnStop() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val store = FakeStore()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { now },
            tickMs = 600_000,
            activeRoutes = store,
        )

        tracking.start(lRoute(), this)
        try {
            assertEquals(lRoute(), store.saved)

            delay(20)
            listOf(0L, 1_000L, 2_000L, 12_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }
            assertEquals("la ruta recalculada reemplaza a la guardada", detour, store.saved)
        } finally {
            tracking.stop()
        }
        assertNull(store.saved)
    }

    @Test
    fun arrivingForgetsTheSavedRoute() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val store = FakeStore()
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { 1_000L },
            tickMs = 600_000,
            activeRoutes = store,
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            fixes.emit(fixAt(200.0, 200.0, 1_000L))
            delay(20)

            assertEquals(NavigationStatus.ARRIVED, tracking.state.value.status)
            assertNull(store.saved)
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun eachStatusChangeIsLoggedWithTheNavTagAndStopIsIdempotent() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val vibration = FakeVibration()
        val logger = FakeLogger()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { now },
            tickMs = 600_000,
            logger = logger,
        )

        tracking.start(lRoute(), this)
        delay(20)
        listOf(0L, 1_000L, 2_000L).forEach { t ->
            now = t
            fixes.emit(fixAt(40.0, 100.0, t))
            delay(20)
        }
        tracking.stop()
        tracking.stop()

        assertTrue(logger.lines.any { it.startsWith("I/Nav: Navegación iniciada hacia «Parque de prueba»") })
        assertTrue(logger.lines.any { it.startsWith("I/Nav: Estado - → ON_ROUTE") })
        assertTrue(logger.lines.any { it.startsWith("I/Nav: Estado ON_ROUTE → OFF_ROUTE") })
        assertEquals(1, logger.lines.count { it.startsWith("I/Nav: Navegación terminada") })
        assertEquals(1, vibration.stops)
    }

    @Test
    fun failingVoiceDoesNotStopVibrations() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val vibration = FakeVibration()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            FakeSpeech(IllegalStateException("sin voz")),
            clock = { now },
            tickMs = 60_000,
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            listOf(0L, 1_000L, 2_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }
            assertEquals(NavigationStatus.OFF_ROUTE, tracking.state.value.status)
            assertEquals(pattern(VibrationCue.OFF_ROUTE), vibration.played.last())
        } finally {
            tracking.stop()
        }
    }
}
