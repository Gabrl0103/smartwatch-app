package com.rumbo.watchapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandTest {

    private fun result(intent: String, destination: String? = null, question: String? = null, text: String = "") =
        VoiceResult(text, intent, destination, question)

    @Test
    fun navigateNeedsADestination() {
        assertEquals(VoiceCommand.Navigate("Parque Bolívar"), result("navigate", destination = "  Parque Bolívar ").toCommand())
        assertEquals(VoiceCommand.NotUnderstood, result("navigate", destination = "   ").toCommand())
        assertEquals(VoiceCommand.NotUnderstood, result("navigate").toCommand())
    }

    @Test
    fun askUsesTheQuestionOrWhatWasSaid() {
        assertEquals(VoiceCommand.Ask("¿Cuánto falta?"), result("ask", question = "¿Cuánto falta?").toCommand())
        assertEquals(VoiceCommand.Ask("qué calle es esta"), result("ask", text = " qué calle es esta ").toCommand())
        // El backend responde ask con texto vacío cuando no entendió nada.
        assertEquals(VoiceCommand.NotUnderstood, result("ask", question = "", text = "").toCommand())
    }

    @Test
    fun theCommandsOfTheBackend() {
        assertEquals(VoiceCommand.Repeat, result("repetir_instruccion").toCommand())
        assertEquals(VoiceCommand.Cancel, result("cancelar_ruta").toCommand())
        assertEquals(VoiceCommand.SaveDestination, result("guardar_destino").toCommand())
        assertEquals(VoiceCommand.HowFar, result("cuanto_falta").toCommand())
        assertEquals(VoiceCommand.SavedStatus, result("estado_guardado").toCommand())
    }

    @Test
    fun theNamesOfAnOlderBackendStillWork() {
        assertEquals(VoiceCommand.Repeat, result("repeat").toCommand())
        assertEquals(VoiceCommand.Cancel, result("cancel").toCommand())
    }

    @Test
    fun anUnknownIntentIsNotUnderstood() {
        assertEquals(VoiceCommand.NotUnderstood, result("bailar").toCommand())
    }
}
