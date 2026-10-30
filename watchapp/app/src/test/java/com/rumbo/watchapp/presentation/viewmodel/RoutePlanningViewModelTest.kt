package com.rumbo.watchapp.presentation.viewmodel

import com.rumbo.watchapp.application.location.LocationTracker
import com.rumbo.watchapp.application.speech.DESTINATION_ALREADY_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_DROPPED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_JUST_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_NOT_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.DESTINATION_SAVED_PHRASE
import com.rumbo.watchapp.application.speech.FINISH_QUESTION_PHRASE
import com.rumbo.watchapp.application.speech.confirmPhrase
import com.rumbo.watchapp.application.usecase.AskAssistant
import com.rumbo.watchapp.application.usecase.ExecuteVoiceCommand
import com.rumbo.watchapp.application.usecase.ManageSavedPlaces
import com.rumbo.watchapp.application.usecase.NOTHING_TO_CANCEL_MESSAGE
import com.rumbo.watchapp.application.usecase.NOTHING_TO_SAVE_MESSAGE
import com.rumbo.watchapp.application.usecase.NOT_UNDERSTOOD_MESSAGE
import com.rumbo.watchapp.application.usecase.NO_DESTINATION_MESSAGE
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.application.usecase.SAVE_FAILED_MESSAGE
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.error.SERVER_WAKING_MESSAGE
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.ActiveRouteStore
import com.rumbo.watchapp.domain.port.AssistantRepository
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
import com.rumbo.watchapp.domain.voice.LEVEL_SAMPLE_MS
import com.rumbo.watchapp.domain.voice.MAX_RECORDING_MS
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
    private val savedRoute = route.copy(destinationSaved = true)
    private val done = pattern(VibrationCue.COMMAND_OK)
    private val failed = pattern(VibrationCue.COMMAND_ERROR)

    private class FakeRecorder : AudioRecorder {
        var started = false
        var stopped = false
        var released = false

        /** Lo que "dice" la persona: un nivel cada 80 ms; después se repite el último. */
        var script: List<Float> = listOf(0f)

        override val levels: Flow<Float> = flow {
            var i = 0
            while (true) {
                emit(script.getOrElse(i++) { script.last() })
                delay(LEVEL_SAMPLE_MS)
            }
        }

        override fun start() {
            started = true
        }

        override fun stop(): File {
            stopped = true
            return File("audio.aac")
        }

        override fun release() {
            released = true
        }
    }

    private class FakeVoice : VoiceRepository {
        var calls = 0

        /** Lo que "entendió" el backend; null: falla, para los tests que no deben llegar ahí. */
        var result: VoiceResult? = null
        var error: AppError? = null

        override suspend fun interpret(audioFile: File): VoiceResult {
            calls++
            error?.let { throw it }
            return result ?: throw IllegalStateException("No debería interpretarse")
        }
    }

    private class FakeAssistant : AssistantRepository {
        val asked = mutableListOf<Pair<String, String>>()

        override suspend fun ask(question: String, context: String): String {
            asked += question to context
            return "Faltan unos cuatrocientos metros."
        }
    }

    private object NoPlaces : PlacesRepository {
        override suspend fun search(query: String, near: Coordinates): Place? = null
    }

    private object AnyPlace : PlacesRepository {
        override suspend fun search(query: String, near: Coordinates) = Place(query, near.lat, near.lng)
    }

    private class FakeRoutes(private val route: Route) : RoutesRepository {
        override suspend fun create(origin: Coordinates, destination: Place) = route
    }

    private class FakeSavedPlaces(
        private val places: List<SavedPlace>,
        private val loaded: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : SavedPlacesRepository {
        val added = mutableListOf<String>()
        var addError: AppError? = null

        override suspend fun list() = places.also { loaded.await() }

        override suspend fun add(name: String, lat: Double, lng: Double): SavedPlace {
            addError?.let { throw it }
            added += name
            return SavedPlace(9, name, lat, lng)
        }

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

        override fun markDestinationSaved() {
            activeRoute = activeRoute?.copy(destinationSaved = true)
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
        override val speaking = MutableStateFlow(false)

        override fun speak(text: String, priority: SpeechPriority) {
            spoken += text
        }

        override fun stop() = Unit

        var paused = false

        override fun pause() {
            paused = true
        }

        override fun resume() {
            paused = false
        }
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
    private val assistant = FakeAssistant()
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
        places: PlacesRepository = NoPlaces,
    ): RoutePlanningViewModel {
        val routes = FakeRoutes(route)
        val tracker = LocationTracker(gps, { 0L }, CoroutineScope(dispatcher))
        val planRoute = PlanRouteFromVoice(voice, places, routes, tracker::awaitOrigin)
        val manageSavedPlaces = ManageSavedPlaces(savedPlaces, routes, tracker::awaitOrigin)
        return RoutePlanningViewModel(
            recorder,
            planRoute,
            ExecuteVoiceCommand(
                planRoute,
                manageSavedPlaces,
                AskAssistant(assistant) { navigation.value },
                runner,
                tracking = { navigation.value },
                repeatInstruction = { repeats++ },
            ),
            manageSavedPlaces,
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
        assertEquals(PlanState.OnRoute(savedRoute, confirmFinish = true), viewModel.state.value)
        assertTrue(runner.running)

        viewModel.back()
        assertEquals(PlanState.OnRoute(savedRoute, confirmFinish = false), viewModel.state.value)

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

        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
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

    private fun levels(durationMs: Long, level: Float) = List((durationMs / LEVEL_SAMPLE_MS).toInt()) { level }

    @Test
    fun listeningStopsByItselfWhenThePersonFinishesSpeaking() {
        // 400 ms de espera, 1,6 s hablando y silencio: la lectura 40 (a los 3 120 ms) cierra.
        recorder.script = levels(400, 0.05f) + levels(1_600, 0.8f) + listOf(0.05f)
        viewModel.listen()

        dispatcher.scheduler.advanceTimeBy(1_000)
        assertEquals(PlanState.Listening, viewModel.state.value)
        assertEquals(0.8f, viewModel.voiceLevel.value)
        // La voz de la app no habla mientras se graba.
        assertTrue(speech.paused)

        dispatcher.scheduler.advanceTimeBy(3_119 - 1_000)
        assertEquals(PlanState.Listening, viewModel.state.value)
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()

        assertTrue(recorder.stopped)
        assertEquals(1, voice.calls)
        assertEquals(0f, viewModel.voiceLevel.value)
        assertFalse(speech.paused)
    }

    @Test
    fun listeningWithoutVoiceStopsAtEightSeconds() {
        recorder.script = listOf(0.1f)
        viewModel.listen()

        dispatcher.scheduler.advanceTimeBy(7_919)
        assertEquals(PlanState.Listening, viewModel.state.value)
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()

        assertTrue(recorder.stopped)
        assertEquals(1, voice.calls)
    }

    @Test
    fun cancelStopsListeningWithoutSending() {
        recorder.script = listOf(0.8f)
        viewModel.listen()
        dispatcher.scheduler.advanceTimeBy(1_000)

        viewModel.cancelListening()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertTrue(recorder.released)
        assertFalse(recorder.stopped)
        assertEquals(0, voice.calls)
        assertEquals(0f, viewModel.voiceLevel.value)
        assertFalse(speech.paused)
    }

    /** Dice algo al micrófono y espera a que termine de grabar (8 s sin voz). */
    private fun say(result: VoiceResult) {
        voice.result = result
        viewModel.listen()
        dispatcher.scheduler.advanceTimeBy(MAX_RECORDING_MS)
        dispatcher.scheduler.runCurrent()
    }

    private fun question(text: String) = VoiceResult(text, "ask", null, text)

    @Test
    fun aQuestionFromStartShowsAndSpeaksTheAnswer() {
        say(question("¿Qué hora es?"))

        assertEquals(PlanState.Assistant("¿Qué hora es?", "Faltan unos cuatrocientos metros."), viewModel.state.value)
        assertEquals("Faltan unos cuatrocientos metros.", speech.spoken.last())
        assertEquals(listOf("¿Qué hora es?" to ""), assistant.asked)

        viewModel.back()
        assertEquals(PlanState.Start, viewModel.state.value)
    }

    @Test
    fun aQuestionDuringTheRouteGoesBackToTheRoute() {
        startRoute()
        navigation.value = TrackingState(destinationName = "Parque de prueba", remainingM = 300.0)

        say(question("¿Cuánto falta?"))

        assertTrue(viewModel.state.value is PlanState.Assistant)
        assertTrue(assistant.asked.single().second.contains("Parque de prueba"))
        assertTrue(runner.running)
        viewModel.back()
        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
    }

    @Test
    fun repeatAndCancelByVoiceDuringTheRoute() {
        startRoute()

        say(VoiceResult("repite", "repeat", null, null))
        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
        assertEquals(1, repeats)

        say(VoiceResult("termina", "cancel", null, null))
        assertEquals(PlanState.OnRoute(savedRoute, confirmFinish = true), viewModel.state.value)
    }

    private fun order(intent: String) = VoiceResult("orden", intent, null, null)

    private fun confirmByVoice(savedPlaces: FakeSavedPlaces = FakeSavedPlaces(emptyList())) {
        viewModel = build(savedPlaces = savedPlaces, places = AnyPlace)
        say(VoiceResult("al parque", "navigate", "parque", null))
        assertEquals(PlanState.Confirming(route), viewModel.state.value)
    }

    @Test
    fun aNewDestinationVibratesDoubleShortAndAsksToConfirm() {
        confirmByVoice()

        assertEquals(listOf(done), vibration.played)
        assertEquals(confirmPhrase(route), speech.spoken.last())
    }

    @Test
    fun aDestinationAlreadySavedArrivesMarkedAsSaved() {
        viewModel = build(savedPlaces = FakeSavedPlaces(listOf(place)), places = AnyPlace)

        say(VoiceResult("al parque", "navigate", "Parque de prueba", null))

        assertEquals(PlanState.Confirming(savedRoute, SaveStatus.SAVED), viewModel.state.value)
    }

    @Test
    fun theMicInConfirmSavesAndComesBackToTheSameRoute() {
        val saved = FakeSavedPlaces(emptyList())
        confirmByVoice(saved)

        say(order("guardar_destino"))

        assertEquals(PlanState.Confirming(route, SaveStatus.SAVED), viewModel.state.value)
        assertEquals(listOf(route.destinationName), saved.added)
        assertEquals(DESTINATION_JUST_SAVED_PHRASE, speech.spoken.last())
        assertEquals(done, vibration.played.last())

        viewModel.go()
        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
        assertEquals(savedRoute, runner.activeRoute)
    }

    @Test
    fun savingTwiceDoesNotDuplicateThePlace() {
        val saved = FakeSavedPlaces(emptyList())
        confirmByVoice(saved)

        say(order("guardar_destino"))
        say(order("guardar_destino"))

        assertEquals(1, saved.added.size)
        assertEquals(DESTINATION_ALREADY_SAVED_PHRASE, speech.spoken.last())
    }

    @Test
    fun theSaveButtonCountsForVoiceAndForTheRoute() {
        val saved = FakeSavedPlaces(emptyList())
        confirmByVoice(saved)
        viewModel.saveDestination()

        say(order("estado_guardado"))
        assertEquals(DESTINATION_SAVED_PHRASE, speech.spoken.last())

        viewModel.go()
        assertEquals(savedRoute, runner.activeRoute)
    }

    @Test
    fun aFailedSaveVibratesLongAndStaysInConfirm() {
        val saved = FakeSavedPlaces(emptyList()).apply { addError = AppError("Sin conexión") }
        confirmByVoice(saved)

        say(order("guardar_destino"))

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
        assertEquals(SAVE_FAILED_MESSAGE, speech.spoken.last())
        assertEquals(failed, vibration.played.last())
    }

    @Test
    fun cancellingInConfirmDropsTheDestinationAndSaysSo() {
        confirmByVoice()

        say(order("cancelar_ruta"))

        assertEquals(PlanState.Start, viewModel.state.value)
        assertEquals(DESTINATION_DROPPED_PHRASE, speech.spoken.last())
        assertEquals(done, vibration.played.last())
    }

    @Test
    fun repeatingInConfirmSaysTheDestinationAgain() {
        confirmByVoice()

        say(order("repetir_instruccion"))

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
        assertEquals(confirmPhrase(route), speech.spoken.last())
        assertEquals(0, repeats)
    }

    @Test
    fun cancelInListeningComesBackToConfirmWithoutSending() {
        confirmByVoice()
        val calls = voice.calls

        viewModel.listen()
        assertTrue(viewModel.confirming)
        viewModel.cancelListening()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
        assertEquals(calls, voice.calls)
    }

    @Test
    fun backWhileListeningInConfirmComesBackToConfirm() {
        confirmByVoice()

        viewModel.listen()
        viewModel.back()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
    }

    @Test
    fun changeInConfirmStillGoesHome() {
        confirmByVoice()

        viewModel.reset()

        assertEquals(PlanState.Start, viewModel.state.value)
        assertFalse(viewModel.confirming)
    }

    @Test
    fun aQuestionFromConfirmReturnsToConfirmEvenAfterAskingAgain() {
        confirmByVoice()

        say(question("¿Hay un baño?"))
        assertTrue(viewModel.state.value is PlanState.Assistant)
        assertEquals(done, vibration.played.last())
        say(question("¿Y una tienda?"))
        viewModel.back()

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
    }

    @Test
    fun anErrorFromConfirmVibratesLongAndReturnsToConfirm() {
        confirmByVoice()

        say(order("bailar"))
        assertEquals(PlanState.Error(NOT_UNDERSTOOD_MESSAGE), viewModel.state.value)
        assertEquals(failed, vibration.played.last())
        viewModel.reset()

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
    }

    @Test
    fun anotherDestinationInConfirmReplacesIt() {
        confirmByVoice()

        say(VoiceResult("mejor a la plaza", "navigate", "plaza", null))

        assertEquals(PlanState.Confirming(route), viewModel.state.value)
        assertFalse(viewModel.confirming)
    }

    @Test
    fun howFarDuringTheRouteIsAnsweredOnTheWatch() {
        startRoute()
        navigation.value = TrackingState(remainingM = 200.0)

        say(order("cuanto_falta"))

        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
        assertEquals("Faltan doscientos metros, tres minutos a pie.", speech.spoken.last())
        assertEquals(done, vibration.played.last())
        assertTrue(assistant.asked.isEmpty())
    }

    @Test
    fun savingDuringTheRouteMarksItAndAskingConfirmsIt() {
        viewModel = build(savedPlaces = FakeSavedPlaces(emptyList()), places = AnyPlace)
        say(VoiceResult("al parque", "navigate", "parque", null))
        viewModel.go()
        assertEquals(route, runner.activeRoute)

        say(order("estado_guardado"))
        assertEquals(DESTINATION_NOT_SAVED_PHRASE, speech.spoken.last())
        say(order("guardar_destino"))
        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
        say(order("estado_guardado"))

        assertEquals(DESTINATION_SAVED_PHRASE, speech.spoken.last())
    }

    @Test
    fun cancelOnRouteAsksOutLoudAndNeverEndsTheRouteByItself() {
        startRoute()

        say(order("cancelar_ruta"))

        assertEquals(PlanState.OnRoute(savedRoute, confirmFinish = true), viewModel.state.value)
        assertEquals(FINISH_QUESTION_PHRASE, speech.spoken.last())
        assertTrue(runner.running)
    }

    @Test
    fun repeatOnRouteDoesNotVibrateOnTopOfTheInstruction() {
        startRoute()

        say(order("repetir_instruccion"))

        assertEquals(1, repeats)
        assertTrue(vibration.played.isEmpty())
    }

    @Test
    fun ordersWithoutARouteAreRefusedOutLoud() {
        listOf(
            "guardar_destino" to NOTHING_TO_SAVE_MESSAGE,
            "cancelar_ruta" to NOTHING_TO_CANCEL_MESSAGE,
            "cuanto_falta" to NO_DESTINATION_MESSAGE,
            "estado_guardado" to NO_DESTINATION_MESSAGE,
        ).forEach { (intent, message) ->
            viewModel.reset()
            say(order(intent))

            assertEquals(PlanState.Error(message), viewModel.state.value)
            assertEquals(message, speech.spoken.last())
            assertEquals(failed, vibration.played.last())
        }
    }

    @Test
    fun aNetworkErrorVibratesLong() {
        voice.error = AppError("Sin conexión. Revisa el internet del reloj")

        say(order("guardar_destino"))

        assertEquals(PlanState.Error("Sin conexión. Revisa el internet del reloj"), viewModel.state.value)
        assertEquals(failed, vibration.played.last())
    }

    @Test
    fun theOldNamesFromAnOlderBackendStillWork() {
        startRoute()

        say(order("repeat"))
        say(order("cancel"))

        assertEquals(1, repeats)
        assertEquals(PlanState.OnRoute(savedRoute, confirmFinish = true), viewModel.state.value)
    }

    @Test
    fun anotherDestinationDuringTheRouteKeepsTheRoute() {
        startRoute()

        say(VoiceResult("llévame a la plaza", "navigate", "plaza", null))

        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
        assertTrue(speech.spoken.last().startsWith("Ya vas hacia"))
    }

    @Test
    fun notUnderstoodShowsTheError() {
        say(VoiceResult("", "ask", null, null))
        assertEquals(PlanState.Error(NOT_UNDERSTOOD_MESSAGE), viewModel.state.value)
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
        assertEquals(PlanState.PermissionNeeded(Permission.NOTIFICATIONS, savedRoute), viewModel.state.value)
        assertFalse(runner.running)

        viewModel.goWithoutNotification()
        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
        assertTrue(runner.running)
    }

    @Test
    fun notificationsGrantedInSettingsStartTheWaitingRoute() {
        confirmSavedPlace()
        viewModel.permissionDenied(Permission.NOTIFICATIONS)

        viewModel.permissionGranted()

        assertEquals(PlanState.OnRoute(savedRoute), viewModel.state.value)
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
