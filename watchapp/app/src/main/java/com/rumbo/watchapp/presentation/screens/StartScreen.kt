package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.R

@Composable
fun StartScreen(onSpeak: () -> Unit, gpsAvailable: Boolean) {
    CenteredScreen {
        Text(
            text = "¿A dónde vamos?",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        FilledIconButton(onClick = onSpeak, modifier = Modifier.size(72.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Hablar destino",
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = "Toca y habla",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!gpsAvailable) NoGpsLabel()
    }
}
