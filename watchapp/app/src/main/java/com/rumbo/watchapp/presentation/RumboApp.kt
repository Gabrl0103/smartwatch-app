package com.rumbo.watchapp.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.presentation.screens.BackGesture
import com.rumbo.watchapp.presentation.screens.ConfirmDeleteScreen
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.ErrorScreen
import com.rumbo.watchapp.presentation.screens.ListeningScreen
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.PermissionScreen
import com.rumbo.watchapp.presentation.screens.ProcessingScreen
import com.rumbo.watchapp.presentation.screens.SavedPlacesScreen
import com.rumbo.watchapp.presentation.screens.StartScreen
import com.rumbo.watchapp.presentation.screens.VibrationsScreen
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.Permission
import com.rumbo.watchapp.presentation.viewmodel.PlanState
import com.rumbo.watchapp.presentation.viewmodel.RetryAction
import com.rumbo.watchapp.presentation.viewmodel.RoutePlanningViewModel

/**
 * Navegación simple: cada estado del ViewModel es una pantalla. El gesto de volver lo resuelve
 * el ViewModel ([RoutePlanningViewModel.back]); en Inicio no se intercepta y sale de la app.
 * Cada permiso se pide justo antes de la acción que lo necesita: micrófono y ubicación al tocar
 * el micrófono, ubicación al elegir un lugar guardado y notificaciones al tocar "Ir".
 */
@Composable
fun RumboApp(viewModel: RoutePlanningViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val navigation by viewModel.navigationState.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val speechStatus by viewModel.speechStatus.collectAsState()
    val waitMessage by viewModel.waitMessage.collectAsState()
    val previousRoute by viewModel.previousRoute.collectAsState()
    var gpsAvailable by remember { mutableStateOf(context.isGranted(Permission.LOCATION)) }
    val refreshGps = {
        gpsAvailable = context.isGranted(Permission.LOCATION)
        if (gpsAvailable) viewModel.startLocation()
    }

    LaunchedEffect(Unit) { refreshGps() }

    // Al volver de Ajustes: si ya dio el permiso que faltaba, se sigue donde iba.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshGps()
        val current = viewModel.state.value
        if (current is PlanState.PermissionNeeded && context.isGranted(current.permission)) {
            viewModel.permissionGranted()
        }
    }

    val requestPermissions = rememberPermissionRequest(
        onDenied = viewModel::permissionDenied,
        onResult = refreshGps,
    )
    val onSpeak = {
        requestPermissions(listOf(Permission.MICROPHONE, Permission.LOCATION)) { viewModel.listen() }
    }
    val onChooseSavedPlace = { place: SavedPlace ->
        requestPermissions(listOf(Permission.LOCATION)) { viewModel.chooseSavedPlace(place) }
    }
    // Android 13+ pide permiso para mostrar la notificación "Rumbo: navegando".
    val onGo = { requestPermissions(listOf(Permission.NOTIFICATIONS)) { viewModel.go() } }
    val onContinue = {
        requestPermissions(listOf(Permission.LOCATION, Permission.NOTIFICATIONS)) { viewModel.resumePreviousRoute() }
    }
    val onOpenSettings = {
        if (!context.openAppSettings()) viewModel.showError("No pude abrir los ajustes del reloj")
    }
    // Abre el instalador de voces del motor; si no hay a dónde ir, avisa en pantalla y por voz.
    val onInstallVoice = {
        try {
            context.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            viewModel.showError("Falta la voz en español del reloj")
        }
    }

    WatchappTheme {
        AppScaffold {
            BackGesture(onBack = if (state == PlanState.Start) null else viewModel::back) {
                Screen(
                    state = state,
                    viewModel = viewModel,
                    navigation = navigation,
                    voiceEnabled = voiceEnabled,
                    speechStatus = speechStatus,
                    gpsAvailable = gpsAvailable,
                    waitMessage = waitMessage,
                    previousDestination = previousRoute?.destinationName,
                    onSpeak = onSpeak,
                    onContinue = onContinue,
                    onChooseSavedPlace = onChooseSavedPlace,
                    onGo = onGo,
                    onInstallVoice = onInstallVoice,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun Screen(
    state: PlanState,
    viewModel: RoutePlanningViewModel,
    navigation: TrackingState,
    voiceEnabled: Boolean,
    speechStatus: SpeechStatus,
    gpsAvailable: Boolean,
    waitMessage: String?,
    previousDestination: String?,
    onSpeak: () -> Unit,
    onContinue: () -> Unit,
    onChooseSavedPlace: (SavedPlace) -> Unit,
    onGo: () -> Unit,
    onInstallVoice: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        PlanState.Start -> StartScreen(
            onSpeak = onSpeak,
            onSavedPlaces = viewModel::openSavedPlaces,
            onVibrations = viewModel::openVibrations,
            voiceEnabled = voiceEnabled,
            onVoiceChange = viewModel::setVoiceEnabled,
            speechStatus = speechStatus,
            onInstallVoice = onInstallVoice,
            gpsAvailable = gpsAvailable,
            previousDestination = previousDestination,
            onContinue = onContinue,
        )
        PlanState.Listening -> ListeningScreen()
        PlanState.Processing -> ProcessingScreen(waitMessage ?: "Buscando ruta...")
        PlanState.LoadingPlaces -> ProcessingScreen(waitMessage ?: "Cargando lugares...")
        is PlanState.SavedPlaces -> SavedPlacesScreen(
            places = state.places,
            onChoose = onChooseSavedPlace,
            onDelete = viewModel::askDelete,
            onBack = viewModel::reset,
        )
        is PlanState.ConfirmDelete -> ConfirmDeleteScreen(
            place = state.place,
            onConfirm = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete,
        )
        is PlanState.Confirming -> ConfirmScreen(
            route = state.route,
            save = state.save,
            gpsAvailable = gpsAvailable,
            onGo = onGo,
            onChange = viewModel::reset,
            onSave = viewModel::saveDestination,
        )
        PlanState.Vibrations -> VibrationsScreen(
            patterns = viewModel.vibrationPatterns,
            onPlay = viewModel::playVibration,
            onBack = viewModel::reset,
        )
        is PlanState.OnRoute -> NavigationScreen(
            state = navigation,
            confirmFinish = state.confirmFinish,
            onRepeat = viewModel::repeatInstruction,
            onFinish = viewModel::finish,
            onKeepNavigating = viewModel::keepNavigating,
        )
        is PlanState.Error -> ErrorScreen(
            message = state.message,
            onRetry = when (state.retry) {
                RetryAction.LISTEN -> onSpeak
                RetryAction.SAVED_PLACES -> viewModel::openSavedPlaces
            },
            onHome = viewModel::reset,
        )
        is PlanState.PermissionNeeded -> PermissionScreen(
            permission = state.permission,
            onOpenSettings = onOpenSettings,
            onHome = viewModel::reset,
            onContinueWithout = if (state.route != null) viewModel::goWithoutNotification else null,
        )
    }
}
