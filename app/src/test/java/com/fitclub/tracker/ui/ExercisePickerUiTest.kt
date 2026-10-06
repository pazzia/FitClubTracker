package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.ui.programs.ProgramExercisePicker
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.theme.Fc
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExercisePickerUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var added = emptyList<String>()
    private var closed = false
    private fun picker(already: Set<String> = emptySet(), light: Boolean = true) {
        rule.setContent { FitClubTheme(lightTheme = light, accent = 0xFF236B9D) { Surface(color = Fc.Bg) {
            ProgramExercisePicker(ExerciseSeed.all, already, "День A · Моя программа", { closed = true }, { added = it })
        } } }
    }
    private fun query(text: String) {
        rule.onNodeWithTag("exercise-search").performTextReplacement(text)
        rule.waitUntil(15000) { rule.onAllNodesWithText("Поиск…").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/ui-preview/$name.png"); file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun fuzzySearchSelectionPersistsAcrossViewAndQueryAndBatchIncludesHidden() {
        picker()
        query("штанг лежа")
        rule.onNodeWithTag("picker-exercise-bench").performClick()
        query("тгяа")
        rule.onNodeWithText("Вне текущего поиска: 1").assertExists()
        rule.onNodeWithTag("picker-view").performClick()
        rule.onNodeWithTag("picker-select-all").performClick()
        val expected = com.fitclub.tracker.ui.library.ExerciseSearchIndex(ExerciseSeed.all).search("тгяа").map { it.id }.toSet() + "bench"
        screenshot("exercise-picker-filtered-tree")
        rule.onNodeWithText("Проверить выбор").performClick()
        rule.onNodeWithText("Выбранные упражнения").assertExists()
        rule.onNodeWithTag("picker-add").performClick()
        assertEquals(expected, added.toSet())
        assertEquals(added.size, added.distinct().size)
    }
    @Test fun treeCollapsesAndFilteringRetainsOnlyMatchingGroup() {
        picker()
        query("штанг лежа")
        rule.onNodeWithTag("picker-view").performClick()
        rule.onNodeWithContentDescription("Свернуть Грудь").performClick()
        rule.onNodeWithTag("picker-exercise-bench").assertDoesNotExist()
        rule.onNodeWithContentDescription("Развернуть Грудь").performClick()
        rule.onNodeWithTag("picker-exercise-bench").assertExists()
        rule.onNodeWithTag("picker-group-BACK").assertDoesNotExist()
        rule.onNodeWithContentDescription("Выбрать группу Грудь").performClick()
        rule.onNodeWithText("Выбрано: 1").assertExists()
        query("zzzzzz")
        rule.onNodeWithTag("picker-select-all").assertIsNotEnabled()
        rule.onNodeWithText("Выбрано: 1").assertExists()
        rule.onNodeWithTag("picker-add").performClick()
        assertEquals(listOf("bench"), added)
    }
    @Test fun alreadyAddedIsDisabledAndCancelRequiresConfirmation() {
        picker(setOf("bench"), light = false)
        query("штанг лежа")
        rule.onNodeWithTag("picker-exercise-bench").assertIsNotEnabled()
        rule.onNodeWithTag("picker-select-all").assertIsNotEnabled()
        query("жим гант")
        rule.onNodeWithTag("picker-select-all").performClick()
        screenshot("exercise-picker-dark-search")
        rule.onNodeWithText("К программе").performClick()
        rule.onNodeWithText("Отменить выбор упражнений?").assertExists()
        rule.onNodeWithText("Продолжить выбор").performClick()
        assertFalse(closed)
        rule.onNodeWithText("К программе").performClick()
        rule.onNodeWithText("Отменить выбор").performClick()
        assertTrue(closed)
        assertTrue(added.isEmpty())
    }
}
