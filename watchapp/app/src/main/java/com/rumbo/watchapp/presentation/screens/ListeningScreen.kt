package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.ProgressRing
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.VoiceBars
import com.rumbo.watchapp.presentation.theme.Primary

/**
 * Escuchando (diseño A2): anillo y badge en el azul primario, nueve barras que suben y bajan con
 * la voz real ([level], 0 a 1), "Te escucho" (34 sp si entra en una línea), "Di a dónde quieres
 * ir" y "Cancelar", que deja de grabar sin enviar. TalkBack anuncia "Escuchando" (título de la
 * pantalla y badge); las barras y el anillo son decorativos.
 */
@Composable
fun ListeningScreen(level: Float, onCancel: () -> Unit) {
    RumboScreen(
        paneTitle = "Escuchando",
        edgeRing = { ProgressRing(progress = 1f, color = Primary, contentDescription = null) },
    ) {
        StateBadge("Escuchando", Primary, announce = true, filled = true)
        VoiceBars(level = level, color = Primary)
        FittedTitle("Te escucho")
        Text(
            text = "Di a dónde quieres ir",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PillButton("Cancelar", onClick = onCancel)
    }
}

// Tamaño del diseño A2 y lo menos que se achica para entrar en una línea.
private const val TITLE_SP = 34
private const val TITLE_MIN_SP = 24

/**
 * Título en Poppins 700 a 34 sp (diseño A2) si entra en una línea; si no, el tamaño más grande
 * que entre. En la pantalla redonda de 454 px "Te escucho" a 34 sp mide más que el ancho
 * disponible: partido en dos líneas quedaba contra el borde de abajo, atenuado.
 */
@Composable
private fun FittedTitle(text: String) {
    BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val base = MaterialTheme.typography.displayMedium
        val style = remember(text, constraints.maxWidth, base) {
            (TITLE_SP downTo TITLE_MIN_SP)
                .map { size -> base.copy(fontSize = size.sp, lineHeight = (size * 1.25f).sp) }
                .firstOrNull { measurer.measure(text, it, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth }
                ?: base.copy(fontSize = TITLE_MIN_SP.sp, lineHeight = (TITLE_MIN_SP * 1.25f).sp)
        }
        Text(
            text = text,
            modifier = Modifier.semantics { heading() },
            style = style,
            textAlign = TextAlign.Center,
        )
    }
}
