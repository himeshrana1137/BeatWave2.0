package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.BeatWaveRootApp
import com.example.ui.theme.BeatWaveTheme

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as BeatWaveApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            BeatWaveTheme {
                BeatWaveRootApp(container = appContainer)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appContainer.playbackController.initializeConnection()
    }
}
