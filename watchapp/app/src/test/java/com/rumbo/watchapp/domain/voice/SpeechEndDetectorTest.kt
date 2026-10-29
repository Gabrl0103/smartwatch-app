package com.rumbo.watchapp.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cuántos milisegundos se graba con una secuencia de niveles (uno cada 80 ms) que se repite al final. */
private fun recordedMs(levels: List<Float>, detector: SpeechEndDetector = SpeechEndDetector()): Long {
    var samples = 0L
    while (true) {
        val level = levels.getOrElse(samples.toInt()) { levels.last() }
        samples++
        if (detector.onLevel(level)) return samples * LEVEL_SAMPLE_MS
        check(samples < 1_000) { "Nunca se detuvo" }
    }
}

private fun ms(duration: Long, level: Float) = List((duration / LEVEL_SAMPLE_MS).toInt()) { level }

class SpeechEndDetectorTest {

    @Test
    fun stopsAfterOneSecondAndAHalfOfSilenceFollowingSpeech() {
        // 400 ms de espera, 1,6 s hablando y luego silencio: corta ~1,2 s después de la última palabra.
        val levels = ms(400, 0.05f) + ms(1_600, 0.8f) + listOf(0.05f)
        assertEquals(2_000 + 1_200L, recordedMs(levels))
    }

    @Test
    fun pausesBetweenWordsDoNotStopTheRecording() {
        // Pausas de 800 ms entre palabras (menos de 1,2 s): sigue grabando hasta el silencio final.
        val word = ms(400, 0.8f)
        val pause = ms(800, 0.1f)
        val levels = word + pause + word + pause + word + listOf(0.1f)
        assertEquals(3 * 400 + 2 * 800 + 1_200L, recordedMs(levels))
    }

    @Test
    fun aVeryShortWordStillRecordsAtLeastOneSecond() {
        // "Sí" de 240 ms con un silencio de corte de 400 ms: a los 640 ms ya cortaría, pero el
        // mínimo de 1 s manda (la primera lectura que pasa de 1 s es la de 1 040 ms).
        val detector = SpeechEndDetector(silenceToStopMs = 400)
        assertEquals(1_040L, recordedMs(listOf(0.9f, 0.9f, 0.9f, 0.1f), detector))
    }

    @Test
    fun withoutVoiceItWaitsUntilTheMaximum() {
        assertEquals(MAX_RECORDING_MS, recordedMs(listOf(0.1f)))
    }

    @Test
    fun neverRecordsMoreThanEightSeconds() {
        assertEquals(MAX_RECORDING_MS, recordedMs(listOf(0.9f)))
    }

    @Test
    fun aSingleSpikeIsNotVoice() {
        // Un clic de 80 ms al empezar, suavizado como lo da el micrófono (1 → 0,6, 0,45, 0,34...),
        // no corta la grabación al segundo: se espera a que la persona hable.
        val smoother = LevelSmoother()
        val click = (listOf(1f) + List(40) { 0f }).map(smoother::next)
        assertEquals(MAX_RECORDING_MS, recordedMs(click))
        // Tampoco varios clics sueltos.
        val clicks = List(5) { listOf(1f) + List(10) { 0f } }.flatten().map(LevelSmoother()::next)
        assertEquals(MAX_RECORDING_MS, recordedMs(clicks + listOf(0f)))
    }

    @Test
    fun aRealWordIsVoiceEvenWhenSmoothed() {
        // Una palabra de 400 ms y silencio: corta 1,2 s después de que el nivel suavizado baja.
        val smoother = LevelSmoother()
        val levels = (ms(400, 0.9f) + ms(3_000, 0f)).map(smoother::next)
        val stoppedAt = recordedMs(levels)
        assertTrue("$stoppedAt", stoppedAt in 1_600L..2_000L)
    }

    @Test
    fun noiseBelowTheThresholdIsNotVoice() {
        val detector = SpeechEndDetector()
        repeat(30) { assertFalse(detector.onLevel(VOICE_LEVEL_THRESHOLD - 0.01f)) }
    }
}
