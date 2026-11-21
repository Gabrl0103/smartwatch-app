package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.Dial
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.RingStyle
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.StatusPill
import com.rumbo.watchapp.presentation.components.VoiceBars
import com.rumbo.watchapp.presentation.theme.Primary

/**
 * Escuchando: anillo lleno y bisel encendidos en el azul primario, insignia "Escuchando", nueve
 * barras que suben y bajan con la voz real ([level], 0 a 1), "Te escucho", [hint] ("Di a dónde
 * quieres ir"; en ruta, "Pregunta o da una orden") y "Cancelar", que deja de grabar sin enviar.
 * TalkBack anuncia "Escuchando" (título de la pantalla e insignia); barras y dial son decorativos.
 */
@Composable
fun ListeningScreen(level: Float, onCancel: () -> Unit, hint: String = "Di a dónde quieres ir") {
    RumboScreen(
        paneTitle = "Escuchando",
        dial = { Dial(Primary, RingStyle.FULL, progress = 1f) },
    ) {
        StatusPill("Escuchando", Primary, icon = StateIconKind.MIC, announce = true)
        VoiceBars(level = level, color = Primary, minHeight = 10.dp, maxHeight = 48.dp, barWidth = 7.dp, barGap = 5.dp)
        Text(
            text = "Te escucho",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 26.sp, lineHeight = 32.sp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PillButton("Cancelar", onClick = onCancel)
    }
}
