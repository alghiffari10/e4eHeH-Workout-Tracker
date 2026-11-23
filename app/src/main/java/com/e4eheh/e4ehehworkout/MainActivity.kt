package com.e4eheh.e4ehehworkout

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.e4eheh.e4ehehworkout.ui.E4eHeHWorkoutApp

/** Single-activity entry point for the Arkham Batcomputer workout tracker. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val appContainer = (application as WorkoutApplication).appContainer
        setContent {
            E4eHeHWorkoutApp(appContainer = appContainer)
        }
    }
}
