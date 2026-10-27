package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    CenteredScreen {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Reintentar", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
