package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.application.speech.ARRIVED_PHRASE
import com.rumbo.watchapp.application.speech.SIGNAL_LOST_PHRASE
import com.rumbo.watchapp.application.speech.SIGNAL_RECOVERED_PHRASE
import com.rumbo.watchapp.domain.error.NoConnectionError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.VibrationEvent
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SpeechOutput
import com.rumbo.watchapp.domain.port.SpeechPriority
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.port.VibrationController
import com.rumbo.watchapp.domain.port.VibrationEventsRepository
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.testing.GpxPoint
import com.rumbo.watchapp.testing.readGpx
import com.rumbo.watchapp.testing.routeFromGpx
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los GPX de docs/demo de punta a punta por [TrackNavigation] (filtro, motor, vibraciones, voz y
 * recálculo), en tiempo virtual: los 23 minutos del recorrido corren en milisegundos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoGpxTrackingTest {

    private val route = routeFromGpx(readGpx("ruta_parque_narino_a_parque_bolivar.gpx"))

    private class FakeVibration : VibrationController {
        val played = mutableListOf<VibrationPattern>()

        override fun play(pattern: VibrationPattern) {
            played += pattern
        }

        override fun stop() = Unit
    }

    private object NoEvents : VibrationEventsRepository {
        override suspend fun record(event: VibrationEvent) = Unit
    }

    private class FakeSpeech : SpeechOutput {
        val spoken = mutableListOf<String>()
        override val status = MutableStateFlow(SpeechStatus())

        override fun speak(text: String, priority: SpeechPriority) {
            spoken += text
        }

        override fun stop() = Unit
    }

    /** Sin internet: cada recálculo falla. */
    private class OfflineRoutes : RoutesRepository {
        var calls = 0

        override suspend fun create(origin: Coordinates, destination: Place): Route {
            calls++
            throw NoConnectionError()
        }
    }

    private fun TestScope.walk(gpx: String, routes: RoutesRepository, vibration: FakeVibration, speech: FakeSpeech): TrackNavigation {
        val track = readGpx(gpx)
        val startMs = track.first().timeMs
        val clock = { startMs + testScheduler.currentTime }
        val tracking = TrackNavigation(fixesOf(track, clock), routes, vibration, NoEvents, speech, clock)
        tracking.start(route, backgroundScope)
        advanceTimeBy(track.last().timeMs - startMs + 10_000)
        return tracking
    }

    // Cada punto llega a su hora, como al reproducir el GPX en el emulador.
    private fun fixesOf(track: List<GpxPoint>, clock: () -> Long) = flow {
        track.forEach { point ->
            delay(point.timeMs - clock())
            emit(LocationFix(point.lat, point.lng, accuracyM = 5.0, timeMs = clock()))
        }
    }

    @Test
    fun normalWalkVibratesEachTurnOnItsSideAndArrives() = runTest {
        val vibration = FakeVibration()
        val speech = FakeSpeech()

        val tracking = walk("ruta_parque_narino_a_parque_bolivar.gpx", OfflineRoutes(), vibration, speech)

        assertEquals(NavigationStatus.ARRIVED, tracking.state.value.status)
        assertEquals(6, vibration.played.count { it == VibrationPatterns.LEFT })
        assertEquals(6, vibration.played.count { it == VibrationPatterns.RIGHT })
        assertEquals(12, vibration.played.count { it == VibrationPatterns.TURN_APPROACHING })
        assertEquals(VibrationPatterns.ARRIVED, vibration.played.last())
        assertTrue(VibrationPatterns.OFF_ROUTE !in vibration.played)
        assertEquals(ARRIVED_PHRASE, speech.spoken.last())
        tracking.stop()
    }

    @Test
    fun detourWithoutInternetKeepsTheOldRouteVibratesOffRouteAndStillArrives() = runTest {
        val vibration = FakeVibration()
        val routes = OfflineRoutes()

        val tracking = walk("ruta_con_desvio.gpx", routes, vibration, FakeSpeech())

        // ~44 s fuera de ruta: un intento cada 10 s, todos fallidos, sin pasar del tope de 5.
        assertTrue("intentos: ${routes.calls}", routes.calls in 3..5)
        // Un aviso al salirse y uno por cada intento fallido.
        assertEquals(1 + routes.calls, vibration.played.count { it == VibrationPatterns.OFF_ROUTE })
        assertEquals(NavigationStatus.ARRIVED, tracking.state.value.status)
        assertEquals(VibrationPatterns.ARRIVED, vibration.played.last())
        tracking.stop()
    }

    @Test
    fun signalGapVibratesNoSignalAndRecovers() = runTest {
        val vibration = FakeVibration()
        val speech = FakeSpeech()

        val tracking = walk("ruta_sin_senal.gpx", OfflineRoutes(), vibration, speech)

        assertTrue(VibrationPatterns.NO_SIGNAL in vibration.played)
        assertTrue(SIGNAL_LOST_PHRASE in speech.spoken)
        assertTrue(SIGNAL_RECOVERED_PHRASE in speech.spoken)
        assertEquals(NavigationStatus.ARRIVED, tracking.state.value.status)
        tracking.stop()
    }
}
