package com.rumbo.watchapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rumbo.watchapp.di.RumboApplication
import com.rumbo.watchapp.presentation.viewmodel.RoutePlanningViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RoutePlanningViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as RumboApplication).container
                RoutePlanningViewModel(
                    container.audioRecorder,
                    container.planRouteFromVoice,
                    container.manageSavedPlaces,
                    container.locationTracker,
                    container.navigationRunner,
                    container.trackNavigation.state,
                    container.trackNavigation::repeat,
                    container.vibration,
                    container.speech,
                    container.voiceSettings,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RumboApp(viewModel)
        }
    }
}
