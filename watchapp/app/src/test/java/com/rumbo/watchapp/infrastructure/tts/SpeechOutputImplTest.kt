package com.rumbo.watchapp.infrastructure.tts

import com.rumbo.watchapp.domain.error.QuotaExhaustedError
import com.rumbo.watchapp.domain.port.SpeechEngine
import com.rumbo.watchapp.domain.port.SpeechPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOutputImplTest {

    /**
     * Reproductor falso: cada frase "suena" hasta que el test la termina con [finish]. Con
     * [holdAudio] el audio no empieza hasta [startAudio] (como Gemini mientras descarga).
     */
    private open class FakePlayer : SpeechPlayer {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val finished = mutableListOf<String>()
        var error: Exception? = null
        var holdAudio = false
        private val playing = mutableMapOf<String, CompletableDeferred<Unit>>()
        private val audioStarts = mutableMapOf<String, () -> Unit>()

        override suspend fun play(text: String, onStart: () -> Unit) {
            started += text
            error?.let { throw it }
            if (holdAudio) audioStarts[text] = onStart else onStart()
            val done = CompletableDeferred<Unit>().also { playing[text] = it }
            try {
                done.await()
            } catch (e: CancellationException) {
                cancelled += text
                throw e
            }
            finished += text
        }

        fun finish(text: String) {
            playing.getValue(text).complete(Unit)
        }

        fun startAudio(text: String) {
            audioStarts.getValue(text)()
        }
    }

    private class FakeLocal(initial: LocalVoiceState) : FakePlayer(), LocalSpeechPlayer {
        override val state = MutableStateFlow(initial)
    }

    private class Setup(
        val local: FakeLocal,
        val remote: FakePlayer,
        val speech: SpeechOutputImpl,
        val errors: MutableList<Exception>,
    )

    private var now = 0L

    // Cada test corre en un scope hijo que se cancela al final: así no quedan corrutinas vivas
    // (el estado del motor local se observa para siempre y la pausa por cuota dura 30 min).
    private fun speechTest(
        local: LocalVoiceState = LocalVoiceState.UNAVAILABLE,
        enabled: Boolean = true,
        localStartWaitMs: Long = 3_000,
        block: suspend Setup.() -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val fakeLocal = FakeLocal(local)
        val remote = FakePlayer()
        val errors = mutableListOf<Exception>()
        val speech = SpeechOutputImpl(
            fakeLocal,
            remote,
            isEnabled = { enabled },
            scope = scope,
            clock = { now },
            onError = { errors += it },
            localStartWaitMs = localStartWaitMs,
        )
        try {
            settle()
            Setup(fakeLocal, remote, speech, errors).block()
        } finally {
            scope.cancel()
        }
    }

    private suspend fun settle() = repeat(5) { yield() }

    @Test
    fun speakingWhileTheAudioPlays() = speechTest {
        assertFalse(speech.speaking.value)
        speech.speak("Parque Bolívar, a un kilómetro y medio. ¿Vamos?", SpeechPriority.NORMAL)
        settle()
        assertTrue(speech.speaking.value)

        remote.finish("Parque Bolívar, a un kilómetro y medio. ¿Vamos?")
        settle()
        assertFalse(speech.speaking.value)
    }

    @Test
    fun notSpeakingWhileGeminiIsStillDownloadingTheAudio() = speechTest {
        remote.holdAudio = true
        speech.speak("Uno", SpeechPriority.NORMAL)
        settle()
        assertFalse(speech.speaking.value)

        remote.startAudio("Uno")
        assertTrue(speech.speaking.value)
    }

    @Test
    fun stopOrANewPhraseEndsTheSpeakingOfTheOldOne() = speechTest {
        speech.speak("Uno", SpeechPriority.NORMAL)
        settle()
        speech.stop()
        settle()
        assertFalse(speech.speaking.value)

        speech.speak("Dos", SpeechPriority.NORMAL)
        settle()
        assertTrue(speech.speaking.value)
        // "Tres" corta a "Dos" pero su audio aún no empieza: mientras tanto no se habla.
        remote.holdAudio = true
        speech.speak("Tres", SpeechPriority.NORMAL)
        settle()
        assertFalse(speech.speaking.value)
        remote.startAudio("Tres")
        assertTrue(speech.speaking.value)
    }

    @Test
    fun deviceVoiceAlsoMarksSpeaking() = speechTest(local = LocalVoiceState.READY) {
        speech.speak("Iniciando ruta.", SpeechPriority.NORMAL)
        settle()
        assertEquals(listOf("Iniciando ruta."), local.started)
        assertTrue(speech.speaking.value)
        local.finish("Iniciando ruta.")
        settle()
        assertFalse(speech.speaking.value)
    }

    @Test
    fun newPhraseCancelsThePreviousOne() = speechTest {
        speech.speak("Uno", SpeechPriority.NORMAL)
        settle()
        speech.speak("Dos", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Uno", "Dos"), remote.started)
        assertEquals(listOf("Uno"), remote.cancelled)
    }

    @Test
    fun highPriorityCancelsNormal() = speechTest {
        speech.speak("Señal recuperada.", SpeechPriority.NORMAL)
        settle()
        speech.speak("Gira a la izquierda.", SpeechPriority.HIGH)
        settle()

        assertEquals(listOf("Señal recuperada."), remote.cancelled)
        assertEquals(listOf("Señal recuperada.", "Gira a la izquierda."), remote.started)
    }

    @Test
    fun normalDoesNotInterruptHighAndPlaysAfterItEnds() = speechTest {
        speech.speak("Llegaste a tu destino.", SpeechPriority.HIGH)
        settle()
        speech.speak("Perdí la señal de GPS.", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Llegaste a tu destino."), remote.started)
        assertEquals(emptyList<String>(), remote.cancelled)

        remote.finish("Llegaste a tu destino.")
        settle()
        speech.speak("Señal recuperada.", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Llegaste a tu destino."), remote.finished)
        assertEquals(listOf("Llegaste a tu destino.", "Señal recuperada."), remote.started)
    }

    @Test
    fun highReplacesHigh() = speechTest {
        speech.speak("En treinta metros, gire a la izquierda.", SpeechPriority.HIGH)
        settle()
        speech.speak("Gira a la izquierda.", SpeechPriority.HIGH)
        settle()

        assertEquals(listOf("En treinta metros, gire a la izquierda."), remote.cancelled)
        assertEquals("Gira a la izquierda.", remote.started.last())
    }

    @Test
    fun stopSilencesCurrentPhrase() = speechTest {
        speech.speak("Iniciando ruta.", SpeechPriority.HIGH)
        settle()
        speech.stop()
        settle()
        speech.speak("Perdí la señal de GPS.", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Iniciando ruta."), remote.cancelled)
        assertEquals("Perdí la señal de GPS.", remote.started.last())
    }

    @Test
    fun disabledVoiceSaysNothing() = speechTest(local = LocalVoiceState.READY, enabled = false) {
        speech.speak("Gira a la derecha.", SpeechPriority.HIGH)
        settle()

        assertEquals(emptyList<String>(), local.started)
        assertEquals(emptyList<String>(), remote.started)
    }

    @Test
    fun playerErrorIsReportedAndNextPhraseStillPlays() = speechTest {
        remote.error = IllegalStateException("sin audio")

        speech.speak("Uno", SpeechPriority.HIGH)
        settle()
        remote.error = null
        speech.speak("Dos", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("sin audio"), errors.map { it.message })
        assertEquals(listOf("Uno", "Dos"), remote.started)
    }

    @Test
    fun deviceVoiceIsUsedFirstAndGeminiIsNotCalled() = speechTest(local = LocalVoiceState.READY) {
        speech.speak("Gira a la izquierda.", SpeechPriority.HIGH)
        settle()

        assertEquals(listOf("Gira a la izquierda."), local.started)
        assertEquals(emptyList<String>(), remote.started)
        assertEquals(SpeechEngine.DEVICE, speech.status.value.engine)
    }

    @Test
    fun geminiOnlyWhenDeviceVoiceIsUnavailable() = speechTest(local = LocalVoiceState.UNAVAILABLE) {
        speech.speak("Gira a la izquierda.", SpeechPriority.HIGH)
        settle()

        assertEquals(emptyList<String>(), local.started)
        assertEquals(listOf("Gira a la izquierda."), remote.started)
        assertEquals(SpeechEngine.GEMINI, speech.status.value.engine)
    }

    @Test
    fun waitsForDeviceVoiceThatIsStillStarting() = speechTest(local = LocalVoiceState.STARTING) {
        speech.speak("Parque Bolívar. ¿Vamos?", SpeechPriority.NORMAL)
        settle()
        local.state.value = LocalVoiceState.READY
        settle()

        assertEquals(listOf("Parque Bolívar. ¿Vamos?"), local.started)
        assertEquals(emptyList<String>(), remote.started)
        assertEquals(SpeechEngine.DEVICE, speech.status.value.engine)
    }

    @Test
    fun deviceVoiceThatNeverStartsFallsBackToGemini() =
        speechTest(local = LocalVoiceState.STARTING, localStartWaitMs = 30) {
            speech.speak("Iniciando ruta.", SpeechPriority.NORMAL)
            delay(80)
            settle()

            assertEquals(listOf("Iniciando ruta."), remote.started)
        }

    @Test
    fun deviceVoiceFailureFallsBackToGemini() = speechTest(local = LocalVoiceState.READY) {
        local.error = IllegalStateException("TextToSpeech falló")

        speech.speak("Llegaste a tu destino.", SpeechPriority.HIGH)
        settle()

        assertEquals(listOf("Llegaste a tu destino."), remote.started)
        assertEquals(listOf("TextToSpeech falló"), errors.map { it.message })
    }

    @Test
    fun quotaExhaustedPausesGeminiForThirtyMinutes() = speechTest {
        remote.error = QuotaExhaustedError("Se agotó la cuota gratuita de voz de Gemini.")

        speech.speak("Uno", SpeechPriority.NORMAL)
        settle()
        assertEquals(SpeechEngine.NONE, speech.status.value.engine)

        remote.error = null
        now = 29 * 60 * 1000L
        speech.speak("Dos", SpeechPriority.NORMAL)
        settle()
        assertEquals(listOf("Uno"), remote.started)

        now = QUOTA_PAUSE_MS + 1
        speech.speak("Tres", SpeechPriority.NORMAL)
        settle()
        assertEquals(listOf("Uno", "Tres"), remote.started)
        assertEquals(SpeechEngine.GEMINI, speech.status.value.engine)
    }

    @Test
    fun missingSpanishDataIsReported() = speechTest(local = LocalVoiceState.MISSING_DATA) {
        assertTrue(speech.status.value.deviceVoiceMissing)
        assertEquals(SpeechEngine.GEMINI, speech.status.value.engine)
    }
}
