package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.presentation.format.formatDistance

/** Provisional: muestra solo el primer paso hasta que exista la navegación con GPS. */
@Composable
fun OnRouteScreen(route: Route, onFinish: () -> Unit) {
    val firstStep = route.steps.firstOrNull()
    CenteredScreen {
        Text(
            text = "En ruta",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = firstStep?.instruction ?: "Camina hacia ${route.destinationName}",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (firstStep != null) {
            Text(
                text = formatDistance(firstStep.distanceM),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        FilledTonalButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Terminar", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
