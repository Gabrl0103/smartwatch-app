package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.presentation.components.PillButton

/** Lista de lugares guardados: tocar uno pide la ruta; mantenerlo pulsado ofrece borrarlo. */
@Composable
fun SavedPlacesScreen(
    places: List<SavedPlace>,
    onChoose: (SavedPlace) -> Unit,
    onDelete: (SavedPlace) -> Unit,
    onBack: () -> Unit,
) {
    RumboScreen(paneTitle = "Lugares guardados") {
        Text(
            text = "Lugares guardados",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (places.isEmpty()) {
                "Aún no tienes lugares guardados"
            } else {
                "Mantén pulsado un lugar para borrarlo"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        places.forEach { place ->
            PillButton(
                label = place.name,
                onClick = { onChoose(place) },
                onLongClick = { onDelete(place) },
                onLongClickLabel = "Borrar ${place.name}",
            )
        }
        PillButton("Volver", onClick = onBack)
    }
}
