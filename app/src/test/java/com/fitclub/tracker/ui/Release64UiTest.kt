package com.fitclub.tracker.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.repo.ProfileRepository
import com.fitclub.tracker.ui.programs.ProgramDeleteDialog
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Release64UiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun lightThemeSwitchesBackAndPreferenceSurvivesReload() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = ProfileRepository(context)
        repository.update(repository.settings.value.copy(theme = "White Day", accent = 0xFF536D00))
        assertEquals("White Day", ProfileRepository(context).settings.value.theme)
        val light = mutableStateOf(true)
        var background = Color.Unspecified
        var text = Color.Unspecified
        rule.setContent { FitClubTheme(lightTheme = light.value, accent = 0xFF536D00) {
            background = MaterialTheme.colorScheme.background
            text = Fc.Text
            Text(if (light.value) "Светлая" else "Тёмная", color = Fc.Text)
        } }
        rule.mainClock.advanceTimeBy(64)
        rule.runOnIdle { assertEquals(Color.White, background); assertEquals(Color(0xFF17212B), text); light.value = false }
        rule.waitUntil(10_000) { background == Color(0xFF0E1317) }
        rule.onNodeWithText("Тёмная").assertExists()
        rule.runOnIdle { assertEquals(Color(0xFF0E1317), background); assertEquals(Color(0xFFF5F5F7), text) }
    }
    @Test fun deletionRequiresExplicitConfirmationAndCancelPreservesProgram() {
        rule.mainClock.autoAdvance = false
        var programExists = true
        val dialog = mutableStateOf(true)
        rule.setContent { FitClubTheme {
            if (dialog.value) ProgramDeleteDialog("План", { dialog.value = false }) { programExists = false; dialog.value = false }
        } }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Удалить программу?").assertExists()
        rule.runOnIdle { assertTrue(programExists) }
        rule.onNodeWithText("Отмена").performClick()
        rule.runOnIdle { assertTrue(programExists); dialog.value = true }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("Удалить").performClick()
        rule.runOnIdle { assertFalse(programExists) }
    }
}

