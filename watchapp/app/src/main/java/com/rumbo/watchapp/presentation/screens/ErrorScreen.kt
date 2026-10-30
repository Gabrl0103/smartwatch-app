package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.theme.StatusOffRoute

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onHome: () -> Unit, homeLabel: String = "Inicio") {
    RumboScreen(paneTitle = "Error") {
        StateIcon(StateIconKind.WARNING, StatusOffRoute)
        Text(
            text = message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        PillButton("Reintentar", onClick = onRetry, style = PillStyle.PRIMARY)
        PillButton(homeLabel, onClick = onHome)
    }
}
