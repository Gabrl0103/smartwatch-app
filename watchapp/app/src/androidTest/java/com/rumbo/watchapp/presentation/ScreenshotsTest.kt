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
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.ProgressRing
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.screens.ConfirmDeleteScreen
import com.rumbo.watchapp.presentation.screens.ConfirmScreen
import com.rumbo.watchapp.presentation.screens.ErrorScreen
import com.rumbo.watchapp.presentation.screens.ListeningScreen
import com.rumbo.watchapp.presentation.screens.NavigationScreen
import com.rumbo.watchapp.presentation.screens.ProcessingScreen
import com.rumbo.watchapp.presentation.screens.SavedPlacesScreen
import com.rumbo.watchapp.presentation.screens.StartScreen
import com.rumbo.watchapp.presentation.screens.VibrationsScreen
import com.rumbo.watchapp.presentation.theme.Accent
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import com.rumbo.watchapp.presentation.viewmodel.SaveStatus
import java.io.File
import kotlin.math.min
import org.junit.Rule
import org.junit.Test

/** Una captura: [scrolls] desplaza la pantalla hacia abajo antes de tomarla. */
private class Shot(val name: String, val scrolls: Int = 0, val content: @Composable () -> Unit)

private val ROUTE = Route(
    destinationName = "Parque Bolívar",
    distanceM = 1970.0,
    durationS = 1500.0,
    steps = listOf(RouteStep(1, Maneuver.ARRIVE, "Ha llegado.", 0.0, 1.2056, -77.2703)),
    polyline = listOf(Coordinates(1.2136, -77.2811), Coordinates(1.2056, -77.2703)),
)

private val PLACES = listOf(
    SavedPlace(1, "Casa", 1.2146, -77.2783),
    SavedPlace(2, "Parque Bolívar", 1.2056, -77.2703),
    SavedPlace(3, "Universidad", 1.2110, -77.2770),
)

private fun tracking(
    status: NavigationStatus,
    instruction: String = "Gira a la izquierda hacia la Calle 18",
    maneuver: Maneuver = Maneuver.LEFT,
    toNext: Double = 30.0,
    pattern: VibrationPattern = VibrationPatterns.TURN_APPROACHING,
    recalculating: Boolean = false,
) = TrackingState(
    status = status,
    destinationName = "Parque Bolívar",
    nextInstruction = instruction,
    nextManeuver = maneuver,
    distanceToNextStepM = toNext,
    stepLengthM = 120.0,
    remainingM = 1240.0,
    pattern = pattern,
    recalculating = recalculating,
)

@Composable
private fun Navigation(state: TrackingState, confirmFinish: Boolean = false) =
    NavigationScreen(state, confirmFinish, onRepeat = {}, onFinish = {}, onKeepNavigating = {})

@Composable
private fun Start() = StartScreen(
    onSpeak = {},
    onSavedPlaces = {},
    onVibrations = {},
    voiceEnabled = true,
    onVoiceChange = {},
    speechStatus = SpeechStatus(SpeechEngine.DEVICE),
    onInstallVoice = {},
    gpsAvailable = true,
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
        Shot("03_escuchando") { ListeningScreen() },
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
                    instruction = "Sigue recto por la Carrera 24",
                    maneuver = Maneuver.STRAIGHT,
                    toNext = 85.0,
                    pattern = VibrationPatterns.ON_ROUTE,
                ),
            )
        },
        Shot("09_en_ruta_abajo", scrolls = 3) {
            Navigation(tracking(NavigationStatus.ON_ROUTE, toNext = 85.0, pattern = VibrationPatterns.ON_ROUTE))
        },
        Shot("10_giro_proximo") { Navigation(tracking(NavigationStatus.TURN_APPROACHING)) },
        Shot("11_gira_ahora") {
            Navigation(
                tracking(
                    NavigationStatus.MANEUVER,
                    instruction = "Gira a la derecha",
                    maneuver = Maneuver.RIGHT,
                    toNext = 8.0,
                    pattern = VibrationPatterns.RIGHT,
                ),
            )
        },
        Shot("12_fuera_de_ruta") {
            Navigation(tracking(NavigationStatus.OFF_ROUTE, pattern = VibrationPatterns.OFF_ROUTE, recalculating = true))
        },
        Shot("13_sin_senal") { Navigation(tracking(NavigationStatus.NO_SIGNAL, pattern = VibrationPatterns.NO_SIGNAL)) },
        Shot("14_llegaste") { Navigation(tracking(NavigationStatus.ARRIVED, pattern = VibrationPatterns.ARRIVED)) },
        Shot("15_terminar_ruta") { Navigation(tracking(NavigationStatus.ON_ROUTE), confirmFinish = true) },
        Shot("16_lugares_guardados") {
            SavedPlacesScreen(PLACES, onChoose = {}, onDelete = {}, onBack = {})
        },
        Shot("17_borrar_lugar") { ConfirmDeleteScreen(PLACES[0], onConfirm = {}, onCancel = {}) },
        Shot("18_vibraciones") { VibrationsScreen(onPlay = {}, onBack = {}) },
        Shot("19_componentes") { ComponentsGallery() },
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
            VibrationIndicator(VibrationPatterns.ARRIVED, StatusArrived)
            PillButton("Ir", onClick = {}, style = PillStyle.PRIMARY)
        }
    }
}
