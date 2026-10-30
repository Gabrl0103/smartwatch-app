package com.rumbo.watchapp.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.SpeakingBars
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.theme.AssistantChip

/**
 * Asistente: lo que preguntó la persona, la respuesta escrita (también se dice en voz alta, y
 * TalkBack la anuncia), "Preguntar otra vez" y [backLabel] ("Volver a la ruta" o "Inicio").
 * Mientras la respuesta suena, el badge se funde en barras como en Confirmar.
 */
@Composable
fun AssistantScreen(
    question: String,
    answer: String,
    speaking: Boolean,
    onAskAgain: () -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    RumboScreen(paneTitle = "Asistente") {
        val talking by animateFloatAsState(if (speaking) 1f else 0f, label = "habla")
        Box(contentAlignment = Alignment.Center) {
            StateBadge("Asistente", AssistantChip, filled = true, modifier = Modifier.graphicsLayer { alpha = 1f - talking })
            if (talking > 0f) SpeakingBars(AssistantChip, modifier = Modifier.graphicsLayer { alpha = talking })
        }
        Text(
            text = "«$question»",
            modifier = Modifier.semantics { contentDescription = "Preguntaste: $question" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = answer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        PillButton("Preguntar otra vez", onClick = onAskAgain, style = PillStyle.ASSISTANT)
        PillButton(backLabel, onClick = onBack)
    }
}
