package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.theme.OnColor

/**
 * Píldora con el nombre del estado escrito en su color, sobre un tinte del mismo color. El
 * estado siempre va en el texto; el color solo lo refuerza. Con [filled] la píldora es del color
 * y el texto va oscuro (como "Escuchando"). Con [announce], TalkBack lo lee cuando cambia.
 */
@Composable
fun StateBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
    filled: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier
            .background(if (filled) color else color.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, color, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) OnColor else color,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
}
