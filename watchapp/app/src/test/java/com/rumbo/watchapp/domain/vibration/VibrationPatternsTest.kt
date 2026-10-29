package com.rumbo.watchapp.domain.vibration

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.vibration.VibrationCue.ARRIVED
import com.rumbo.watchapp.domain.vibration.VibrationCue.LEFT
import com.rumbo.watchapp.domain.vibration.VibrationCue.NO_SIGNAL
import com.rumbo.watchapp.domain.vibration.VibrationCue.OFF_ROUTE
import com.rumbo.watchapp.domain.vibration.VibrationCue.ON_ROUTE
import com.rumbo.watchapp.domain.vibration.VibrationCue.RIGHT
import com.rumbo.watchapp.domain.vibration.VibrationCue.TURN_APPROACHING
import com.rumbo.watchapp.testing.lRoute
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Diferencia mínima de duración que se nota al tacto entre dos pulsos (umbral de Weber ~20–30 %).
private const val MIN_NOTICEABLE_RATIO = 1.3

/** Lo que se siente sin amplitud: duración de cada pulso, pausas entre ellos y cada cuánto se repite. */
private data class Rhythm(val pulsesMs: List<Long>, val gapsMs: List<Long>, val repeatEveryMs: Long?)

private val VibrationPattern.rhythm get() = Rhythm(pulseDurationsMs, gapsMs, repeatEveryMs)

/**
 * Dos patrones se distinguen solo con encendido/apagado si tienen distinto número de pulsos o si
 * algún pulso dura al menos un 30 % más que el que ocupa su lugar en el otro.
 */
private fun distinguishableByRhythm(a: VibrationPattern, b: VibrationPattern): Boolean {
    if (a.pulseDurationsMs.size != b.pulseDurationsMs.size) return true
    return a.pulseDurationsMs.zip(b.pulseDurationsMs).any { (x, y) ->
        max(x, y).toDouble() / min(x, y) >= MIN_NOTICEABLE_RATIO
    }
}

private fun <T> pairs(items: Collection<T>): List<Pair<T, T>> {
    val list = items.toList()
    return list.indices.flatMap { i -> (i + 1 until list.size).map { j -> list[i] to list[j] } }
}

class VibrationPatternsTest {

    private val versions = listOf(VibrationPatterns.WITH_AMPLITUDE, VibrationPatterns.WITHOUT_AMPLITUDE)

    @Test
    fun bothVersionsHaveEveryCueWithItsName() {
        versions.forEach { version ->
            assertEquals(VibrationCue.entries.toSet(), version.keys)
            version.forEach { (cue, pattern) -> assertEquals(cue.title, pattern.name) }
        }
        assertEquals("Izquierda: 2 pulsos medios", VibrationPatterns.of(LEFT, supportsAmplitude = false).label)
    }

    @Test
    fun selectionDependsOnAmplitudeSupport() {
        assertSame(VibrationPatterns.WITH_AMPLITUDE, VibrationPatterns.forDevice(supportsAmplitude = true))
        assertSame(VibrationPatterns.WITHOUT_AMPLITUDE, VibrationPatterns.forDevice(supportsAmplitude = false))
        assertEquals(VibrationPatterns.WITHOUT_AMPLITUDE.getValue(ON_ROUTE), VibrationPatterns.of(ON_ROUTE, false))
        assertNotEquals(VibrationPatterns.of(ON_ROUTE, true), VibrationPatterns.of(ON_ROUTE, false))
    }

    @Test
    fun everyPatternAlternatesPauseAndVibration() {
        versions.flatMap { it.values }.forEach { pattern ->
            pattern.amplitudes.forEachIndexed { i, amplitude ->
                if (i % 2 == 0) assertEquals(pattern.label, 0, amplitude)
                else assertTrue(pattern.label, amplitude in 1..255)
            }
        }
    }

    @Test
    fun rhythmsMatchTheSpecification() {
        versions.forEach { version ->
            fun pattern(cue: VibrationCue) = version.getValue(cue)
            // Izquierda: 2 pulsos medios; derecha: 1 pulso largo.
            assertEquals(listOf(300L, 300L), pattern(LEFT).pulseDurationsMs)
            assertEquals(listOf(700L), pattern(RIGHT).pulseDurationsMs)
            // Desviado: 3 pulsos largos con pausas más cortas que ellos.
            assertEquals(listOf(600L, 600L, 600L), pattern(OFF_ROUTE).pulseDurationsMs)
            assertTrue(pattern(OFF_ROUTE).gapsMs.all { it < 600 / 2 })
            // Giro próximo: 2 pulsos muy cortos, los más cortos de todos.
            assertEquals(listOf(100L, 100L), pattern(TURN_APPROACHING).pulseDurationsMs)
            assertEquals(100L, version.values.flatMap { it.pulseDurationsMs }.min())
            // Llegada: pulsos cada vez más largos.
            assertEquals(5, pattern(ARRIVED).pulseDurationsMs.size)
            assertTrue(pattern(ARRIVED).pulseDurationsMs.zipWithNext().all { (a, b) -> b > a })
            // Sin señal: 1 pulso largo cada 5 s, más largo que el de derecha.
            assertEquals(1, pattern(NO_SIGNAL).pulseDurationsMs.size)
            assertTrue(pattern(NO_SIGNAL).pulseDurationsMs.single() > pattern(RIGHT).pulseDurationsMs.single())
            assertEquals(5_000L, pattern(NO_SIGNAL).repeatEveryMs)
            // Recto: 1 pulso cada 6 s. Solo estos dos se repiten.
            assertEquals(1, pattern(ON_ROUTE).pulseDurationsMs.size)
            assertEquals(6_000L, pattern(ON_ROUTE).repeatEveryMs)
            assertEquals(setOf(ON_ROUTE, NO_SIGNAL), version.filterValues { it.repeatEveryMs != null }.keys)
        }
    }

