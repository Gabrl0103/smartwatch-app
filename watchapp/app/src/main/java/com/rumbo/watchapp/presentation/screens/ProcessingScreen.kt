package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.theme.Primary

@Composable
fun ProcessingScreen(message: String = "Buscando ruta...") {
    RumboScreen(paneTitle = "Procesando") {
        StateBadge("Procesando", Primary, announce = true)
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp).semantics { contentDescription = "Cargando" },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
