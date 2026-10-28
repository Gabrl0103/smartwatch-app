package com.rumbo.watchapp.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import com.rumbo.watchapp.presentation.screens.ConfirmDeleteScreen
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.ErrorScreen
import com.rumbo.watchapp.presentation.screens.ListeningScreen
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.ProcessingScreen
import com.rumbo.watchapp.presentation.screens.SavedPlacesScreen
import com.rumbo.watchapp.presentation.screens.StartScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.PlanState
import com.rumbo.watchapp.presentation.viewmodel.RetryAction
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
    // Android 13+ pide permiso para mostrar la notificación "Rumbo: navegando". Se navega igual
    // si lo niega: el servicio sigue en primer plano, solo que sin notificación visible.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.go() }
    val onGo = {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
        if (needsNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.go()
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
                PlanState.Start -> StartScreen(
                    onSpeak = onSpeak,
                    onSavedPlaces = viewModel::openSavedPlaces,
                    gpsAvailable = gpsAvailable,
                )
                PlanState.Listening -> ListeningScreen()
                PlanState.Processing -> ProcessingScreen()
                PlanState.LoadingPlaces -> ProcessingScreen("Cargando lugares...")
                is PlanState.SavedPlaces -> SavedPlacesScreen(
                    places = current.places,
                    onChoose = viewModel::chooseSavedPlace,
                    onDelete = viewModel::askDelete,
                    onBack = viewModel::reset,
                )
                is PlanState.ConfirmDelete -> ConfirmDeleteScreen(
                    place = current.place,
                    onConfirm = viewModel::confirmDelete,
                    onCancel = viewModel::cancelDelete,
                )
                is PlanState.Confirming -> ConfirmScreen(
                    route = current.route,
                    save = current.save,
                    gpsAvailable = gpsAvailable,
                    onGo = onGo,
                    onChange = viewModel::reset,
                    onSave = viewModel::saveDestination,
                )
                is PlanState.OnRoute -> NavigationScreen(state = navigation, onFinish = viewModel::finish)
                is PlanState.Error -> ErrorScreen(
                    message = current.message,
                    onRetry = when (current.retry) {
                        RetryAction.LISTEN -> onSpeak
                        RetryAction.SAVED_PLACES -> viewModel::openSavedPlaces
                    },
                    onHome = viewModel::reset,
                )
            }
        }
    }
}
