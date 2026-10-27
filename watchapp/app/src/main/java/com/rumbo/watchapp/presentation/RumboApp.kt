package com.rumbo.watchapp.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.ErrorScreen
import com.rumbo.watchapp.presentation.screens.ListeningScreen
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.ProcessingScreen
import com.rumbo.watchapp.presentation.screens.StartScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.PlanState
import com.rumbo.watchapp.presentation.viewmodel.RoutePlanningViewModel

// Android 12+ ignora la ubicación precisa si no se pide junto con la aproximada.
private val PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Navegación simple: cada estado del ViewModel es una pantalla. */
@Composable
fun RumboApp(viewModel: RoutePlanningViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val navigation by viewModel.navigationState.collectAsState()
    var gpsAvailable by remember {
        mutableStateOf(context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    LaunchedEffect(Unit) {
        if (gpsAvailable) viewModel.startLocation()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        gpsAvailable = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (gpsAvailable) viewModel.startLocation()
        if (context.isGranted(Manifest.permission.RECORD_AUDIO)) {
            viewModel.listen()
        } else {
            viewModel.showError("Necesito permiso del micrófono para escucharte")
        }
    }
    val onSpeak = {
        val missing = PERMISSIONS.filterNot { context.isGranted(it) }
        if (missing.isEmpty()) {
            viewModel.startLocation()
            viewModel.listen()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    WatchappTheme {
        AppScaffold {
            when (val current = state) {
                PlanState.Start -> StartScreen(onSpeak = onSpeak, gpsAvailable = gpsAvailable)
                PlanState.Listening -> ListeningScreen()
                PlanState.Processing -> ProcessingScreen()
                is PlanState.Confirming -> ConfirmScreen(
                    route = current.route,
                    gpsAvailable = gpsAvailable,
                    onGo = viewModel::go,
                    onChange = viewModel::reset,
                )
                is PlanState.OnRoute -> NavigationScreen(state = navigation, onFinish = viewModel::finish)
                is PlanState.Error -> ErrorScreen(message = current.message, onRetry = onSpeak)
            }
        }
    }
}
