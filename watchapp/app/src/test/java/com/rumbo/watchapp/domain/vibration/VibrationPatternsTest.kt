package com.rumbo.watchapp.domain.vibration

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationEvent
import com.rumbo.watchapp.domain.vibration.VibrationCue.ARRIVED
import com.rumbo.watchapp.domain.vibration.VibrationCue.ATTENTION
import com.rumbo.watchapp.domain.vibration.VibrationCue.COMMAND_ERROR
import com.rumbo.watchapp.domain.vibration.VibrationCue.COMMAND_OK
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
        assertNull("El aviso lejano es solo de voz", forEvent(NavigationEvent.TurnAhead(Maneuver.LEFT)))
        assertNull("El destino cercano es solo de voz", forEvent(NavigationEvent.DestinationNear))
    }

    @Test
    fun specialManeuversVibrateTheirSide() = with(VibrationPatterns) {
        listOf(Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT, Maneuver.U_TURN_LEFT).forEach {
            assertEquals(LEFT, forEvent(NavigationEvent.TurnNow(it)))
        }
        listOf(Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT, Maneuver.U_TURN_RIGHT).forEach {
            assertEquals(RIGHT, forEvent(NavigationEvent.TurnNow(it)))
        }
    }

    @Test
    fun anUnknownStepVibratesAttentionInBothStagesNeverATurn() = with(VibrationPatterns) {
        assertEquals(ATTENTION, forEvent(NavigationEvent.TurnApproaching(Maneuver.UNKNOWN)))
        assertEquals(ATTENTION, forEvent(NavigationEvent.TurnNow(Maneuver.UNKNOWN)))
        // Si las dos etapas llegan juntas se siente una sola vez.
        val both = listOf(NavigationEvent.TurnApproaching(Maneuver.UNKNOWN), NavigationEvent.TurnNow(Maneuver.UNKNOWN))
        assertEquals(listOf(ATTENTION), cuesFor(both))
    }

    @Test
    fun attentionIsFourShortPulsesInEveryVersion() {
        versions.forEach { version ->
            assertEquals(listOf(150L, 150L, 150L, 150L), version.getValue(ATTENTION).pulseDurationsMs)
            assertEquals(null, version.getValue(ATTENTION).repeatEveryMs)
        }
    }

    @Test
    fun aCommandThatWorkedIsADoubleShortPulseNotATurnWarning() {
        versions.forEach { version ->
            val done = version.getValue(COMMAND_OK)
            assertEquals(listOf(180L, 180L), done.pulseDurationsMs)
            assertEquals(null, done.repeatEveryMs)
            // Mismos 2 pulsos que "Giro próximo" e "Izquierda": se distinguen por la duración.
            assertTrue(distinguishableByRhythm(done, version.getValue(TURN_APPROACHING)))
            assertTrue(distinguishableByRhythm(done, version.getValue(LEFT)))
        }
    }

    @Test
    fun aCommandThatFailedIsTheLongestSinglePulse() {
        versions.forEach { version ->
            val error = version.getValue(COMMAND_ERROR)
            assertEquals(listOf(1_400L), error.pulseDurationsMs)
            assertEquals(null, error.repeatEveryMs)
            assertTrue(distinguishableByRhythm(error, version.getValue(NO_SIGNAL)))
        }
    }

    @Test
    fun theResultOfACommandNeverComesFromTheRoute() = with(VibrationPatterns) {
        val events = listOf(
            NavigationEvent.TurnApproaching(Maneuver.LEFT),
            NavigationEvent.TurnNow(Maneuver.LEFT),
            NavigationEvent.OffRoute,
            NavigationEvent.SignalLost,
            NavigationEvent.Arrived,
        )

        assertTrue(events.mapNotNull(::forEvent).none { it == COMMAND_OK || it == COMMAND_ERROR })
    }

    @Test
    fun attentionIsShownInTheVibrationsScreenOrder() {
        assertTrue(ATTENTION in VibrationCue.entries)
        assertEquals("Atención", ATTENTION.title)
        assertEquals("Atención: 4 pulsos cortos seguidos", VibrationPatterns.of(ATTENTION, supportsAmplitude = false).label)
    }

    @Test
    fun everyTurnVibratesAtTheManeuverEvenARoundaboutWithoutSide() = with(VibrationPatterns) {
        assertEquals(TURN_APPROACHING, forEvent(NavigationEvent.TurnNow(Maneuver.ROUNDABOUT, 2)))
        Maneuver.entries.filter { it.isTurn }.forEach {
            assertEquals(it.name, true, forEvent(NavigationEvent.TurnNow(it)) != null)
        }
    }

    @Test
    fun mostImportantCueWins() {
        val events = listOf(NavigationEvent.TurnNow(Maneuver.RIGHT), NavigationEvent.Arrived)
        assertEquals(ARRIVED, VibrationPatterns.mostImportant(events))
        assertNull(VibrationPatterns.mostImportant(emptyList()))
    }

    private val allSettings = VibrationIntensity.entries.flatMap { intensity ->
        PulseDuration.entries.map { VibrationSettings(intensity, it) }
    }

    @Test
    fun normalSettingsChangeNothing() {
        listOf(true, false).forEach { amplitude ->
            assertEquals(VibrationPatterns.forDevice(amplitude), VibrationPatterns.forDevice(amplitude, VibrationSettings()))
        }
    }

    @Test
    fun longDurationStretchesPulsesThirtyPercentButNotPauses() {
        val long = VibrationSettings(duration = PulseDuration.LONG)
        listOf(true, false).forEach { amplitude ->
            val left = VibrationPatterns.of(LEFT, amplitude, long)
            assertEquals(listOf(390L, 390L), left.pulseDurationsMs)
            assertEquals(listOf(200L), left.gapsMs)
        }
        assertEquals(listOf(1_300L), VibrationPatterns.of(NO_SIGNAL, true, long).pulseDurationsMs)
        assertEquals(5_000L, VibrationPatterns.of(NO_SIGNAL, true, long).repeatEveryMs)
    }

    @Test
    fun intensityScalesStrengthOnlyWhenTheWatchControlsIt() {
        assertEquals(listOf(96, 96), VibrationPatterns.of(LEFT, true, VibrationSettings(VibrationIntensity.SOFT)).pulseAmplitudes)
        assertEquals(listOf(200, 200), VibrationPatterns.of(LEFT, true, VibrationSettings(VibrationIntensity.STRONG)).pulseAmplitudes)
        allSettings.forEach { settings ->
            assertEquals(
                VibrationPatterns.of(LEFT, supportsAmplitude = false).pulseAmplitudes,
                VibrationPatterns.of(LEFT, false, settings).pulseAmplitudes,
            )
        }
    }

    @Test
    fun everySettingKeepsPatternsValidAndDistinguishable() {
        allSettings.forEach { settings ->
            listOf(true, false).forEach { amplitude ->
                val version = VibrationPatterns.forDevice(amplitude, settings)
                assertTrue(version.values.flatMap { it.pulseAmplitudes }.all { it in 1..255 })
                version.values.forEach { assertTrue(it.label, it.durationMs < (it.repeatEveryMs ?: Long.MAX_VALUE)) }
                pairs(version.values).forEach { (a, b) ->
                    assertTrue("$settings: ${a.label} se confunde con ${b.label}", distinguishableByRhythm(a, b))
                }
            }
            val arrived = VibrationPatterns.of(ARRIVED, true, settings).pulseAmplitudes
            assertTrue("$settings", arrived.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test
    fun settingsCycleWithOneButton() {
        assertEquals(VibrationIntensity.STRONG, VibrationIntensity.NORMAL.next())
        assertEquals(VibrationIntensity.SOFT, VibrationIntensity.STRONG.next())
        assertEquals(PulseDuration.LONG, PulseDuration.NORMAL.next())
        assertEquals(PulseDuration.NORMAL, PulseDuration.LONG.next())
    }

    @Test
    fun turnVibratesInTwoStagesWhenBothArriveTogether() = with(VibrationPatterns) {
        val left = NavigationEvent.TurnNow(Maneuver.LEFT)
        val approaching = NavigationEvent.TurnApproaching(Maneuver.LEFT)
        assertEquals(listOf(TURN_APPROACHING, LEFT), cuesFor(listOf(approaching, left)))
        assertEquals(listOf(TURN_APPROACHING), cuesFor(listOf(approaching)))
        assertEquals(listOf(RIGHT), cuesFor(listOf(NavigationEvent.TurnNow(Maneuver.RIGHT))))
        assertEquals(listOf(ARRIVED), cuesFor(listOf(approaching, left, NavigationEvent.Arrived)))
        assertEquals(emptyList<VibrationCue>(), cuesFor(listOf(NavigationEvent.RecalculateRequested)))

        val twoStages = forCues(listOf(TURN_APPROACHING, LEFT), true, VibrationSettings())
        assertEquals(listOf(100L, 100L, 300L, 300L), twoStages.pulseDurationsMs)
        assertEquals(listOf(150L, TWO_STAGE_GAP_MS, 200L), twoStages.gapsMs)
        assertEquals("Giro próximo, luego izquierda: 2 pulsos muy cortos y 2 pulsos medios", twoStages.label)
        assertNull(twoStages.repeatEveryMs)
        // Cabe en el log de vibraciones del backend (100 caracteres).
        assertTrue(forCues(listOf(TURN_APPROACHING, RIGHT), false, VibrationSettings()).label.length <= 100)
        // La pausa entre etapas no se alarga con "Duración: larga".
        val long = forCues(listOf(TURN_APPROACHING, RIGHT), false, VibrationSettings(duration = PulseDuration.LONG))
        assertEquals(listOf(130L, 130L, 910L), long.pulseDurationsMs)
        assertEquals(listOf(150L, TWO_STAGE_GAP_MS), long.gapsMs)
        // La pausa entre etapas es más larga que cualquiera dentro de un patrón: se sienten dos avisos.
        assertTrue(versions.flatMap { it.values }.flatMap { it.gapsMs }.all { it < TWO_STAGE_GAP_MS })
    }
}
