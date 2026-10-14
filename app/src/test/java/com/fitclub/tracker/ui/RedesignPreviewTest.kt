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
import com.fitclub.tracker.ui.workout.AnimationPack
import com.fitclub.tracker.ui.workout.WorkoutResultScreen
import com.fitclub.tracker.ui.workout.WorkoutScreen
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Iron & Lime redesign: the start countdown and the completion screen follow the design pack's
 * timeline, and every main screen is rendered for review in the dark theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedesignPreviewTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FitApp>()

    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** With the clock paused, frames are stepped by hand until [condition] holds. */
    private fun stepUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(5)
        }
    }

    private fun hasTag(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun screenshot(name: String) {
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out = File("build/ui-preview/redesign-$name.png"); out.parentFile.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }

    private fun settle(name: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Загрузка…").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        screenshot(name)
    }

    @Test fun packTimingsComeFromTheBundledConfig() {
        val timings = AnimationPack.timings(app)
        assertEquals(900, timings.start.stepMs)
        assertEquals(2700, timings.start.goAtMs)
        assertEquals(3300, timings.start.finishAtMs)
        assertEquals(300, timings.complete.ringsAtMs)
        assertEquals(1050, timings.complete.statsAtMs)
        assertEquals(1450, timings.complete.streakAtMs)
        assertEquals(2200, timings.complete.finishAtMs)
        assertEquals(700, timings.start.reduced().stepMs)
    }

    @Test fun countdownRunsThreeTwoOneGoThenOpensTheFirstExercise() {
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = LocalDate.now().toEpochDay(), title = "Фулбоди 3× · день A",
            items = listOf(ItemDraft("bench", 3, RepRange.R8_12, 90), ItemDraft("bbrow", 3, RepRange.R8_12, 90)))) }
        rule.mainClock.autoAdvance = false
        rule.setContent { FitClubTheme { WorkoutScreen(id, {}, {}, countdown = true) } }
        stepUntil { hasTag("workout-countdown") }
        rule.mainClock.advanceTimeBy(400)
        rule.onAllNodesWithText("3").onFirst().assertExists()
        rule.onNodeWithText("Жим штанги лёжа").assertExists()
        screenshot("countdown-3")
        rule.mainClock.advanceTimeBy(1000)
        rule.onAllNodesWithText("2").onFirst().assertExists()
        screenshot("countdown-2")
        rule.mainClock.advanceTimeBy(1450)
        rule.onNodeWithText("GO").assertExists()
        screenshot("countdown-go")
        rule.mainClock.advanceTimeBy(800)
        rule.mainClock.autoAdvance = true
        waitFor("Упражнение 1 из 2")
        rule.onNodeWithTag("workout-countdown").assertDoesNotExist()
        settle("workout-exercise")
    }

    @Test fun tappingTheCountdownStartsAtOnce() {
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22000, items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 0)))) }
        rule.setContent { FitClubTheme { WorkoutScreen(id, {}, {}, countdown = true) } }
        rule.waitUntil(15_000) { hasTag("workout-countdown") || rule.onAllNodesWithText("Упражнение 1 из 1").fetchSemanticsNodes().isNotEmpty() }
        if (hasTag("workout-countdown")) rule.onNodeWithTag("workout-countdown").performClick()
        waitFor("Упражнение 1 из 1")
    }

    @Test fun countdownCanBeSwitchedOffInTheProfile() {
        app.container.profile.update(app.container.profile.settings.value.copy(startCountdown = false))
        try {
            val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22000, items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 0)))) }
            rule.setContent { FitClubTheme { WorkoutScreen(id, {}, {}, countdown = true) } }
            waitFor("Упражнение 1 из 1")
            rule.onNodeWithTag("workout-countdown").assertDoesNotExist()
        } finally {
            app.container.profile.update(app.container.profile.settings.value.copy(startCountdown = true))
        }
    }

    @Test fun completionScreenAnimatesAndUnlocksItsActions() {
        app.container.profile.update(app.container.profile.settings.value.copy(name = "Дмитрий"))
        val now = System.currentTimeMillis()
        val id = runBlocking {
            app.container.fitness.finishWorkout(null, null, now - 21 * 60_000L,
                listOf(CompletedSet("bench", 0, 60f, 8), CompletedSet("bench", 1, 62.5f, 8), CompletedSet("bbrow", 0, 50f, 10, exercisePosition = 1)),
                title = "Фулбоди 3× · день A")
        }
        rule.mainClock.autoAdvance = false
        rule.setContent { FitClubTheme { WorkoutResultScreen(id, {}, celebrate = true) } }
        stepUntil { hasTag("complete-header") }
        rule.onNodeWithTag("result-progress").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(250); screenshot("complete-0250")
        rule.mainClock.advanceTimeBy(550); screenshot("complete-0800")
        rule.mainClock.advanceTimeBy(700); screenshot("complete-1500")
        rule.mainClock.advanceTimeBy(1200)
        rule.mainClock.autoAdvance = true
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithTag("result-progress").assertIsEnabled() }.isSuccess }
        rule.onNodeWithText("Отличная работа,\nДмитрий").assertExists()
        settle("complete-final")
        rule.onNodeWithTag("result-list").performScrollToNode(hasText("Редактировать результат"))
        settle("complete-details")
        app.container.profile.update(app.container.profile.settings.value.copy(name = "Мой профиль"))
    }

    @Test fun mainScreensInTheDarkTheme() {
        val today = LocalDate.now().toEpochDay()
        runBlocking {
            app.container.fitness.finishWorkout(null, null, System.currentTimeMillis() - 2 * 86_400_000L,
                listOf(CompletedSet("bench", 0, 60f, 8)), title = "Грудь и спина")
            app.container.schedule.save(ScheduledDraft(epochDay = today, title = "Фулбоди 3× · день A", startMinute = 1080,
                items = listOf(ItemDraft("bench", 3, RepRange.R8_12, 90), ItemDraft("bbrow", 3, RepRange.R8_12, 90),
                    ItemDraft("squat", 3, RepRange.R6_8, 120), ItemDraft("plank", 3, RepRange.TIME, 60))))
        }
        rule.setContent { FitClubTheme { AppRoot() } }
        waitFor("Начать тренировку")
        settle("today")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("План недели"))
        settle("today-week")
        rule.onNodeWithText("Программы").performClick()
        rule.waitForIdle()
        settle("programs")
        rule.onNodeWithText("Справочник").performClick()
        rule.waitForIdle()
        settle("library")
        rule.onNodeWithText("Прогресс").performClick()
        rule.waitForIdle()
        settle("progress")
        rule.onNodeWithText("Профиль").performClick()
        rule.waitForIdle()
        settle("profile")
        rule.onNodeWithText("Сегодня").performClick()
        rule.onNodeWithText("Календарь").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_500)
        settle("calendar")
    }
}
