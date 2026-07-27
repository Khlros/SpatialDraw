package com.example.myspatial

import androidx.compose.ui.Modifier
import com.example.myspatial.ui.DrawerHomeScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) = with(scope) {
    DefaultStage {
        PicoTheme {
            DrawerHomeScreen(modifier = Modifier)
        }
    }
}
