package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.viewmodel.Permission

/**
 * Explica un permiso negado y lleva a los ajustes de la app para darlo. Con [onContinueWithout]
 * (notificaciones) ofrece seguir sin él, porque la navegación funciona igual.
 */
@Composable
fun PermissionScreen(
    permission: Permission,
    onOpenSettings: () -> Unit,
    onHome: () -> Unit,
    onContinueWithout: (() -> Unit)? = null,
) {
    RumboScreen(paneTitle = "Permiso necesario") {
        // Ícono más chico que en Error para que "Abrir ajustes" asome sin desplazar.
        StateIcon(iconOf(permission), StatusTurnSoon, size = 56.dp)
        Text(
            text = permission.explanation,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        PillButton("Abrir ajustes", onClick = onOpenSettings, style = PillStyle.PRIMARY)
        if (onContinueWithout != null) PillButton("Ir sin notificación", onClick = onContinueWithout)
        PillButton("Inicio", onClick = onHome)
        Text(
            text = "En Ajustes, toca Permisos y actívalo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun iconOf(permission: Permission) = when (permission) {
    Permission.MICROPHONE -> StateIconKind.MIC
    Permission.LOCATION -> StateIconKind.NO_SIGNAL
    Permission.NOTIFICATIONS -> StateIconKind.WARNING
}
