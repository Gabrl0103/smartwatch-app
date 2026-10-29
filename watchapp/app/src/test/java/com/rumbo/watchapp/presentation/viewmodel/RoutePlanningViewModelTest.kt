package com.rumbo.watchapp.presentation.viewmodel

import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.error.SERVER_WAKING_MESSAGE
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.domain.port.LocationProvider
import com.rumbo.watchapp.domain.port.NavigationRunner
import com.rumbo.watchapp.domain.port.PlacesRepository
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SavedPlacesRepository
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechPriority
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.port.VoiceRepository
import com.rumbo.watchapp.domain.port.VoiceSettings
import com.rumbo.watchapp.domain.vibration.PulseDuration
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationIntensity
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.testing.BASE
import com.rumbo.watchapp.testing.FakeVibration
import com.rumbo.watchapp.testing.FakeVibrationSettingsStore
import com.rumbo.watchapp.testing.lRoute
import com.rumbo.watchapp.testing.pattern
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Gesto de volver, "Repetir", "Vibraciones", permisos y esperas: qué pantalla queda y qué se dice. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutePlanningViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val place = SavedPlace(1, "Parque de prueba", 1.2155, -77.2793)
    private val route = lRoute()

    private class FakeRecorder : AudioRecorder {
        var started = false
        var released = false
        override val levels: Flow<Float> = emptyFlow()

        override fun start() {
            started = true
        }

        override fun stop() = File("audio.aac")

        override fun release() {
            released = true
        }
    }

    private class FakeVoice : VoiceRepository {
        var calls = 0

        override suspend fun interpret(audioFile: File): VoiceResult {
            calls++
            throw IllegalStateException("No debería interpretarse")
        }
    }

    private object NoPlaces : PlacesRepository {
        override suspend fun search(query: String, near: Coordinates): Place? = null
    }

    private class FakeRoutes(private val route: Route) : RoutesRepository {
        override suspend fun create(origin: Coordinates, destination: Place) = route
    }

    private class FakeSavedPlaces(
        private val places: List<SavedPlace>,
        private val loaded: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : SavedPlacesRepository {
        override suspend fun list() = places.also { loaded.await() }

        override suspend fun add(name: String, lat: Double, lng: Double) = places.first()

        override suspend fun delete(id: Long) = Unit
    }

    private class FakeRunner : NavigationRunner {
        override var activeRoute: Route? = null
        val running get() = activeRoute != null

        override fun start(route: Route) {
            activeRoute = route
        }

        override fun stop() {
            activeRoute = null
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


    private class FakeSpeech : SpeechOutput {
        val spoken = mutableListOf<String>()
        override val status = MutableStateFlow(SpeechStatus())

        override fun speak(text: String, priority: SpeechPriority) {
            spoken += text
        }

        override fun stop() = Unit
    }

    /** GPS que da una lectura buena en cuanto se enciende. */
    private object GoodGps : LocationProvider {
        override fun locations() = flowOf(LocationFix(BASE.lat, BASE.lng, accuracyM = 5.0, timeMs = 0))
    }

    /** GPS que no da nada hasta que el test emite. */
    private class SlowGps : LocationProvider {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 4)

        override fun locations() = fixes
    }

    private object VoiceOn : VoiceSettings {
        override fun isEnabled() = true

        override fun setEnabled(enabled: Boolean) = Unit
    }

    private val recorder = FakeRecorder()
    private val voice = FakeVoice()
    private val runner = FakeRunner()
    private val vibration = FakeVibration()
    private val vibrationSettings = FakeVibrationSettingsStore()
    private val speech = FakeSpeech()
    private val navigation = MutableStateFlow(TrackingState())
    private val serverWaking = MutableStateFlow(false)
    private val store = FakeStore()
    private var repeats = 0
    private lateinit var viewModel: RoutePlanningViewModel

    private fun build(
        gps: LocationProvider = GoodGps,
        savedPlaces: SavedPlacesRepository = FakeSavedPlaces(listOf(place)),
        vibration: FakeVibration = this.vibration,
    ): RoutePlanningViewModel {
        val routes = FakeRoutes(route)
        val tracker = LocationTracker(gps, { 0L }, CoroutineScope(dispatcher))
        return RoutePlanningViewModel(
            recorder,
            PlanRouteFromVoice(voice, NoPlaces, routes, tracker::awaitOrigin),
            ManageSavedPlaces(savedPlaces, routes, tracker::awaitOrigin),
            tracker,
            runner,
            navigation,
            { repeats++ },
            vibration,
            vibrationSettings,
            speech,
            VoiceOn,
            serverWaking,
            store,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun confirmSavedPlace() {
        viewModel.openSavedPlaces()
        viewModel.chooseSavedPlace(place)
        assertTrue(viewModel.state.value is PlanState.Confirming)
    }

    private fun startRoute() {
        confirmSavedPlace()
        viewModel.go()
        assertTrue(runner.running)
    }

    @Test
    fun backOnStartDoesNothingSoTheSystemClosesTheApp() {
        viewModel.back()

        assertEquals(PlanState.Start, viewModel.state.value)
    }

    @Test
    fun backFromConfirmGoesToStart() {
        confirmSavedPlace()

        viewModel.back()

        assertEquals(PlanState.Start, viewModel.state.value)
    }

    @Test
    fun backOnRouteAsksBeforeFinishing() {
        startRoute()

        viewModel.back()
        assertEquals(PlanState.OnRoute(route, confirmFinish = true), viewModel.state.value)
        assertTrue(runner.running)

        viewModel.back()
        assertEquals(PlanState.OnRoute(route, confirmFinish = false), viewModel.state.value)

        viewModel.back()
        viewModel.finish()
        assertEquals(PlanState.Start, viewModel.state.value)
        assertFalse(runner.running)
    }

    @Test
    fun keepNavigatingClosesTheQuestion() {
        startRoute()
        viewModel.back()

        viewModel.keepNavigating()

        assertEquals(PlanState.OnRoute(route), viewModel.state.value)
        assertTrue(runner.running)
    }

    @Test
    fun backAfterArrivingFinishesWithoutAsking() {
        startRoute()
        navigation.value = TrackingState(status = NavigationStatus.ARRIVED)

        viewModel.back()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertFalse(runner.running)
    }

    @Test
    fun backWhileListeningCancelsTheRecording() {
        viewModel.listen()
        assertEquals(PlanState.Listening, viewModel.state.value)

        viewModel.back()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertTrue(recorder.released)
        assertEquals(0, voice.calls)
    }

    @Test
    fun backFromDeleteQuestionReturnsToTheList() {
        viewModel.openSavedPlaces()
        viewModel.askDelete(place)

        viewModel.back()

        assertEquals(PlanState.SavedPlaces(listOf(place)), viewModel.state.value)
    }

    @Test
    fun repeatOnlyWorksOnRoute() {
        viewModel.repeatInstruction()
        assertEquals(0, repeats)

        startRoute()
        viewModel.repeatInstruction()

        assertEquals(1, repeats)
    }

    @Test
    fun vibrationsScreenPlaysPatternsAndStopsOnBack() {
        viewModel.playVibration(VibrationCue.LEFT)
        assertTrue(vibration.played.isEmpty())

        viewModel.openVibrations()
        viewModel.playVibration(VibrationCue.LEFT)
        viewModel.back()

        assertEquals(listOf(pattern(VibrationCue.LEFT)), vibration.played)
        assertEquals(1, vibration.stops)
        assertEquals(PlanState.Start, viewModel.state.value)
    }

    @Test
    fun vibrationsScreenUsesTheVersionTheWatchUnderstands() {
        assertTrue(viewModel.supportsAmplitude)
        assertEquals(VibrationPatterns.WITH_AMPLITUDE, viewModel.vibrationPatterns.value)

        val onOff = FakeVibration(supportsAmplitude = false)
        viewModel = build(vibration = onOff)
        viewModel.openVibrations()
        viewModel.playVibration(VibrationCue.ON_ROUTE)

        assertFalse(viewModel.supportsAmplitude)
        assertEquals(VibrationPatterns.WITHOUT_AMPLITUDE, viewModel.vibrationPatterns.value)
        assertEquals(listOf(pattern(VibrationCue.ON_ROUTE, supportsAmplitude = false)), onOff.played)
    }

    @Test
    fun vibrationSettingsAreSavedAppliedAndFeltRightAway() {
        viewModel.changeVibrationIntensity()
        assertEquals(VibrationSettings(), viewModel.vibrationSettings.value)

        viewModel.openVibrations()
        viewModel.changeVibrationIntensity()
        viewModel.changeVibrationDuration()

        val settings = VibrationSettings(VibrationIntensity.STRONG, PulseDuration.LONG)
        assertEquals(settings, vibrationSettings.saved)
        assertEquals(settings, viewModel.vibrationSettings.value)
        // Cada cambio vibra el ejemplo con el ajuste nuevo.
        assertEquals(
            listOf(
                VibrationPatterns.of(SETTINGS_SAMPLE, true, VibrationSettings(VibrationIntensity.STRONG)),
                VibrationPatterns.of(SETTINGS_SAMPLE, true, settings),
            ),
            vibration.played,
        )
        // La lista y lo que se toca usan los mismos ajustes.
        assertEquals(VibrationPatterns.forDevice(true, settings), viewModel.vibrationPatterns.value)
        viewModel.playVibration(VibrationCue.RIGHT)
        assertEquals(listOf(910L), vibration.played.last().pulseDurationsMs)

        viewModel.changeVibrationIntensity()
        assertEquals(VibrationIntensity.SOFT, vibrationSettings.saved.intensity)
    }

    @Test
    fun savedVibrationSettingsAreLoadedOnStart() {
        vibrationSettings.saved = VibrationSettings(duration = PulseDuration.LONG)
        viewModel = build()

        assertEquals(PulseDuration.LONG, viewModel.vibrationSettings.value.duration)
        assertEquals(listOf(390L, 390L), viewModel.vibrationPatterns.value.getValue(VibrationCue.LEFT).pulseDurationsMs)
    }

    @Test
    fun demoPlaysEveryPatternInOrderWithTwoSecondPauses() {
        viewModel.openVibrations()
        viewModel.startVibrationDemo()

        val total = VibrationCue.entries.size
        assertEquals(PlanState.Vibrations(DemoStep(VibrationCue.ON_ROUTE, 1, total)), viewModel.state.value)
        assertEquals(listOf(pattern(VibrationCue.ON_ROUTE)), vibration.played)

        // El siguiente suena cuando termina el patrón y pasan 2 s, ni antes ni después.
        dispatcher.scheduler.advanceTimeBy(pattern(VibrationCue.ON_ROUTE).durationMs + DEMO_PAUSE_MS - 1)
        assertEquals(1, vibration.played.size)
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertEquals(PlanState.Vibrations(DemoStep(VibrationCue.TURN_APPROACHING, 2, total)), viewModel.state.value)

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(VibrationCue.entries.map { pattern(it) }, vibration.played)
        assertEquals(PlanState.Vibrations(), viewModel.state.value)
    }

    @Test
    fun stoppingTheDemoCutsTheVibrationAndReturnsToTheList() {
        viewModel.openVibrations()
        viewModel.startVibrationDemo()

        viewModel.stopVibrationDemo()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PlanState.Vibrations(), viewModel.state.value)
        assertEquals(1, vibration.played.size)
        assertEquals(1, vibration.stops)
    }

    @Test
    fun backDuringTheDemoStopsItAndAgainGoesHome() {
        viewModel.startVibrationDemo()
        assertEquals(PlanState.Start, viewModel.state.value)

        viewModel.openVibrations()
        viewModel.startVibrationDemo()
        viewModel.back()
        assertEquals(PlanState.Vibrations(), viewModel.state.value)

        viewModel.back()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(PlanState.Start, viewModel.state.value)
        assertEquals(1, vibration.played.size)
    }

    @Test
    fun deniedMicrophoneExplainsWhyAndBackReturnsToStart() {
        viewModel.permissionDenied(Permission.MICROPHONE)

        assertEquals(PlanState.PermissionNeeded(Permission.MICROPHONE), viewModel.state.value)
        assertEquals("Necesito el micrófono para escucharte", speech.spoken.last())

        viewModel.back()
        assertEquals(PlanState.Start, viewModel.state.value)
    }

    @Test
    fun deniedLocationSaysItNeedsTheLocationToGuide() {
        viewModel.openSavedPlaces()

        viewModel.permissionDenied(Permission.LOCATION)

        assertEquals(PlanState.PermissionNeeded(Permission.LOCATION), viewModel.state.value)
        assertEquals("Necesito tu ubicación para guiarte", speech.spoken.last())
    }

    @Test
    fun grantedInSettingsWithoutRouteGoesBackToStart() {
        viewModel.permissionDenied(Permission.MICROPHONE)

        viewModel.permissionGranted()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertFalse(runner.running)
    }

    @Test
    fun deniedNotificationsKeepsTheRouteAndCanGoWithoutThem() {
        confirmSavedPlace()

        viewModel.permissionDenied(Permission.NOTIFICATIONS)
        assertEquals(PlanState.PermissionNeeded(Permission.NOTIFICATIONS, route), viewModel.state.value)
        assertFalse(runner.running)

        viewModel.goWithoutNotification()
        assertEquals(PlanState.OnRoute(route), viewModel.state.value)
        assertTrue(runner.running)
    }

    @Test
    fun notificationsGrantedInSettingsStartTheWaitingRoute() {
        confirmSavedPlace()
        viewModel.permissionDenied(Permission.NOTIFICATIONS)

        viewModel.permissionGranted()

        assertEquals(PlanState.OnRoute(route), viewModel.state.value)
        assertTrue(runner.running)
    }

    @Test
    fun withoutGpsFixWaitsShowingAndSayingSearchingSignal() {
        val gps = SlowGps()
        viewModel = build(gps = gps)
        viewModel.openSavedPlaces()

        viewModel.chooseSavedPlace(place)

        assertEquals(PlanState.Processing, viewModel.state.value)
        assertEquals(GPS_SEARCHING_MESSAGE, viewModel.waitMessage.value)
        assertEquals(GPS_SEARCHING_MESSAGE, speech.spoken.last())

        gps.fixes.tryEmit(LocationFix(BASE.lat, BASE.lng, accuracyM = 5.0, timeMs = 0))

        assertTrue(viewModel.state.value is PlanState.Confirming)
        assertNull(viewModel.waitMessage.value)
    }

    @Test
    fun wakingServerShowsAndSaysWhyItTakesLong() {
        val loaded = CompletableDeferred<Unit>()
        viewModel = build(savedPlaces = FakeSavedPlaces(listOf(place), loaded))
        viewModel.openSavedPlaces()
        assertEquals(PlanState.LoadingPlaces, viewModel.state.value)

        serverWaking.value = true
        assertEquals(SERVER_WAKING_MESSAGE, viewModel.waitMessage.value)
        assertEquals(SERVER_WAKING_MESSAGE, speech.spoken.last())

        serverWaking.value = false
        loaded.complete(Unit)
        assertEquals(PlanState.SavedPlaces(listOf(place)), viewModel.state.value)
    }

    @Test
    fun reopeningWhileTheServiceNavigatesGoesStraightBackToTheRoute() {
        runner.start(route)

        viewModel = build()

        assertEquals(PlanState.OnRoute(route), viewModel.state.value)
        assertNull(viewModel.previousRoute.value)
    }

    @Test
    fun routeLeftAfterTheProcessDiedIsOfferedAndCanContinue() {
        store.saved = route

        viewModel = build()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertEquals(route, viewModel.previousRoute.value)
        assertTrue(speech.spoken.last().startsWith("Tienes una ruta sin terminar a Parque de prueba"))

        viewModel.resumePreviousRoute()

        assertEquals(PlanState.OnRoute(route), viewModel.state.value)
        assertTrue(runner.running)
        assertNull(viewModel.previousRoute.value)
    }

    @Test
    fun previousRouteIsStillOfferedAfterGoingBackToStart() {
        store.saved = route
        viewModel = build()

        viewModel.openVibrations()
        viewModel.back()

        assertEquals(route, viewModel.previousRoute.value)
    }

    @Test
    fun deniedNotificationsWhenContinuingKeepThePreviousRoute() {
        store.saved = route
        viewModel = build()

        viewModel.permissionDenied(Permission.NOTIFICATIONS)
        assertEquals(PlanState.PermissionNeeded(Permission.NOTIFICATIONS, route), viewModel.state.value)

        viewModel.goWithoutNotification()
        assertEquals(PlanState.OnRoute(route), viewModel.state.value)
    }

    @Test
    fun finishStopsEverythingAndForgetsTheRoute() {
        startRoute()
        store.saved = route

        viewModel.finish()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertFalse(runner.running)
        assertNull(store.saved)
        assertNull(viewModel.previousRoute.value)
    }

    @Test
    fun wakingServerIsNotSpokenOutsideAWait() {
        serverWaking.value = true

        assertEquals(PlanState.Start, viewModel.state.value)
        assertTrue(speech.spoken.isEmpty())
    }
}
