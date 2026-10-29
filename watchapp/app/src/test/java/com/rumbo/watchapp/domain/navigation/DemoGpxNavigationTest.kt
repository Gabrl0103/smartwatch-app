package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.geo.cumulativeDistances
import com.rumbo.watchapp.domain.geo.snapToPolyline
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus.ARRIVED
import com.rumbo.watchapp.domain.navigation.NavigationStatus.MANEUVER
import com.rumbo.watchapp.domain.navigation.NavigationStatus.NO_SIGNAL
import com.rumbo.watchapp.domain.navigation.NavigationStatus.OFF_ROUTE
import com.rumbo.watchapp.domain.navigation.NavigationStatus.ON_ROUTE
import com.rumbo.watchapp.domain.navigation.NavigationStatus.TURN_APPROACHING
import com.rumbo.watchapp.testing.GpxPoint
import com.rumbo.watchapp.testing.Replay
import com.rumbo.watchapp.testing.readDemoRoute
import com.rumbo.watchapp.testing.readGpx
import com.rumbo.watchapp.testing.realisticWalk
import com.rumbo.watchapp.testing.replay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Un estado que aparece y desaparece en menos de esto entre dos iguales es un parpadeo.
private const val MIN_STABLE_MS = 3_000L

private val ON_ROUTE_STATES = setOf(ON_ROUTE, TURN_APPROACHING, MANEUVER)

// Recorridos realistas generados en el test, además de los tres GPX de docs/demo.
private const val SEEDS = 30

/**
 * La máquina de estados con la ruta real del backend (Plaza de Nariño → Parque Bolívar, 1 791 m,
 * 5 giros) y los tres recorridos realistas de docs/demo (ruido de GPS, velocidad variable, pausas
 * en los cruces, saltos de ~40 m y pérdidas breves de señal), pasando cada punto por el mismo
 * filtro que en el reloj: el normal pasa por Giro próximo → Gira ahora en cada giro y llega, el
 * del desvío por Fuera de ruta → recuperada, y el del hueco por Sin señal → recuperada, sin
 * parpadeos de estado.
 */
class DemoGpxNavigationTest {

    private val route = readDemoRoute()
    private val turns = route.steps.count { it.maneuver == Maneuver.LEFT || it.maneuver == Maneuver.RIGHT }
    private val cumulative = cumulativeDistances(route.polyline)

    private fun walk(gpx: String) = replay(route, readGpx(gpx))

    private fun distanceToRoute(point: GpxPoint) =
        snapToPolyline(Coordinates(point.lat, point.lng), route.polyline, cumulative).distanceToRouteM

    /** Ningún estado se mete menos de [MIN_STABLE_MS] entre dos iguales (p. ej. En ruta → Fuera → En ruta). */
    private fun assertNoFlicker(replay: Replay) {
        replay.runs.windowed(3).forEach { (before, middle, after) ->
            val flicker = before.status == after.status && middle.durationMs < MIN_STABLE_MS
            assertTrue("Parpadeo: ${before.status} → ${middle.status} (${middle.durationMs} ms) → ${after.status}\n${replay.describe()}", !flicker)
        }
    }

    /** Cada giro se avisa una vez y en orden: Giro próximo y luego Gira ahora, nunca al revés. */
    private fun assertTurnsInOrder(replay: Replay) {
        replay.runs.zipWithNext().forEach { (current, next) ->
            if (current.status == TURN_APPROACHING) {
                assertEquals("Tras Giro próximo viene Gira ahora\n${replay.describe()}", MANEUVER, next.status)
            }
            if (current.status == MANEUVER) {
                assertTrue("Tras Gira ahora: ${next.status}\n${replay.describe()}", next.status in setOf(ON_ROUTE, TURN_APPROACHING, ARRIVED))
            }
        }
    }

    /** Sin desvíos ni giros falsos: cada giro de la ruta avisa una vez en sus dos etapas y llega. */
    private fun assertCleanWalk(replay: Replay, label: String) {
        val description = "$label\n${replay.describe()}"
        assertTrue("Nunca fuera de ruta ni sin señal: $description", replay.statuses.none { it == OFF_ROUTE || it == NO_SIGNAL })
        assertEquals("Giros próximos: $description", turns, replay.countOf<NavigationEvent.TurnApproaching>())
        assertEquals("Gira ahora: $description", turns, replay.countOf<NavigationEvent.TurnNow>())
        assertEquals("Llegadas: $description", 1, replay.count(NavigationEvent.Arrived))
        assertEquals(description, ARRIVED, replay.statuses.last())
        assertTurnsInOrder(replay)
        assertNoFlicker(replay)
    }

    @Test
    fun theDemoRouteIsTheBackendRoute() {
        // less_maneuvers: 5 giros por calles en cuadrícula y la llegada; el primero, a 4 m del inicio.
        assertEquals(5, turns)
        assertEquals(1791.0, route.distanceM, 1.0)
        assertEquals("Gire a la derecha hacia Carrera 25.", route.steps.first().instruction)
        assertEquals(Maneuver.ARRIVE, route.steps.last().maneuver)
    }

