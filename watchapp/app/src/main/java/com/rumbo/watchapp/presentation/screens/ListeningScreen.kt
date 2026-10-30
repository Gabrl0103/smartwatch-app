package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * la voz real ([level], 0 a 1), "Te escucho" (34 sp), [hint] ("Di a dónde quieres ir"; en ruta,
 * "¿Qué quieres saber?") y "Cancelar", que deja de grabar sin enviar. TalkBack anuncia "Escuchando" (título de la pantalla y badge); las
 * barras y el anillo son decorativos.
 */
@Composable
fun ListeningScreen(level: Float, onCancel: () -> Unit, hint: String = "Di a dónde quieres ir") {
    RumboScreen(
        paneTitle = "Escuchando",
        edgeRing = { ProgressRing(progress = 1f, color = Primary, contentDescription = null) },
    ) {
        StateBadge("Escuchando", Primary, announce = true, filled = true)
        VoiceBars(level = level, color = Primary)
        Text(
            text = "Te escucho",
            modifier = Modifier.semantics { heading() },
            // Poppins 700 a 34 sp, fijo como en el diseño A2: en la pantalla de 454 px va en dos líneas.
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp, lineHeight = 42.sp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PillButton("Cancelar", onClick = onCancel)
    }
}
