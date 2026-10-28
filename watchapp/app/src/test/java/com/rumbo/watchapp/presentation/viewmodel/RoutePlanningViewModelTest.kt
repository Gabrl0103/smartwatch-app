package com.rumbo.watchapp.presentation.viewmodel

import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.domain.port.LocationProvider
import com.rumbo.watchapp.domain.port.NavigationRunner
import com.rumbo.watchapp.domain.port.PlacesRepository
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SavedPlacesRepository
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechPriority
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.port.VoiceRepository
import com.rumbo.watchapp.domain.port.VoiceSettings
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.testing.lRoute
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Gesto de volver, "Repetir" y "Vibraciones": qué pantalla queda y qué se detiene. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutePlanningViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val place = SavedPlace(1, "Parque de prueba", 1.2155, -77.2793)
    private val route = lRoute()

    private class FakeRecorder : AudioRecorder {
        var started = false
        var released = false

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

    private class FakeSavedPlaces(private val places: List<SavedPlace>) : SavedPlacesRepository {
        override suspend fun list() = places

        override suspend fun add(name: String, lat: Double, lng: Double) = places.first()

        override suspend fun delete(id: Long) = Unit
    }

    private class FakeRunner : NavigationRunner {
        var running = false

        override fun start(route: Route) {
            running = true
        }

        override fun stop() {
            running = false
        }
    }

    private class FakeVibration : VibrationController {
        val played = mutableListOf<VibrationPattern>()
        var stops = 0

        override fun play(pattern: VibrationPattern) {
            played += pattern
        }

        override fun stop() {
            stops++
        }
    }

    private class SilentSpeech : SpeechOutput {
        override val status = MutableStateFlow(SpeechStatus())

        override fun speak(text: String, priority: SpeechPriority) = Unit

        override fun stop() = Unit
    }

    private object NoGps : LocationProvider {
        override fun locations() = emptyFlow<LocationFix>()
    }

    private object VoiceOn : VoiceSettings {
        override fun isEnabled() = true

        override fun setEnabled(enabled: Boolean) = Unit
    }

    private val recorder = FakeRecorder()
    private val voice = FakeVoice()
    private val runner = FakeRunner()
    private val vibration = FakeVibration()
    private val navigation = MutableStateFlow(TrackingState())
    private var repeats = 0
    private lateinit var viewModel: RoutePlanningViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val routes = FakeRoutes(route)
        viewModel = RoutePlanningViewModel(
            recorder,
            PlanRouteFromVoice(voice, NoPlaces, routes),
            ManageSavedPlaces(FakeSavedPlaces(listOf(place)), routes),
            LocationTracker(NoGps, { 0L }, CoroutineScope(dispatcher)),
            runner,
            navigation,
            { repeats++ },
            vibration,
            SilentSpeech(),
            VoiceOn,
        )
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
        viewModel.playVibration(VibrationPatterns.LEFT)
        assertTrue(vibration.played.isEmpty())

        viewModel.openVibrations()
        viewModel.playVibration(VibrationPatterns.LEFT)
        viewModel.back()

        assertEquals(listOf(VibrationPatterns.LEFT), vibration.played)
        assertEquals(1, vibration.stops)
        assertEquals(PlanState.Start, viewModel.state.value)
    }
}
