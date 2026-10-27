package com.rumbo.watchapp.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.ErrorScreen
import com.rumbo.watchapp.presentation.screens.ListeningScreen
import com.rumbo.watchapp.presentation.screens.OnRouteScreen
import com.rumbo.watchapp.presentation.screens.ProcessingScreen
import com.rumbo.watchapp.presentation.screens.StartScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.PlanState
import com.rumbo.watchapp.presentation.viewmodel.RoutePlanningViewModel

/** Navegación simple: cada estado del ViewModel es una pantalla. */
@Composable
fun RumboApp(viewModel: RoutePlanningViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.listen()
        } else {
            viewModel.showError("Necesito permiso del micrófono para escucharte")
        }
    }
    val onSpeak = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.listen() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    WatchappTheme {
        AppScaffold {
            when (val current = state) {
                PlanState.Start -> StartScreen(onSpeak = onSpeak)
                PlanState.Listening -> ListeningScreen()
                PlanState.Processing -> ProcessingScreen()
                is PlanState.Confirming -> ConfirmScreen(
                    route = current.route,
                    onGo = viewModel::go,
                    onChange = viewModel::reset,
                )
                is PlanState.OnRoute -> OnRouteScreen(route = current.route, onFinish = viewModel::reset)
                is PlanState.Error -> ErrorScreen(message = current.message, onRetry = onSpeak)
            }
        }
    }
}
