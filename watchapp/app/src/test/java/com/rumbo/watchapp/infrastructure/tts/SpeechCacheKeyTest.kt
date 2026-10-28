package com.rumbo.watchapp.infrastructure.tts

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCacheKeyTest {

    private fun oldName(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".wav"

    @Test
    fun fileNameCarriesTheVersion() {
        val name = speechCacheFileName("Gira a la izquierda.")

        assertTrue(name.startsWith("v2-"))
        assertTrue(name.endsWith(".wav"))
    }

    @Test
    fun samePhraseGivesSameFileAndOthersDiffer() {
        assertEquals(speechCacheFileName("Llegaste a tu destino."), speechCacheFileName("Llegaste a tu destino."))
        assertNotEquals(speechCacheFileName("Llegaste a tu destino."), speechCacheFileName("Señal recuperada."))
    }

    @Test
    fun audiosSavedBeforeTheVersionAreNotReusedAndGetDeleted() {
        val text = "Gira a la izquierda."

        assertNotEquals(oldName(text), speechCacheFileName(text))
        assertTrue(isStaleSpeechFile(oldName(text)))
        assertTrue(isStaleSpeechFile("${oldName(text)}.part"))
        assertFalse(isStaleSpeechFile(speechCacheFileName(text)))
    }
}
