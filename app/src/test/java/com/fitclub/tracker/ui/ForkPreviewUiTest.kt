package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.BuildConfig
import com.fitclub.tracker.ui.dashboard.ProfileScreen
import androidx.compose.material3.Surface
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.workout.WorkoutScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForkPreviewUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(text: String) = rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun screenshot(name: String) {
        rule.waitUntil(15000) { rule.onAllNodesWithText("Загрузка…").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out = File("build/ui-preview/$name.png"); out.parentFile.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    @Test fun calendarSupportsIndependentPlansAndLongPressDeleteWithCancel() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val date = LocalDate.now().toEpochDay()
        val items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 0))
        val morning = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = date, title = "Утренняя", startMinute = 480, items = items)) }
        runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = date, title = "Вечерняя", startMinute = 1200, items = items)) }
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) { AppRoot() } }
        // Today shows the earliest plan; the calendar lists every plan of the day.
        waitFor("Утренняя")
        rule.onNodeWithText("Календарь").performClick()
        // The day's plans load asynchronously after navigation: retry until the list has them.
        rule.waitUntil(15_000) {
            runCatching { rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Утренняя")) }.isSuccess
        }
        screenshot("fork-calendar")
        rule.onNodeWithText("Утренняя", useUnmergedTree = true).performTouchInput { longClick() }
        waitFor("Удалить запланированную тренировку?")
        rule.onNodeWithText("Отмена").performClick()
        assertEquals(2, runBlocking { app.container.schedule.getAllForDate(date).size })
        rule.onNodeWithText("Утренняя", useUnmergedTree = true).performTouchInput { longClick() }
        waitFor("Удалить запланированную тренировку?")
        rule.onNodeWithText("Удалить").performClick()
        rule.waitUntil(15000) { runBlocking { app.container.schedule.get(morning) == null } }
        assertEquals("Вечерняя", runBlocking { app.container.schedule.getAllForDate(date).single().title })
    }
    @Test fun overviewAndCompletedExerciseHaveReadableUniformActions() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22000,
            items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 0)))) }
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) { WorkoutScreen(id, {}, {}) } }
        waitFor("УПРАЖНЕНИЕ 1 ИЗ 1")
        screenshot("fork-exercise")
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("60")
        screenshot("test-080-active-sets")
        rule.onNodeWithTag("done-0").performClick()
        rule.onNodeWithTag("complete-exercise").performClick()
        waitFor("Упражнение завершено")
        // After an exercise is finished, moving on is the one primary action and spans the width.
        val next = rule.onNodeWithTag("next-exercise").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(next.width > root.width * 0.8f)
        rule.onNodeWithTag("complete-exercise").assertDoesNotExist()
        screenshot("fork-completed-exercise")
    }
    @Test fun testSettingsShowWeightChoiceAndVersion() {
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) { Surface(color = Fc.Bg) { ProfileScreen({}) } } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        rule.onNodeWithText("Фунты (lb)").performClick()
        screenshot("test-080-settings")
    }

}

