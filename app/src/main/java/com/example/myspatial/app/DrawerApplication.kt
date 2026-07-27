package com.example.myspatial.app

import android.app.Application
import com.example.myspatial.mainApp
import com.pico.spatial.ui.foundation.dsl.launch

class DrawerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
