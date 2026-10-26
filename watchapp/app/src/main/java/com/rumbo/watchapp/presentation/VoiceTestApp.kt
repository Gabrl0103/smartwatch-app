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
import com.rumbo.watchapp.presentation.theme.WatchappTheme
import java.io.File
import kotlinx.coroutines.delay

/**
 * Pantalla temporal de prueba: graba 4 segundos con el micrófono, mide el nivel de
 * sonido y permite reproducir lo grabado. Sirve para saber si el micrófono y el
 * altavoz del emulador funcionan. El archivo grabado (AAC) es el mismo tipo de audio
 * que luego se enviará al backend para que Gemini lo transcriba.
 */
@Composable
fun VoiceTestApp() {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Toca Grabar y habla") }
    var peak by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
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
            recording = true
            status = "Grabando... habla ahora"
        } catch (e: Exception) {
            status = "Error al grabar: ${e.message}"
        }
    }

    fun stopRecording() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        fileKb = (file.length() / 1024).toInt()
        status = if (peak > 1000) "Listo: sí se oyó tu voz" else "Listo, pero casi sin sonido"
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
                            Text("Prueba de micrófono")
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                if (recording) return@Button
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
