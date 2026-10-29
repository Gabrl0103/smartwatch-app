package com.rumbo.watchapp.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.rumbo.watchapp.presentation.viewmodel.Permission

/**
 * Permisos de Android detrás de cada [Permission]; el primero es el que de verdad hace falta.
 * Android 12+ ignora la ubicación precisa si no se pide junto con la aproximada, y con solo la
 * aproximada no se puede guiar. Antes de Android 13 las notificaciones no necesitan permiso.
 */
private fun Permission.androidPermissions(): List<String> = when (this) {
    Permission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
    Permission.LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    Permission.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
}

fun Context.isGranted(permission: Permission): Boolean {
    val required = permission.androidPermissions().firstOrNull() ?: return true
    return ContextCompat.checkSelfPermission(this, required) == PackageManager.PERMISSION_GRANTED
}

/**
 * Abre la página de la app en Ajustes (o los Ajustes generales) desde la actividad, para que
 * "atrás" vuelva a Rumbo. false si no hay a dónde ir.
 */
fun Context.openAppSettings(): Boolean {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    return listOf(appDetails, Intent(Settings.ACTION_SETTINGS)).any { intent ->
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

private class PendingRequest {
    var permissions: List<Permission> = emptyList()
    var action: () -> Unit = {}
}

/**
 * Devuelve `request(permissions, action)`: si ya están todos, corre [action]; si no, pide los que
 * faltan (Android los muestra uno tras otro) y corre [action] solo si se dieron todos. Si alguno
 * se niega, avisa el primero con [onDenied] para mostrar su explicación. [onResult] se llama tras
 * cada respuesta del sistema, p. ej. para encender el GPS si se dio la ubicación.
 */
@Composable
fun rememberPermissionRequest(
    onDenied: (Permission) -> Unit,
    onResult: () -> Unit,
): (List<Permission>, () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { PendingRequest() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onResult()
        val denied = pending.permissions.firstOrNull { !context.isGranted(it) }
        if (denied == null) pending.action() else onDenied(denied)
    }
    return remember(launcher) {
        { permissions, action ->
            val missing = permissions.filterNot { context.isGranted(it) }
            if (missing.isEmpty()) {
                action()
            } else {
                pending.permissions = permissions
                pending.action = action
                launcher.launch(missing.flatMap { it.androidPermissions() }.toTypedArray())
            }
        }
    }
}
