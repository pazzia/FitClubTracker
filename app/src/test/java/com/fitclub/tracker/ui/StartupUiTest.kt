package com.fitclub.tracker.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitclub.tracker.StartupState
import com.fitclub.tracker.ui.startup.StartupScreen
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = Application::class)
class StartupUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun loadingPreventsEntryAndReadyShowsReleaseNotes() {
        // Inspect a deterministic frame without waiting for the loading spinner to finish.
        rule.mainClock.autoAdvance = false
        val state = mutableStateOf<StartupState>(StartupState.Loading)
        rule.setContent { FitClubTheme { StartupScreen(state.value, true, {}, {}) } }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Загружаем программы и справочник…").assertExists()
        rule.onNodeWithText("Продолжить").assertIsNotEnabled()
        rule.runOnIdle { state.value = StartupState.Ready }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Что нового").assertExists()
        rule.onNodeWithText("Продолжить").assertIsEnabled()
    }
}

