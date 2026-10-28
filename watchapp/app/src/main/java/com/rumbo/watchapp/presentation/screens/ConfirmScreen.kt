package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.format.formatWalkingMinutes
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.viewmodel.SaveStatus

@Composable
fun ConfirmScreen(
    route: Route,
    save: SaveStatus,
    gpsAvailable: Boolean,
    onGo: () -> Unit,
    onChange: () -> Unit,
    onSave: () -> Unit,
) {
    CenteredScreen {
        if (!gpsAvailable) NoGpsLabel()
        Text(
            text = route.destinationName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatDistance(route.distanceM),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatWalkingMinutes(route.durationS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onChange, modifier = Modifier.weight(1f)) {
                Text("Cambiar", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Button(onClick = onGo, modifier = Modifier.weight(1f)) {
                Text("Ir", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        // Secundario: no compite con "Ir". Siempre activo para que el texto mantenga contraste.
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(saveLabel(save), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        if (save == SaveStatus.FAILED) {
            Text(
                text = "No pude guardar el lugar",
                style = MaterialTheme.typography.labelMedium,
                color = StatusOffRoute,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun saveLabel(save: SaveStatus) = when (save) {
    SaveStatus.IDLE, SaveStatus.FAILED -> "Guardar"
    SaveStatus.SAVING -> "Guardando..."
    SaveStatus.SAVED -> "Guardado"
}