    @Test
    fun noTwoPatternsHaveTheSameRhythm() {
        versions.forEach { version ->
            pairs(version.values).forEach { (a, b) ->
                assertNotEquals("${a.label} / ${b.label}", a.rhythm, b.rhythm)
            }
        }
    }

    @Test
    fun withoutAmplitudeEveryPairFeelsDifferentByRhythmAlone() {
        pairs(VibrationPatterns.WITHOUT_AMPLITUDE.values).forEach { (a, b) ->
            assertTrue("${a.label} se confunde con ${b.label}", distinguishableByRhythm(a, b))
        }
    }

    @Test
    fun withoutAmplitudeNothingDependsOnStrength() {
        val strengths = VibrationPatterns.WITHOUT_AMPLITUDE.values.flatMap { it.pulseAmplitudes }.toSet()
        assertEquals(1, strengths.size)
        // "Recto" no puede ser suave: es el único pulso corto que se repite, para no parecer un aviso.
        val onOff = VibrationPatterns.of(ON_ROUTE, supportsAmplitude = false).pulseDurationsMs.single()
        val soft = VibrationPatterns.of(ON_ROUTE, supportsAmplitude = true).pulseDurationsMs.single()
        assertTrue(onOff < soft)
    }

    @Test
    fun withAmplitudeStrengthReinforcesTheRhythm() {
        val version = VibrationPatterns.WITH_AMPLITUDE
        // Lo mismo se distingue sin amplitud: la fuerza solo suma.
        pairs(version.values).forEach { (a, b) ->
            assertTrue("${a.label} se confunde con ${b.label}", distinguishableByRhythm(a, b))
        }
        val onRoute = version.getValue(ON_ROUTE).pulseAmplitudes.single()
        assertTrue(version.values.flatMap { it.pulseAmplitudes }.all { it >= onRoute })
        assertTrue(version.getValue(ARRIVED).pulseAmplitudes.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun pulsesKeepTheirPlaceInThePattern() {
        assertEquals(
            listOf(Pulse(0, 300, 160), Pulse(500, 300, 160)),
            VibrationPatterns.of(LEFT, supportsAmplitude = true).pulses,
        )
        assertEquals(listOf(150L, 150L), VibrationPatterns.of(OFF_ROUTE, supportsAmplitude = true).gapsMs)
        versions.flatMap { it.values }.forEach { pattern ->
            assertEquals(pattern.label, pattern.pulseDurationsMs, pattern.pulses.map { it.durationMs })
            assertTrue(pattern.label, pattern.pulses.all { it.startMs + it.durationMs <= pattern.durationMs })
        }
    }

    @Test
    fun eventsMapToCues() = with(VibrationPatterns) {
        assertEquals(TURN_APPROACHING, forEvent(NavigationEvent.TurnApproaching(Maneuver.LEFT)))
        assertEquals(LEFT, forEvent(NavigationEvent.TurnNow(Maneuver.LEFT)))
        assertEquals(RIGHT, forEvent(NavigationEvent.TurnNow(Maneuver.RIGHT)))
        assertEquals(OFF_ROUTE, forEvent(NavigationEvent.OffRoute))
        assertEquals(NO_SIGNAL, forEvent(NavigationEvent.SignalLost))
        assertEquals(ARRIVED, forEvent(NavigationEvent.Arrived))
        assertEquals(ON_ROUTE, forEvent(NavigationEvent.BackOnRoute))
        assertNull(forEvent(NavigationEvent.RecalculateRequested))
        assertNull(forEvent(NavigationEvent.NextStepChanged(lRoute().steps[1])))
    }

    @Test
    fun mostImportantCueWins() {
        val events = listOf(NavigationEvent.TurnNow(Maneuver.RIGHT), NavigationEvent.Arrived)
        assertEquals(ARRIVED, VibrationPatterns.mostImportant(events))
        assertNull(VibrationPatterns.mostImportant(emptyList()))
    }
}
