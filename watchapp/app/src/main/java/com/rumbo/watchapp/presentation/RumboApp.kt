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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.application.usecase.mapUpdates
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.presentation.screens.AssistantScreen
import com.rumbo.watchapp.presentation.screens.BackGesture
import com.rumbo.watchapp.presentation.screens.ConfirmDeleteScreen
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.ErrorScreen
import com.rumbo.watchapp.presentation.screens.ListeningScreen
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.PermissionScreen
import com.rumbo.watchapp.presentation.screens.ProcessingScreen
import com.rumbo.watchapp.presentation.screens.RouteMapScreen
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
    val vibrationSettings by viewModel.vibrationSettings.collectAsState()
    val vibrationPatterns by viewModel.vibrationPatterns.collectAsState()
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
                    vibrationSettings = vibrationSettings,
                    vibrationPatterns = vibrationPatterns,
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
    vibrationSettings: VibrationSettings,
    vibrationPatterns: Map<VibrationCue, VibrationPattern>,
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
        PlanState.Listening -> {
            val level by viewModel.voiceLevel.collectAsState()
            ListeningScreen(
                level = level,
                onCancel = viewModel::cancelListening,
                // En ruta no se pide otro destino: el micrófono es para preguntar, guardar, repetir o terminar.
                hint = when {
                    viewModel.navigating -> "Pregunta o da una orden"
                    viewModel.confirming -> "Di guardar, repetir o cancelar"
                    else -> "Di a dónde quieres ir"
                },
            )
        }
        PlanState.Processing -> ProcessingScreen(
            waitMessage ?: if (viewModel.navigating || viewModel.confirming) "Entendiendo..." else "Buscando ruta...",
        )
        PlanState.LoadingPlaces -> ProcessingScreen(waitMessage ?: "Cargando lugares...")
        PlanState.Answering -> ProcessingScreen(waitMessage ?: "Pensando la respuesta...")
        is PlanState.Assistant -> AssistantScreen(
            question = state.question,
            answer = state.answer,
            speaking = viewModel.speaking.collectAsState().value,
            onAskAgain = onSpeak,
            onBack = viewModel::reset,
            backLabel = homeLabel(viewModel),
        )
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
            speaking = viewModel.speaking.collectAsState().value,
            route = state.route,
            save = state.save,
            gpsAvailable = gpsAvailable,
            onGo = onGo,
            onChange = viewModel::reset,
            onSave = viewModel::saveDestination,
            onSpeak = onSpeak,
        )
        is PlanState.Vibrations -> VibrationsScreen(
            patterns = vibrationPatterns,
            supportsAmplitude = viewModel.supportsAmplitude,
            settings = vibrationSettings,
            demo = state.demo,
            onPlay = viewModel::playVibration,
            onChangeIntensity = viewModel::changeVibrationIntensity,
            onChangeDuration = viewModel::changeVibrationDuration,
            onStartDemo = viewModel::startVibrationDemo,
            onStopDemo = viewModel::stopVibrationDemo,
            onBack = viewModel::reset,
        )
        is PlanState.OnRoute -> NavigationScreen(
            state = navigation,
            confirmFinish = state.confirmFinish,
            onRepeat = viewModel::repeatInstruction,
            onFinish = viewModel::finish,
            onKeepNavigating = viewModel::keepNavigating,
            onAsk = onSpeak,
            onMap = viewModel::openMap,
        )
        // Solo se recoge aquí: al salir del mapa (o con la app detrás) deja de redibujar.
        PlanState.RouteMap -> {
            val updates = remember(viewModel) { viewModel.navigationState.mapUpdates() }
            val map by updates.collectAsStateWithLifecycle(navigation)
            RouteMapScreen(state = map, onBack = viewModel::closeMap)
        }
        is PlanState.Error -> ErrorScreen(
            message = state.message,
            onRetry = when (state.retry) {
                RetryAction.LISTEN -> onSpeak
                RetryAction.SAVED_PLACES -> viewModel::openSavedPlaces
            },
            onHome = viewModel::reset,
            homeLabel = homeLabel(viewModel),
        )
        is PlanState.PermissionNeeded -> PermissionScreen(
            permission = state.permission,
            onOpenSettings = onOpenSettings,
            onHome = viewModel::reset,
            onContinueWithout = if (state.route != null) viewModel::goWithoutNotification else null,
        )
    }
}

// Con una ruta en curso (se fue a preguntar algo), "Inicio" vuelve a ella; lo mismo con Confirmar.
private fun homeLabel(viewModel: RoutePlanningViewModel) = when {
    viewModel.navigating -> "Volver a la ruta"
    viewModel.confirming -> "Volver a Confirmar"
    else -> "Inicio"
}
