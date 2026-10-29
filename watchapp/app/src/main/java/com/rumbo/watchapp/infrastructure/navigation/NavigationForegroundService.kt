package com.rumbo.watchapp.infrastructure.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rumbo.watchapp.R
import com.rumbo.watchapp.di.RumboApplication
import com.rumbo.watchapp.presentation.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val CHANNEL_ID = "navegacion"
private const val TAG = "Nav"
private const val NOTIFICATION_ID = 1

/**
 * Servicio en primer plano de tipo ubicación: mantiene el GPS y las vibraciones con la
 * pantalla apagada. Solo orquesta: arranca y detiene [TrackNavigation][com.rumbo.watchapp.application.usecase.TrackNavigation].
 */
class NavigationForegroundService : Service() {

    // Un solo hilo (main) para que lecturas y ticker de TrackNavigation no compitan. Un error
    // inesperado queda en el log en vez de cerrar la app.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Error inesperado en la navegación", e) },
    )

    private val container get() = (application as RumboApplication).container

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        val route = container.navigationRunner.activeRoute
        if (route == null) {
            Log.w(TAG, "Servicio iniciado sin ruta: se detiene")
            stopSelf()
            return START_NOT_STICKY
        }
        Log.i(TAG, "Servicio en primer plano: GPS y vibraciones siguen con la pantalla apagada")
        container.locationTracker.start()
        container.trackNavigation.start(route, scope)
        // Si el proceso muere no se reinicia solo: al reabrir, la app ofrece continuar la ruta.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Normalmente "Terminar" ya detuvo todo; esto cubre que Android cierre el servicio.
        container.trackNavigation.stop()
        container.locationTracker.stop()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "Servicio detenido y notificación quitada")
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Navegación", NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle("Rumbo: navegando")
            .setContentText(container.navigationRunner.activeRoute?.destinationName.orEmpty())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openApp)
            .build()
        try {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: SecurityException) {
            // Sin permiso de ubicación Android no deja usar el tipo "location": la navegación
            // sigue como servicio normal y mostrará "Sin señal GPS".
            Log.w(TAG, "Sin servicio en primer plano de ubicación: ${e.message}")
        } catch (e: IllegalStateException) {
            // Android 12+ no deja pasar a primer plano desde segundo plano: se navega igual.
            Log.w(TAG, "No se pudo pasar a primer plano: ${e.message}")
        }
    }
}
