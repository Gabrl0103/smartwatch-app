package com.rumbo.watchapp.presentation.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// WCAG AA para texto normal.
private const val MIN_TEXT_CONTRAST = 4.5

/** Luminancia relativa de WCAG 2.x (sRGB). */
private fun Color.relativeLuminance(): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

private fun contrast(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** Que todo texto de la app se lea: contraste de al menos 4.5:1 con lo que tiene detrás. */
class ColorContrastTest {

    private val filled = mapOf(
        "primario" to Primary,
        "acento" to Accent,
        "en ruta" to StatusOnRoute,
        "giro próximo" to StatusTurnSoon,
        "desviado" to StatusOffRoute,
        "sin señal" to StatusNoSignal,
        "llegada" to StatusArrived,
        "chip del asistente" to AssistantChip,
    )

    @Test
    fun darkTextOnEveryColoredButtonIsReadable() {
        filled.forEach { (name, color) ->
            val ratio = contrast(OnColor, color)
            assertTrue("Texto oscuro sobre $name: $ratio", ratio >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun coloredTextOnBackgroundAndSurfaceIsReadable() {
        // Badges y textos de estado van en su color sobre el fondo o la superficie.
        (filled + mapOf("texto" to TextPrimary, "texto secundario" to TextSecondary)).forEach { (name, color) ->
            listOf(Background, Surface).forEach { behind ->
                val ratio = contrast(color, behind)
                assertTrue("$name sobre ${if (behind == Background) "fondo" else "superficie"}: $ratio", ratio >= MIN_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun whiteOnPrimaryWouldNotBeEnoughAndAssistantPurpleNeedsTheChipTone() {
        assertEquals(2.9, contrast(TextPrimary, Primary), 0.05)
        assertTrue(contrast(OnColor, Assistant) < MIN_TEXT_CONTRAST)
        assertTrue(contrast(OnColor, AssistantChip) >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun tokensMatchTheDesign() {
        assertEquals(Color(0xFF0B0E14), Background)
        assertEquals(Color(0xFF151922), Surface)
        assertEquals(Color(0xFFF5F7FA), TextPrimary)
        assertEquals(Color(0xFFA0A8B8), TextSecondary)
        assertEquals(Color(0xFF4F8EFF), Primary)
        assertEquals(Color(0xFFFFC857), Accent)
        assertEquals(Color(0xFF2A2F3A), Border)
        assertEquals(Color(0xFFFF8A3D), StatusOffRoute)
        assertEquals(Color(0xFF8A93A6), StatusNoSignal)
        assertEquals(Color(0xFF3DDC84), StatusArrived)
        assertEquals(Color(0xFF7C5CFC), Assistant)
        assertEquals(Color(0xFF9B82FF), AssistantChip)
    }
}
