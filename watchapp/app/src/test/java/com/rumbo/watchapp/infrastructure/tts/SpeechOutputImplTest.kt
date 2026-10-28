package com.rumbo.watchapp.infrastructure.tts

import com.rumbo.watchapp.domain.port.SpeechPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechOutputImplTest {

    /** Reproductor falso: cada frase "suena" hasta que el test la termina con [finish]. */
    private class FakePlayer : SpeechPlayer {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val finished = mutableListOf<String>()
        var error: Exception? = null
        private val playing = mutableMapOf<String, CompletableDeferred<Unit>>()

        override suspend fun play(text: String) {
            started += text
            error?.let { throw it }
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
    }

    private suspend fun settle() = repeat(5) { yield() }

    private fun CoroutineScope.output(player: FakePlayer, enabled: Boolean = true, errors: MutableList<Exception> = mutableListOf()) =
        SpeechOutputImpl(player, { enabled }, this, onError = { errors += it })

    @Test
    fun newPhraseCancelsThePreviousOne() = runBlocking {
        val player = FakePlayer()
        val speech = output(player)

        speech.speak("Uno", SpeechPriority.NORMAL)
        settle()
        speech.speak("Dos", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Uno", "Dos"), player.started)
        assertEquals(listOf("Uno"), player.cancelled)
        speech.stop()
    }

    @Test
    fun highPriorityCancelsNormal() = runBlocking {
        val player = FakePlayer()
        val speech = output(player)

        speech.speak("Señal recuperada.", SpeechPriority.NORMAL)
        settle()
        speech.speak("Gira a la izquierda.", SpeechPriority.HIGH)
        settle()

        assertEquals(listOf("Señal recuperada."), player.cancelled)
        assertEquals(listOf("Señal recuperada.", "Gira a la izquierda."), player.started)
        speech.stop()
    }

    @Test
    fun normalDoesNotInterruptHighAndPlaysAfterItEnds() = runBlocking {
        val player = FakePlayer()
        val speech = output(player)

        speech.speak("Llegaste a tu destino.", SpeechPriority.HIGH)
        settle()
        speech.speak("Perdí la señal de GPS.", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Llegaste a tu destino."), player.started)
        assertEquals(emptyList<String>(), player.cancelled)

        player.finish("Llegaste a tu destino.")
        settle()
        speech.speak("Señal recuperada.", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Llegaste a tu destino."), player.finished)
        assertEquals(listOf("Llegaste a tu destino.", "Señal recuperada."), player.started)
        speech.stop()
    }

    @Test
    fun highReplacesHigh() = runBlocking {
        val player = FakePlayer()
        val speech = output(player)

        speech.speak("En treinta metros, gire a la izquierda.", SpeechPriority.HIGH)
        settle()
        speech.speak("Gira a la izquierda.", SpeechPriority.HIGH)
        settle()

        assertEquals(listOf("En treinta metros, gire a la izquierda."), player.cancelled)
        assertEquals("Gira a la izquierda.", player.started.last())
        speech.stop()
    }

    @Test
    fun stopSilencesCurrentPhrase() = runBlocking {
        val player = FakePlayer()
        val speech = output(player)

        speech.speak("Iniciando ruta.", SpeechPriority.HIGH)
        settle()
        speech.stop()
        settle()
        speech.speak("Perdí la señal de GPS.", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("Iniciando ruta."), player.cancelled)
        assertEquals("Perdí la señal de GPS.", player.started.last())
        speech.stop()
    }

    @Test
    fun disabledVoiceSaysNothing() = runBlocking {
        val player = FakePlayer()
        val speech = output(player, enabled = false)

        speech.speak("Gira a la derecha.", SpeechPriority.HIGH)
        settle()

        assertEquals(emptyList<String>(), player.started)
    }

    @Test
    fun playerErrorIsReportedAndNextPhraseStillPlays() = runBlocking {
        val player = FakePlayer().apply { error = IllegalStateException("sin audio") }
        val errors = mutableListOf<Exception>()
        val speech = output(player, errors = errors)

        speech.speak("Uno", SpeechPriority.HIGH)
        settle()
        player.error = null
        speech.speak("Dos", SpeechPriority.NORMAL)
        settle()

        assertEquals(listOf("sin audio"), errors.map { it.message })
        assertEquals(listOf("Uno", "Dos"), player.started)
        speech.stop()
    }
}
