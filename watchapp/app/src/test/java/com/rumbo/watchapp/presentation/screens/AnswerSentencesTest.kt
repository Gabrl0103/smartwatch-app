package com.rumbo.watchapp.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerSentencesTest {

    @Test
    fun eachSentenceGoesInItsOwnBubble() {
        assertEquals(
            listOf("Faltan unos 470 metros.", "En 30 metros gira a la izquierda hacia la Carrera 14."),
            answerSentences("Faltan unos 470 metros. En 30 metros gira a la izquierda hacia la Carrera 14."),
        )
        assertEquals(listOf("¿Seguro?", "¡Sí!", "Listo…", "Vamos"), answerSentences(" ¿Seguro? ¡Sí! Listo… Vamos "))
    }

    @Test
    fun numbersWithDecimalsAndASingleSentenceStayWhole() {
        assertEquals(listOf("Faltan 1,2 km o 1.200 m"), answerSentences("Faltan 1,2 km o 1.200 m"))
        assertEquals(listOf("Sí"), answerSentences("Sí"))
    }
}
