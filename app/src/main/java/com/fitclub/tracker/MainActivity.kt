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
        // Development builds must allow screenshots and screen recording for UX review.
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val settings by (application as FitApp).container.profile.settings.collectAsStateWithLifecycle()
            val light = settings.theme == "Светлая"
            SideEffect {
                window.statusBarColor = if (light) android.graphics.Color.rgb(243, 244, 239) else android.graphics.Color.rgb(10, 12, 13)
                window.navigationBarColor = window.statusBarColor
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            FitClubTheme(accent = settings.accent, lightTheme = light) { AppStartup(application as FitApp) }
        }
    }

    override fun onResume() {
        super.onResume()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}


