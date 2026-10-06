package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
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
        waitFor("Утренняя")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Утренняя"))
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
        waitFor("Жим штанги лёжа")
        screenshot("fork-overview")
        rule.onNodeWithText("Жим штанги лёжа").performClick()
        waitFor("УПРАЖНЕНИЕ 1 ИЗ 1")
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("60")
        rule.onNodeWithTag("done-0").performClick()
        rule.onNodeWithTag("complete-exercise").performClick()
        waitFor("Упражнение завершено")
        val left = rule.onNodeWithText("Все упражнения").fetchSemanticsNode().boundsInRoot
        val right = rule.onNodeWithTag("next-exercise").fetchSemanticsNode().boundsInRoot
        assertEquals(left.height, right.height, 1f)
        assertEquals(left.width, right.width, 1f)
        screenshot("fork-completed-exercise")
    }
}

