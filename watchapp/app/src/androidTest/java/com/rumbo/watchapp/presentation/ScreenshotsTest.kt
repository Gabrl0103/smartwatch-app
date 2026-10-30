package com.rumbo.watchapp.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.domain.port.SpeechEngine
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.ProgressRing
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.screens.AssistantScreen
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
import com.rumbo.watchapp.presentation.theme.Accent
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.DemoStep
import com.rumbo.watchapp.presentation.viewmodel.GPS_SEARCHING_MESSAGE
import com.rumbo.watchapp.presentation.viewmodel.Permission
import com.rumbo.watchapp.presentation.viewmodel.SaveStatus
import java.io.File
import kotlin.math.min
import org.junit.Rule
import org.junit.Test

/** Una captura: [scrolls] desplaza la pantalla hacia abajo antes de tomarla. */
private class Shot(val name: String, val scrolls: Int = 0, val content: @Composable () -> Unit)

// La ruta real de la demo (docs/demo): Plaza de Nariño → Parque Bolívar, 1 791 m a 1,25 m/s.
private val ROUTE = Route(
    destinationName = "Parque Bolívar",
    distanceM = 1791.0,
    durationS = 1433.0,
    steps = listOf(RouteStep(1, Maneuver.ARRIVE, "Su destino está a la derecha.", 0.0, 1.206129, -77.27007)),
    polyline = listOf(Coordinates(1.214997, -77.278419), Coordinates(1.206129, -77.27007)),
)

private val PLACES = listOf(
    SavedPlace(1, "Casa", 1.2146, -77.2783),
    SavedPlace(2, "Parque Bolívar", 1.205584, -77.270279),
    SavedPlace(3, "Universidad", 1.2110, -77.2770),
)

// Las capturas muestran la versión con amplitud.
private fun pattern(cue: VibrationCue): VibrationPattern = VibrationPatterns.of(cue, supportsAmplitude = true)

private fun tracking(
    status: NavigationStatus,
    instruction: String = "Gire a la izquierda hacia Carrera 14.",
    maneuver: Maneuver = Maneuver.LEFT,
    toNext: Double = 30.0,
    pattern: VibrationPattern = pattern(VibrationCue.TURN_APPROACHING),
    recalculating: Boolean = false,
) = TrackingState(
    status = status,
    destinationName = "Parque Bolívar",
    nextInstruction = instruction,
    nextManeuver = maneuver,
    distanceToNextStepM = toNext,
    stepLengthM = 1304.0,
    remainingM = 466.0,
    pattern = pattern,
    recalculating = recalculating,
)

@Composable
private fun Navigation(state: TrackingState, confirmFinish: Boolean = false) =
    NavigationScreen(state, confirmFinish, onRepeat = {}, onFinish = {}, onKeepNavigating = {})

// Una respuesta como las de Gemini en ruta: dos frases cortas con el contexto de la ruta.
@Composable
private fun Assistant(speaking: Boolean = false) = AssistantScreen(
    question = "¿Cuánto falta para llegar?",
    answer = "Faltan unos 470 metros. En 30 metros gira a la izquierda hacia la Carrera 14.",
    speaking = speaking,
    onAskAgain = {},
    onBack = {},
    backLabel = "Volver a la ruta",
)

@Composable
private fun Vibrations(supportsAmplitude: Boolean = true, demo: DemoStep? = null) = VibrationsScreen(
    patterns = VibrationPatterns.forDevice(supportsAmplitude),
    supportsAmplitude = supportsAmplitude,
    settings = VibrationSettings(),
    demo = demo,
    onPlay = {},
    onChangeIntensity = {},
    onChangeDuration = {},
    onStartDemo = {},
    onStopDemo = {},
    onBack = {},
)

@Composable
private fun Start(previousDestination: String? = null) = StartScreen(
    onSpeak = {},
    onSavedPlaces = {},
    onVibrations = {},
    voiceEnabled = true,
    onVoiceChange = {},
    speechStatus = SpeechStatus(SpeechEngine.DEVICE),
    onInstallVoice = {},
    gpsAvailable = true,
    previousDestination = previousDestination,
)

/**
 * Genera las capturas de docs/capturas en el emulador: dibuja cada pantalla con datos de ejemplo,
 * la recorta en círculo si la pantalla es redonda y la guarda en
 * /sdcard/Android/data/com.rumbo.watchapp/files/capturas (ver docs/capturas/README.md).
 */
class ScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    private var current by mutableStateOf<Shot?>(null)

    private val shots = listOf(
        Shot("01_inicio") { Start() },
        Shot("02_inicio_abajo", scrolls = 3) { Start() },
        Shot("03_escuchando") { ListeningScreen(level = 0.75f, onCancel = {}) },
        Shot("04_procesando") { ProcessingScreen() },
        Shot("05_confirmar") {
            ConfirmScreen(ROUTE, SaveStatus.IDLE, gpsAvailable = true, onGo = {}, onChange = {}, onSave = {})
        },
        Shot("06_confirmar_abajo", scrolls = 3) {
            ConfirmScreen(ROUTE, SaveStatus.IDLE, gpsAvailable = true, onGo = {}, onChange = {}, onSave = {})
        },
        Shot("07_error") {
            ErrorScreen("No encontré ese lugar. Intenta con otro nombre", onRetry = {}, onHome = {})
        },
        Shot("08_en_ruta") {
            Navigation(
                tracking(
                    NavigationStatus.ON_ROUTE,
                    toNext = 85.0,
                    pattern = pattern(VibrationCue.ON_ROUTE),
                ),
            )
        },
        Shot("09_en_ruta_abajo", scrolls = 3) {
            Navigation(tracking(NavigationStatus.ON_ROUTE, toNext = 85.0, pattern = pattern(VibrationCue.ON_ROUTE)))
        },
        Shot("10_giro_proximo") { Navigation(tracking(NavigationStatus.TURN_APPROACHING)) },
        Shot("11_gira_ahora") {
            Navigation(
                tracking(
                    NavigationStatus.MANEUVER,
                    instruction = "Gire a la derecha hacia Avenida Bolívar.",
                    maneuver = Maneuver.RIGHT,
                    toNext = 8.0,
                    pattern = pattern(VibrationCue.RIGHT),
                ),
            )
        },
        Shot("12_fuera_de_ruta") {
            Navigation(tracking(NavigationStatus.OFF_ROUTE, pattern = pattern(VibrationCue.OFF_ROUTE), recalculating = true))
        },
        Shot("13_sin_senal") { Navigation(tracking(NavigationStatus.NO_SIGNAL, pattern = pattern(VibrationCue.NO_SIGNAL))) },
        Shot("14_llegaste") { Navigation(tracking(NavigationStatus.ARRIVED, pattern = pattern(VibrationCue.ARRIVED))) },
        Shot("15_terminar_ruta") { Navigation(tracking(NavigationStatus.ON_ROUTE), confirmFinish = true) },
        Shot("16_lugares_guardados") {
            SavedPlacesScreen(PLACES, onChoose = {}, onDelete = {}, onBack = {})
        },
        Shot("17_borrar_lugar") { ConfirmDeleteScreen(PLACES[0], onConfirm = {}, onCancel = {}) },
        Shot("18_vibraciones") { Vibrations() },
        Shot("19_componentes") { ComponentsGallery() },
        Shot("20_permiso_ubicacion") { PermissionScreen(Permission.LOCATION, onOpenSettings = {}, onHome = {}) },
        Shot("21_buscando_gps") { ProcessingScreen(GPS_SEARCHING_MESSAGE) },
        Shot("22_continuar_ruta") { Start(previousDestination = "Parque Bolívar") },
        Shot("23_vibraciones_sin_intensidad") { Vibrations(supportsAmplitude = false) },
        // La indicación real más larga de la demo: baja a 16 sp para leerse entera en el círculo.
        Shot("25_indicacion_larga") {
            Navigation(tracking(NavigationStatus.TURN_APPROACHING, instruction = "Gire a la izquierda para permanecer en Carrera 14."))
        },
        Shot("26_escuchando_abajo", scrolls = 3) { ListeningScreen(level = 0.75f, onCancel = {}) },
        Shot("27_confirmar_hablando") {
            ConfirmScreen(ROUTE, SaveStatus.IDLE, gpsAvailable = true, onGo = {}, onChange = {}, onSave = {}, speaking = true)
        },
        Shot("24_demostracion_vibraciones") {
            Vibrations(demo = DemoStep(VibrationCue.LEFT, 3, VibrationCue.entries.size))
        },
        Shot("28_asistente") { Assistant() },
        Shot("29_asistente_hablando") { Assistant(speaking = true) },
        Shot("30_asistente_abajo", scrolls = 3) { Assistant() },
        Shot("31_pensando_respuesta") { ProcessingScreen("Pensando la respuesta...") },
        Shot("32_atencion_escaleras") {
            Navigation(
                tracking(
                    NavigationStatus.TURN_APPROACHING,
                    instruction = "Atención: tome las escaleras.",
                    maneuver = Maneuver.UNKNOWN,
                    pattern = pattern(VibrationCue.ATTENTION),
                ),
            )
        },
    )

    @Test
    fun captureEveryScreen() {
        compose.setContent {
            WatchappTheme {
                AppScaffold {
                    current?.let { shot -> key(shot.name) { shot.content() } }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "capturas").apply {
            deleteRecursively()
            mkdirs()
        }
        val round = context.resources.configuration.isScreenRound
        shots.forEach { shot ->
            current = shot
            compose.waitForIdle()
            repeat(shot.scrolls) {
                compose.onRoot().performTouchInput { swipeUp() }
                compose.waitForIdle()
            }
            // Deja terminar la animación del anillo y la vuelta de la hora tras desplazar.
            compose.mainClock.advanceTimeBy(1_000)
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "${shot.name}.png").outputStream().use {
                (if (round) bitmap.roundCrop() else bitmap).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}

// Deja transparente lo que queda fuera del círculo, como se ve en el reloj.
private fun Bitmap.roundCrop(): Bitmap {
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(width / 2f, height / 2f, min(width, height) / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return out
}

@Composable
private fun ComponentsGallery() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ProgressRing(progress = 0.6f, color = StatusTurnSoon, contentDescription = "Progreso")
        Column(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StateBadge("Giro próximo", StatusTurnSoon)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                StateIconKind.entries.forEach { StateIcon(it, Accent, size = 32.dp) }
            }
            VibrationIndicator(pattern(VibrationCue.ARRIVED), StatusArrived)
            PillButton("Ir", onClick = {}, style = PillStyle.PRIMARY)
        }
    }
}
