package com.fitclub.tracker.ui

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
@Config(sdk = [28], qualifiers = "w390dp-h844dp")
class StartupUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun loadingPreventsEntryAndReadyShowsReleaseNotes() {
        val state = mutableStateOf<StartupState>(StartupState.Loading)
        rule.setContent { FitClubTheme { StartupScreen(state.value, true, {}, {}) } }
        rule.onNodeWithText("Загружаем программы и справочник…").assertExists()
        rule.onNodeWithText("Продолжить").assertIsNotEnabled()
        rule.runOnIdle { state.value = StartupState.Ready }
        rule.onNodeWithText("Что нового").assertExists()
        rule.onNodeWithText("Продолжить").assertIsEnabled()
    }
}
