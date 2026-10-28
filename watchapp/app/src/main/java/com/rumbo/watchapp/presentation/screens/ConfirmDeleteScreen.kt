package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle

@Composable
fun ConfirmDeleteScreen(place: SavedPlace, onConfirm: () -> Unit, onCancel: () -> Unit) {
    RumboScreen(paneTitle = "Borrar lugar") {
        Text(
            text = "¿Borrar «${place.name}»?",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        PillButton("Cancelar", onClick = onCancel)
        PillButton("Borrar", onClick = onConfirm, style = PillStyle.DANGER)
    }
}
