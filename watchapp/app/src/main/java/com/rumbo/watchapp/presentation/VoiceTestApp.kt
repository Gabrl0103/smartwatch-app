package com.rumbo.watchapp.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.rumbo.watchapp.domain.model.VoiceResult
import com.rumbo.watchapp.infrastructure.network.VoiceApiClient
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla temporal de prueba: graba 4 segundos con el micrófono, envía el audio (AAC)
 * al backend para que Gemini lo transcriba e interprete y muestra lo que entendió.
 * También permite reproducir lo grabado para comprobar el micrófono del emulador.
 */
@Composable
fun VoiceTestApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Toca Grabar y habla") }
    var result by remember { mutableStateOf<VoiceResult?>(null) }
    var peak by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var fileKb by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val file = remember { File(context.cacheDir, "prueba.aac") }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.release() }
            runCatching { player?.release() }
        }
    }

    @Suppress("DEPRECATION")
    fun startRecording() {
        try {
            val newRecorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    MediaRecorder()
                }
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioSamplingRate(16000)
            newRecorder.setAudioEncodingBitRate(64000)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            peak = 0
            fileKb = 0
            result = null
            recording = true
            status = "Grabando... habla ahora"
        } catch (e: Exception) {
            status = "Error al grabar: ${e.message}"
        }
    }

    fun sendRecording() {
        sending = true
        status = "Enviando al servidor..."
        scope.launch {
            try {
                result = VoiceApiClient.interpret(file)
                status = "Listo"
            } catch (e: Exception) {
                status = "Error: ${e.message ?: "no se pudo conectar"}"
            } finally {
                sending = false
            }
        }
    }

    fun stopRecording() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        fileKb = (file.length() / 1024).toInt()
        if (file.length() > 0) {
            sendRecording()
        } else {
            status = "No se grabó audio"
        }
    }

    fun playRecording() {
        try {
            runCatching { player?.release() }
            val newPlayer = MediaPlayer()
            newPlayer.setDataSource(file.absolutePath)
            newPlayer.setOnCompletionListener { status = "Reproducción terminada" }
            newPlayer.prepare()
            newPlayer.start()
            player = newPlayer
            status = "Reproduciendo..."
        } catch (e: Exception) {
            status = "Error al reproducir: ${e.message}"
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < 4000) {
                delay(200)
                val level = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                if (level > peak) peak = level
            }
            stopRecording()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) startRecording() else status = "Permiso de micrófono denegado"
    }

    WatchappTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                    item {
                        ListHeader(
                            modifier = Modifier.fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Prueba de voz")
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                if (recording || sending) return@Button
                                if (micGranted) {
                                    startRecording()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Grabar 4 s")
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                if (!recording && fileKb > 0) {
                                    playRecording()
                                } else {
                                    status = "Primero graba algo"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Escuchar")
                        }
                    }
                    item {
                        Text(
                            text = status,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    result?.let { voice ->
                        val lines = listOf(
                            "Entendí: ${voice.text}",
                            "Intención: ${voice.intent}",
                            "Destino: ${voice.destination ?: "-"}",
                        )
                        lines.forEach { line ->
                            item {
                                Text(
                                    text = line,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "Nivel de voz: $peak",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    item {
                        Text(
                            text = "Archivo: $fileKb KB",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
