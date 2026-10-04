package com.fitclub.tracker

import android.os.Bundle
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitclub.tracker.ui.startup.AppStartup
import com.fitclub.tracker.ui.theme.FitClubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by (application as FitApp).container.profile.settings.collectAsStateWithLifecycle()
            val light = settings.theme == "Светлая"
            SideEffect {
                window.statusBarColor = if (light) android.graphics.Color.WHITE else android.graphics.Color.rgb(14, 19, 23)
                window.navigationBarColor = window.statusBarColor
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            FitClubTheme(accent = settings.accent, lightTheme = light) { AppStartup(application as FitApp) }
        }
    }
}


