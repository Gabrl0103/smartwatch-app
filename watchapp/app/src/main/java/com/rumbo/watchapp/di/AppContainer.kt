package com.rumbo.watchapp.di

import android.content.Context
import com.rumbo.watchapp.application.usecase.PlanRouteFromVoice
import com.rumbo.watchapp.domain.port.AudioRecorder
import com.rumbo.watchapp.infrastructure.audio.MediaAudioRecorder
import com.rumbo.watchapp.infrastructure.network.PlacesApiClient
import com.rumbo.watchapp.infrastructure.network.RoutesApiClient
import com.rumbo.watchapp.infrastructure.network.VoiceApiClient

/** Composition root: único lugar donde se eligen las implementaciones. */
class AppContainer(context: Context) {
    val audioRecorder: AudioRecorder = MediaAudioRecorder(context.applicationContext)
    val planRouteFromVoice = PlanRouteFromVoice(VoiceApiClient, PlacesApiClient, RoutesApiClient)
}
