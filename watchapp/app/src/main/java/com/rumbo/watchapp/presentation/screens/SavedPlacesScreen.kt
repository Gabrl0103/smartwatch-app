package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.rumbo.watchapp.domain.model.SavedPlace

/** Lista de lugares guardados: tocar uno pide la ruta; mantenerlo pulsado ofrece borrarlo. */
@Composable
fun SavedPlacesScreen(
    places: List<SavedPlace>,
    onChoose: (SavedPlace) -> Unit,
    onDelete: (SavedPlace) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("Lugares guardados", textAlign = TextAlign.Center)
                }
            }
            item {
                Text(
                    text = if (places.isEmpty()) {
                        "Aún no tienes lugares guardados"
                    } else {
                        "Mantén pulsado un lugar para borrarlo"
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            items(places, key = { it.id }) { place ->
                Button(
                    onClick = { onChoose(place) },
                    onLongClick = { onDelete(place) },
                    onLongClickLabel = "Borrar ${place.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(place.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                FilledTonalButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text("Volver", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
