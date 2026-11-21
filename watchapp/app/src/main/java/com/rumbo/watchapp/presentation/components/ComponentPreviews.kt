package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.ui.tooling.preview.WearPreviewLargeRound
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.presentation.theme.Accent
import com.rumbo.watchapp.presentation.theme.Background
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.theme.WatchappTheme

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    WatchappTheme {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@WearPreviewLargeRound
@Composable
private fun StateIconsPreview() = PreviewSurface {
    FlowRow(
        modifier = Modifier.padding(36.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        StateIconKind.entries.forEach { StateIcon(it, Accent, size = 44.dp) }
    }
}

@WearPreviewLargeRound
@Composable
private fun StatusPillsPreview() = PreviewSurface {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusPill("En ruta", StatusOnRoute, icon = StateIconKind.STRAIGHT)
        StatusPill("Giro próximo", StatusTurnSoon, icon = StateIconKind.TURN_SOON)
        StatusPill("Fuera de ruta", StatusOffRoute, icon = StateIconKind.WARNING)
        StatusPill("Sin señal GPS", StatusNoSignal, icon = StateIconKind.NO_SIGNAL)
        StatusPill("Llegaste", StatusArrived, icon = StateIconKind.ARRIVED)
    }
}

@WearPreviewLargeRound
@Composable
private fun ButtonsPreview() = PreviewSurface {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PillButton("Ir", onClick = {}, style = PillStyle.PRIMARY)
        PillButton("Cambiar", onClick = {})
        PillSwitch(checked = true, onCheckedChange = {}, label = "Voz: sí", secondaryLabel = "Del reloj")
    }
}

@WearPreviewLargeRound
@Composable
private fun DialPreview() = PreviewSurface {
    Dial(StatusTurnSoon, RingStyle.PROGRESS, progress = 0.6f, contentDescription = "Progreso hacia el giro")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassDisc(56.dp, halo = StatusTurnSoon) { StateIcon(StateIconKind.RIGHT, StatusTurnSoon, size = 32.dp) }
        VibrationPill(VibrationPatterns.of(VibrationCue.RIGHT, supportsAmplitude = true), StatusTurnSoon)
        RoundAction(StateIconKind.REPEAT, "Repetir", onClick = {}, style = RoundActionStyle.PRIMARY)
    }
}
