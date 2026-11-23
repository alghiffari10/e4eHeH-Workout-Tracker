package com.e4eheh.e4ehehworkout

import android.app.Application
import com.e4eheh.e4ehehworkout.di.AppContainer

/** Application entry point holding the singleton manual DI container. */
class WorkoutApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer.create(this)
    }
}
