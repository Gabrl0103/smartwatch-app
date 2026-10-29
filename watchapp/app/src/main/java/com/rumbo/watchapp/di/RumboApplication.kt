package com.rumbo.watchapp.di

import android.app.Application

/** Comparte un solo [AppContainer] entre la actividad y el servicio de navegación. */
class RumboApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.wakeUpServerInBackground()
        container.registerDeviceInBackground()
    }
}
