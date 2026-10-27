package com.rumbo.watchapp.infrastructure.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.rumbo.watchapp.R
import com.rumbo.watchapp.di.RumboApplication
import com.rumbo.watchapp.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private const val CHANNEL_ID = "navegacion"
private const val NOTIFICATION_ID = 1

/**
 * Servicio en primer plano de tipo ubicación: mantiene el GPS y las vibraciones con la
 * pantalla apagada. Solo orquesta: arranca y detiene [TrackNavigation][com.rumbo.watchapp.application.usecase.TrackNavigation].
 */
class NavigationForegroundService : Service() {

    // Un solo hilo (main) para que lecturas y ticker de TrackNavigation no compitan.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val container get() = (application as RumboApplication).container

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        val route = container.navigationRunner.currentRoute
        if (route == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        container.locationTracker.start()
        container.trackNavigation.start(route, scope)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        container.trackNavigation.stop()
        container.locationTracker.stop()
        scope.cancel()
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
            .setContentText(container.navigationRunner.currentRoute?.destinationName.orEmpty())
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
        }
    }
}
