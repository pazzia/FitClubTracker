package com.fitclub.tracker.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.workout.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class WorkoutUiTest {
    @get:Rule val rule = createComposeRule()
    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun open(): FitApp {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22000,
            items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 0), ItemDraft("bbrow", 1, RepRange.R8_12, 0)))) }
        rule.setContent { FitClubTheme { WorkoutScreen(id, onClose = {}, onTechnique = {}) } }
        // A workout opens directly on its first exercise.
        waitFor("УПРАЖНЕНИЕ 1 ИЗ 2")
        return app
    }
    private fun openWith(vararg items: ItemDraft): FitApp {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22000, items = items.toList())) }
        rule.setContent { FitClubTheme { WorkoutScreen(id, onClose = {}, onTechnique = {}) } }
        waitFor("УПРАЖНЕНИЕ 1 ИЗ ${items.size}")
        return app
    }
    @Test fun nextSetIsPrefilledWithThePreviousWeight() {
        openWith(ItemDraft("bench", 3, RepRange.R8_12, 0))
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("62,5")
        rule.onNodeWithTag("done-0").performClick()
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-1"))
        rule.onNodeWithTag("weight-1").assert(hasText("62,5"))
    }
    @Test fun choosingAModeAppliesItWithoutConfirming() {
        openWith(ItemDraft("bench", 2, RepRange.R8_12, 0))
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("60")
        rule.onNodeWithTag("technique-0").performClick()
        waitFor("Как выполнить подход 1")
        rule.onNodeWithTag("technique-drop").performScrollTo().performClick()
        // Applied at once: the set card already shows the drop-set stages, before «Готово» is pressed.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Ступень 2 · кг").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("technique-apply").performScrollTo().performClick()
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("stage-weight-0-0"))
        rule.onNodeWithTag("stage-weight-0-0").assert(hasText("47,5"))
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("done-0"))
        rule.onNodeWithText("Дропсет выполнен · 3 ступ.").assertExists()
    }
    @Test fun supersetWithAnotherExerciseIsSavedAsItsOwnSet() {
        val app = openWith(ItemDraft("bench", 1, RepRange.R8_12, 0), ItemDraft("bbrow", 1, RepRange.R8_12, 0))
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("60")
        rule.onNodeWithTag("technique-0").performClick()
        waitFor("Как выполнить подход 1")
        rule.onNodeWithTag("technique-superset").performScrollTo().performClick()
        rule.onNodeWithTag("partner-bbrow").performScrollTo().performClick()
        rule.onNodeWithTag("technique-apply").performScrollTo().performClick()
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("partner-weight-0"))
        rule.onNodeWithTag("partner-weight-0").performTextReplacement("50")
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("done-0"))
        rule.onNodeWithText("Суперсет выполнен").assertExists()
        rule.onNodeWithTag("done-0").performClick()
        rule.onNodeWithTag("finish-workout").performClick()
        waitFor("Завершить и сохранить")
        rule.onNodeWithText("Завершить и сохранить").performClick()
        rule.waitUntil(15_000) { rule.waitForIdle(); runBlocking { app.container.fitness.observeWorkouts().first().isNotEmpty() } }
        val sets = runBlocking { app.container.fitness.observeWorkouts().first().first().sets }
        val partner = sets.single { it.exerciseId == "bbrow" }
        assertEquals("superset", partner.technique)
        assertEquals(50f, partner.weightKg)
        assertEquals("superset", sets.single { it.exerciseId == "bench" }.technique)
    }
    @Test fun canFinishImmediatelyAndSaveSessionWithoutSets() {
        val app = open()
        rule.onNodeWithTag("finish-workout").assertIsEnabled().performClick()
        waitFor("Завершить и сохранить")
        rule.onNodeWithText("Завершить и сохранить").performClick()
        rule.waitUntil(15_000) { rule.waitForIdle(); runBlocking { app.container.fitness.observeWorkouts().first().isNotEmpty() } }
        assertEquals(0, runBlocking { app.container.fitness.observeWorkouts().first().first().completedSets })
    }
    @Test fun completedExerciseShowsStatsAndNextExerciseRemainsReachable() {
        open()
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("60")
        rule.onNodeWithTag("done-0").performClick()
        rule.onNodeWithTag("complete-exercise").performClick()
        waitFor("Упражнение завершено")
        rule.onNodeWithText("Тоннаж: 600 кг").assertExists()
        rule.onNodeWithTag("next-exercise").assertIsEnabled().performClick()
        waitFor("УПРАЖНЕНИЕ 2 ИЗ 2")
        rule.onNodeWithTag("next-exercise").assertIsEnabled().performClick()
        waitFor("Завершить и сохранить")
    }
    @Test
    @Config(application = Application::class)
    fun plannedAndExtraRowsKeepIdenticalFieldBounds() {
        // Bounds do not depend on Material text-field animation time.
        rule.mainClock.autoAdvance = false
        rule.setContent { FitClubTheme { Column {
            SetRow(0, SetEntry("60", "8"), false, {}, {}) { _, _ -> }
            SetRow(3, SetEntry("60", "8"), true, {}, {}) { _, _ -> }
            SetRow(4, SetEntry("60", "8", true), true, {}, {}) { _, _ -> }
        } } }
        rule.mainClock.advanceTimeBy(64)
        val bounds = listOf("weight-0", "reps-0", "weight-3", "reps-3", "weight-4", "reps-4").map { rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.size }
        bounds.forEach { assertEquals(bounds.first().width, it.width, 1f); assertEquals(bounds.first().height, it.height, 1f) }
    }
}