    @Test
    fun theDemoTracksLookLikeARealWatchGps() {
        listOf("ruta_parque_narino_a_parque_bolivar.gpx", "ruta_con_desvio.gpx", "ruta_sin_senal.gpx").forEach { gpx ->
            val track = readGpx(gpx)
            val seconds = track.zipWithNext { a, b -> (b.timeMs - a.timeMs) / 1_000 }
            // Una lectura por segundo, con al menos una pérdida breve de 2 a 3 s.
            assertTrue(gpx, seconds.all { it >= 1 })
            assertTrue(gpx, seconds.any { it in 3..4 })
            assertTrue(gpx, track.all { it.accuracyM in 5.0..15.0 })
            assertTrue(gpx, track.map { it.accuracyM }.toSet().size > 5)
            // Ruido de unos metros y algún salto de más de 30 m.
            val offsets = track.map(::distanceToRoute).sorted()
            assertTrue("$gpx: mediana ${offsets[offsets.size / 2]}", offsets[offsets.size / 2] in 1.0..4.0)
            assertTrue(gpx, offsets.last() > 30.0)
            // Se detiene en los cruces: hay segundos seguidos casi en el mismo lugar.
            val speeds = track.zipWithNext { a, b -> haversine(a, b) / ((b.timeMs - a.timeMs) / 1_000.0) }
            assertTrue(gpx, speeds.count { it < 0.5 } > 0)
        }
        // El del hueco deja 15 s sin puntos.
        assertTrue(readGpx("ruta_sin_senal.gpx").zipWithNext { a, b -> b.timeMs - a.timeMs }.any { it == 16_000L })
    }

    @Test
    fun normalWalkWarnsEveryTurnInTwoStagesAndArrives() {
        val replay = walk("ruta_parque_narino_a_parque_bolivar.gpx")

        assertTrue(replay.describe(), replay.statuses.first() in ON_ROUTE_STATES)
        // Tras una calle larga, el giro se ve venir: En ruta → Giro próximo → Gira ahora.
        assertTrue(replay.describe(), listOf(ON_ROUTE, TURN_APPROACHING, MANEUVER) in replay.statuses.windowed(3))
        assertCleanWalk(replay, "GPX normal")
    }

    @Test
    fun realisticNoiseNeverFakesDetoursOrTurnsAndAlwaysArrives() {
        (1L..SEEDS).forEach { seed ->
            assertCleanWalk(replay(route, realisticWalk(route, seed)), "semilla $seed")
        }
    }

    @Test
    fun detourGoesOffRouteOnceAsksToRecalculateAndRecovers() {
        val replay = walk("ruta_con_desvio.gpx")

        assertEquals("Un solo tramo fuera de ruta\n${replay.describe()}", 1, replay.statuses.count { it == OFF_ROUTE })
        val off = replay.statuses.indexOf(OFF_ROUTE)
        assertTrue(replay.statuses[off - 1] in ON_ROUTE_STATES)
        assertTrue("Recuperada después del desvío\n${replay.describe()}", replay.statuses[off + 1] in ON_ROUTE_STATES)
        // ~40 m caminando lejos de la ruta: dura lo suficiente para pedir al menos un recálculo.
        assertTrue(replay.runs[off].durationMs >= 10_000)

        assertEquals(1, replay.count(NavigationEvent.OffRoute))
        assertEquals(1, replay.count(NavigationEvent.BackOnRoute))
        assertTrue(replay.count(NavigationEvent.RecalculateRequested) >= 1)
        assertTrue(replay.statuses.none { it == NO_SIGNAL })
        assertEquals(ARRIVED, replay.statuses.last())
        assertEquals(1, replay.count(NavigationEvent.Arrived))
        assertTurnsInOrder(replay)
        assertNoFlicker(replay)
    }

    @Test
    fun signalGapShowsNoSignalOnceAndRecovers() {
        val replay = walk("ruta_sin_senal.gpx")

        assertEquals("Un solo tramo sin señal\n${replay.describe()}", 1, replay.statuses.count { it == NO_SIGNAL })
        val lost = replay.statuses.indexOf(NO_SIGNAL)
        assertTrue(replay.statuses[lost - 1] in ON_ROUTE_STATES)
        assertTrue("Recuperada después del hueco\n${replay.describe()}", replay.statuses[lost + 1] in ON_ROUTE_STATES)

        assertEquals(1, replay.count(NavigationEvent.SignalLost))
        assertEquals(1, replay.count(NavigationEvent.SignalRecovered))
        assertTrue("Sin falsos desvíos al volver la señal\n${replay.describe()}", replay.statuses.none { it == OFF_ROUTE })
        assertEquals(ARRIVED, replay.statuses.last())
        assertEquals(1, replay.count(NavigationEvent.Arrived))
        assertTurnsInOrder(replay)
        assertNoFlicker(replay)
    }

    private fun haversine(a: GpxPoint, b: GpxPoint) =
        com.rumbo.watchapp.domain.geo.haversineM(Coordinates(a.lat, a.lng), Coordinates(b.lat, b.lng))
}
