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
import com.rumbo.watchapp.domain.vibration.PulseDuration
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationIntensity
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.testing.FakeVibration
import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.fixAt
import com.rumbo.watchapp.testing.lRoute
import com.rumbo.watchapp.testing.pattern
import com.rumbo.watchapp.testing.uRoute
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
        val prepared = mutableListOf<String>()
        var stops = 0
        override val status = MutableStateFlow(SpeechStatus())
        override val speaking = MutableStateFlow(false)

        override fun speak(text: String, priority: SpeechPriority) {
            error?.let { throw it }
            spoken += text to priority
        }

        override fun prepare(text: String) {
            error?.let { throw it }
            prepared += text
        }

        override fun stop() {
            stops++
        }

        override fun pause() = Unit
        override fun resume() = Unit
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
    fun vibrationSettingsApplyToNavigation() = runBlocking {
        val vibration = FakeVibration()
        val settings = VibrationSettings(VibrationIntensity.STRONG, PulseDuration.LONG)
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { 0L },
            vibrationSettings = { settings },
        )

        tracking.start(lRoute(), this)
        tracking.stop()

        // Recto: 300 ms a fuerza 60 pasa a 390 ms a fuerza 75.
        assertEquals(listOf(VibrationPatterns.of(VibrationCue.ON_ROUTE, true, settings)), vibration.played)
        assertEquals(listOf(390L), vibration.played.single().pulseDurationsMs)
        assertEquals(listOf(75), vibration.played.single().pulseAmplitudes)
    }

    @Test
    fun turnReachedWithoutWarningVibratesBothStagesAndRepeatsThem() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val vibration = FakeVibration()
        val events = FakeVibrationEvents()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            vibration,
            events,
            FakeSpeech(),
            clock = { now },
            tickMs = 60_000,
        )
        val twoStages = VibrationPatterns.forCues(
            listOf(VibrationCue.TURN_APPROACHING, VibrationCue.RIGHT),
            supportsAmplitude = true,
            settings = VibrationSettings(),
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            // La primera lectura ya está a 8 m del giro a la derecha.
            now = 1_000
            fixes.emit(fixAt(0.0, 192.0, now))
            delay(50)

            assertEquals(NavigationStatus.MANEUVER, tracking.state.value.status)
            assertEquals(listOf(pattern(VibrationCue.ON_ROUTE), twoStages), vibration.played)
            assertEquals(listOf(100L, 100L, 700L), twoStages.pulseDurationsMs)
            assertEquals(twoStages.label, events.recorded.last().pattern)

            tracking.repeat()
            assertEquals(twoStages, vibration.played.last())
        } finally {
            tracking.stop()
        }
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

        assertEquals(
            listOf("Iniciando ruta. Camine hacia el norte. En doscientos metros, gira a la derecha." to SpeechPriority.NORMAL),
            speech.spoken,
        )
        assertEquals(1, speech.stops)
    }

    @Test
    fun walkingARouteSaysEachStageOnceWithTheStreets() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
        val speech = FakeSpeech()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            speech,
            clock = { now },
            tickMs = 600_000,
        )
        val track = (0..200 step 5).map { 0.0 to it.toDouble() } + (5..395 step 5).map { it.toDouble() to 200.0 }

        tracking.start(uRoute(), this)
        try {
            delay(20)
            track.forEach { (east, north) ->
                now += 1_000
                fixes.emit(fixAt(east, north, now))
                delay(2)
            }
            delay(20)

            assertEquals(
                listOf(
                    "Iniciando ruta. Camine hacia el norte. En doscientos metros, gira a la derecha.",
                    "En cincuenta metros, gira a la derecha en Carrera 2.",
                    "Gira ahora a la derecha.",
                    "Sigue recto por Carrera 2. En cuatrocientos metros, gira a la izquierda.",
                    "En cincuenta metros, gira a la izquierda en Calle 3.",
                    "Gira ahora a la izquierda.",
                ),
                speech.spoken.map { it.first },
            )
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun aSavedDestinationIsSaidAtTheStartAfterTheGuidance() = runBlocking {
        val speech = FakeSpeech()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            speech,
            clock = { 0L },
        )

        tracking.start(lRoute().copy(destinationSaved = true), this)
        val state = tracking.state.value
        tracking.stop()

        assertEquals(
            "Iniciando ruta. Camine hacia el norte. En doscientos metros, gira a la derecha. Este destino está guardado.",
            speech.spoken.single().first,
        )
        assertTrue(state.destinationSaved)
    }

    @Test
    fun savingDuringTheRouteShowsItAndKeepsItIfTheProcessDies() = runBlocking {
        val store = FakeStore()
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { 0L },
            activeRoutes = store,
        )

        tracking.start(lRoute(), this)
        assertTrue(!tracking.state.value.destinationSaved)
        tracking.markDestinationSaved()

        assertTrue(tracking.state.value.destinationSaved)
        assertEquals(lRoute().copy(destinationSaved = true), store.saved)
        tracking.stop()
    }

    @Test
    fun aRecalculatedRouteStaysSaved() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            FakeSpeech(),
            clock = { now },
            tickMs = 600_000,
        )

        tracking.start(lRoute().copy(destinationSaved = true), this)
        try {
            delay(20)
            listOf(0L, 1_000L, 2_000L, 12_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }

            assertEquals("Ha llegado.", tracking.state.value.nextInstruction)
            assertTrue(tracking.state.value.destinationSaved)
        } finally {
            tracking.stop()
        }
    }

    /** Recorre la ruta en L, con [text] en el paso del medio, y deja la voz y la vibración en los fakes. */
    private fun walkPastAnUnknownStep(text: String, check: (FakeSpeech, FakeVibration, TrackingState) -> Unit) = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
        val speech = FakeSpeech()
        val vibration = FakeVibration()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            vibration,
            FakeVibrationEvents(),
            speech,
            clock = { now },
            tickMs = 600_000,
        )
        val route = lRoute().let { full ->
            full.copy(steps = full.steps.map { if (it.order == 2) it.copy(maneuver = Maneuver.UNKNOWN, instruction = text) else it })
        }

        tracking.start(route, this)
        try {
            delay(20)
            (0..195 step 5).forEach {
                now += 1_000
                fixes.emit(fixAt(0.0, it.toDouble(), now))
                delay(2)
            }
            delay(20)
            check(speech, vibration, tracking.state.value)
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun anUnknownStepIsAnnouncedWithItsOriginalText() = walkPastAnUnknownStep("Tome el ascensor.") { speech, vibration, state ->
        assertEquals(
            listOf(
                "Iniciando ruta. Camine hacia el norte. En doscientos metros, tome el ascensor.",
                "En cincuenta metros, tome el ascensor.",
                "Ahora, tome el ascensor.",
            ),
            speech.spoken.map { it.first },
        )
        assertEquals(setOf(SpeechPriority.HIGH), speech.spoken.drop(1).map { it.second }.toSet())
        assertEquals(2, vibration.played.count { it == pattern(VibrationCue.ATTENTION) })
        assertEquals("Tome el ascensor.", state.nextInstruction)
        assertEquals(Maneuver.UNKNOWN, state.nextManeuver)
    }

    @Test
    fun stairsAreAnnouncedWithAttention() = walkPastAnUnknownStep("Tome las escaleras.") { speech, vibration, state ->
        assertEquals(
            listOf(
                "Iniciando ruta. Camine hacia el norte. Atención: en doscientos metros, tome las escaleras.",
                "Atención: en cincuenta metros, tome las escaleras.",
                "Atención: ahora, tome las escaleras.",
            ),
            speech.spoken.map { it.first },
        )
        assertEquals(2, vibration.played.count { it == pattern(VibrationCue.ATTENTION) })
        assertTrue(pattern(VibrationCue.TURN_APPROACHING) !in vibration.played)
        assertTrue(pattern(VibrationCue.LEFT) !in vibration.played)
        assertTrue(pattern(VibrationCue.RIGHT) !in vibration.played)
        assertEquals("Atención: tome las escaleras.", state.nextInstruction)
    }

    @Test
    fun everyStageIsPreparedOnceAndBeforeItIsSaid() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
        val speech = FakeSpeech()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            speech,
            clock = { now },
            tickMs = 600_000,
        )
        val track = (0..200 step 5).map { 0.0 to it.toDouble() } +
            (5..400 step 5).map { it.toDouble() to 200.0 } +
            (205..400 step 5).map { 400.0 to it.toDouble() }

        tracking.start(uRoute(), this)
        try {
            delay(20)
            track.forEach { (east, north) ->
                now += 1_000
                val preparedBefore = speech.prepared.toList()
                val spokenBefore = speech.spoken.size
                fixes.emit(fixAt(east, north, now))
                delay(2)
                // Lo que suena ahora tenía que estar listo desde antes de esta lectura.
                speech.spoken.drop(spokenBefore).forEach { (text, _) ->
                    assertTrue("«$text» sonó sin precargar", text in preparedBefore)
                }
            }
            delay(20)

            assertEquals(
                listOf(
                    "En cincuenta metros, gira a la derecha en Carrera 2.",
                    "Gira ahora a la derecha.",
                    "Sigue recto por Carrera 2. En cuatrocientos metros, gira a la izquierda.",
                    "En cincuenta metros, gira a la izquierda en Calle 3.",
                    "Gira ahora a la izquierda.",
                    "Tu destino está en cien metros.",
                    "Llegaste a tu destino.",
                ),
                speech.prepared,
            )
            assertEquals("Llegaste a tu destino." to SpeechPriority.HIGH, speech.spoken.last())
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun failingToPrepareDoesNotStopTheNavigation() = runBlocking {
        val failing = object : SpeechOutput by FakeSpeech() {
            val spoken = mutableListOf<String>()

            override fun speak(text: String, priority: SpeechPriority) {
                spoken += text
            }

            override fun prepare(text: String) {
                throw IllegalStateException("sin red")
            }
        }
        val tracking = TrackNavigation(
            MutableSharedFlow(),
            FakeRoutes(detour),
            FakeVibration(),
            FakeVibrationEvents(),
            failing,
            clock = { 0L },
        )

        tracking.start(lRoute(), this)
        tracking.stop()

        assertEquals(1, failing.spoken.size)
    }

    @Test
    fun aRecalculatedRouteIsPreparedToo() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val speech = FakeSpeech()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(uRoute()),
            FakeVibration(),
            FakeVibrationEvents(),
            speech,
            clock = { now },
            tickMs = 600_000,
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            listOf(0L, 1_000L, 2_000L, 12_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }

            assertEquals("En cincuenta metros, gira a la derecha en Carrera 2.", speech.prepared.last())
        } finally {
            tracking.stop()
        }
    }

    @Test
    fun aRecalculatedRouteSaysItsFirstLegOnTheNextReading() = runBlocking {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
        val speech = FakeSpeech()
        var now = 0L
        val tracking = TrackNavigation(
            fixes,
            FakeRoutes(uRoute()),
            FakeVibration(),
            FakeVibrationEvents(),
            speech,
            clock = { now },
            tickMs = 600_000,
        )

        tracking.start(lRoute(), this)
        try {
            delay(20)
            listOf(0L, 1_000L, 2_000L, 12_000L).forEach { t ->
                now = t
                fixes.emit(fixAt(40.0, 100.0, t))
                delay(20)
            }
            now = 13_000
            fixes.emit(fixAt(0.0, 5.0, now))
            delay(20)

            assertEquals(
                "Sigue recto por Carrera 1. En doscientos metros, gira a la derecha." to SpeechPriority.NORMAL,
                speech.spoken.last(),
            )
        } finally {
            tracking.stop()
        }
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
            assertEquals("Sigue recto. En doscientos metros, gira a la derecha." to SpeechPriority.HIGH, speech.spoken.last())
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
