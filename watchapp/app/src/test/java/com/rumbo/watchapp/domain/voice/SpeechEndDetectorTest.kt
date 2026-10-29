package com.rumbo.watchapp.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // "Sí" de 80 ms con un silencio de corte de 400 ms: a los 480 ms ya cortaría, pero el mínimo
        // de 1 s manda (la primera lectura que pasa de 1 s es la de 1 040 ms).
        val detector = SpeechEndDetector(silenceToStopMs = 400)
        assertEquals(1_040L, recordedMs(listOf(0.9f, 0.1f), detector))
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
    fun noiseBelowTheThresholdIsNotVoice() {
        val detector = SpeechEndDetector()
        repeat(30) { assertFalse(detector.onLevel(VOICE_LEVEL_THRESHOLD - 0.01f)) }
    }
}
