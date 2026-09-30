package com.fitclub.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.FitClubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by (application as FitApp).container.profile.settings.collectAsStateWithLifecycle()
            FitClubTheme(accent = settings.accent) { AppRoot() }
        }
    }
}
