package com.fitclub.tracker.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
        assertEquals("Светлая", ProfileRepository(context).settings.value.theme)
        val light = mutableStateOf(true)
        fun colours(bg: Color, fg: Color) = "Фон: $bg; Текст: $fg"
        rule.setContent { FitClubTheme(lightTheme = light.value, accent = 0xFF536D00) {
            val bg = MaterialTheme.colorScheme.background
            val fg = Fc.Text
            TextButton(onClick = { light.value = !light.value }) {
                Text(if (light.value) "Светлая" else "Темная", color = fg,
                    modifier = Modifier.semantics { contentDescription = colours(bg, fg) })
            }
        } }
        rule.onNodeWithContentDescription(colours(Color(0xFFF3F4EF), Color(0xFF101315))).assertExists()
        rule.onNodeWithText("Светлая").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(colours(Color(0xFF0A0C0D), Color(0xFFF2F4EE)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Темная").assertExists()

    }
    @Test fun removedThemesAndAccentsMigrateToTwoSupportedChoices() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("profile", android.content.Context.MODE_PRIVATE)
        for (old in listOf("Volt Night", "Hot Pulse", "Cobalt Chalk")) {
            prefs.edit().putString("theme", old).putLong("accent", 0xFFD4FF3A).commit()
            val st = ProfileRepository(context).settings.value
            assertEquals("Темная", st.theme)
            assertEquals(0xFFC6FF3D, st.accent)
        }
        prefs.edit().putString("theme", "White Day").commit()
        val st = ProfileRepository(context).settings.value
        assertEquals("Светлая", st.theme)
        assertEquals(0xFFC6FF3D, st.accent)
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

