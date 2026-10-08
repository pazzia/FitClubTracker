package com.fitclub.tracker.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.repo.CompletedSet
import com.fitclub.tracker.domain.LocalWeightUnits
import com.fitclub.tracker.domain.WeightUnits
import com.fitclub.tracker.ui.dashboard.ProgressScreen
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.workout.WorkoutResultScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class WorkoutRecordsUiTest {
    @get:Rule val rule = createComposeRule()
    private val exercise = "record-modes"
    private fun scrollTo(matcher: SemanticsMatcher) = rule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    private fun await(text: String) = rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun progressSeparatesRecordsAndHistoryAndRecalculatesOnlyWeight() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        runBlocking {
            app.container.fitness.finishWorkout(null, null, System.currentTimeMillis() - 60000, listOf(
                CompletedSet(exercise, 0, 40f, 8), CompletedSet(exercise, 1, 30f, 12),
                CompletedSet(exercise, 2, 0f, 60, isTimed = true),
                CompletedSet(exercise, 3, 200f, 8, warmup = true),
                CompletedSet(exercise, 4, 190f, 8, assisted = true),
                CompletedSet(exercise, 5, 0f, 120, isTimed = true, warmup = true)))
        }
        val kg = mutableStateOf(true)
        rule.setContent { FitClubTheme {
            CompositionLocalProvider(LocalWeightUnits provides WeightUnits(kg.value)) { ProgressScreen({}) }
        } }
        await("12 недель")
        val weightedCard = hasTestTag("progress-$exercise-false")
        val timedCard = hasTestTag("progress-$exercise-true")
        scrollTo(weightedCard)
        rule.onNodeWithText("Самый тяжёлый подход: 40 кг × 8").assertExists()
        rule.onNode(hasText("История упражнения") and hasAnyAncestor(weightedCard)).performClick()
        rule.onNode(hasText("40 кг × 8") and hasAnyAncestor(weightedCard)).assertExists()
        rule.onNode(hasText("30 кг × 12") and hasAnyAncestor(weightedCard)).assertExists()
        rule.onNode(hasText("60 сек.") and hasAnyAncestor(weightedCard)).assertDoesNotExist()
        scrollTo(timedCard)
        rule.onNodeWithText("Самое долгое удержание: 60 сек.").assertExists()
        rule.onNode(hasText("60 сек.") and hasAnyAncestor(timedCard)).assertExists()
        rule.onNode(hasText("40 кг × 8") and hasAnyAncestor(timedCard)).assertDoesNotExist()
        rule.runOnIdle { kg.value = false }
        scrollTo(weightedCard)
        rule.onNodeWithText("Самый тяжёлый подход: 88,2 фунт. × 8").assertExists()
        scrollTo(timedCard)
        rule.onNodeWithText("Самое долгое удержание: 60 сек.").assertExists()
        assertEquals(40f, runBlocking { app.container.fitness.observeWorkouts().first().single().sets.first().weightKg }, 0f)
    }

    @Test fun legacyMixedResultShowsBothRecordsAndComparableEarlierRecords() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val now = System.currentTimeMillis()
        val id = runBlocking {
            app.container.fitness.finishWorkout(null, null, now - 120000, listOf(
                CompletedSet(exercise, 0, 35f, 6), CompletedSet(exercise, 1, 0f, 45, isTimed = true)))
            app.container.fitness.finishWorkout(null, null, now - 60000, listOf(
                CompletedSet(exercise, 0, 40f, 8), CompletedSet(exercise, 1, 0f, 60, isTimed = true)))
        }
        rule.setContent { FitClubTheme { WorkoutResultScreen(id, {}) } }
        await("Результат тренировки")
        scrollTo(hasText("Самый тяжёлый: 40 кг × 8"))
        rule.onNodeWithText("Самый тяжёлый: 40 кг × 8").assertExists()
        rule.onNodeWithText("Рекорд до тренировки: 35 кг × 6").assertExists()
        rule.onNodeWithText("Самое долгое удержание: 60 сек.").assertExists()
        rule.onNodeWithText("Рекорд до тренировки: 45 сек.").assertExists()
    }

    @Test fun timedHistoryDoesNotBecomePreviousWeightedRecord() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val now = System.currentTimeMillis()
        val id = runBlocking {
            app.container.fitness.finishWorkout(null, null, now - 120000, listOf(CompletedSet(exercise, 0, 0f, 60, isTimed = true)))
            app.container.fitness.finishWorkout(null, null, now - 60000, listOf(CompletedSet(exercise, 0, 40f, 8)))
        }
        rule.setContent { FitClubTheme { WorkoutResultScreen(id, {}) } }
        await("Результат тренировки")
        scrollTo(hasText("Самый тяжёлый: 40 кг × 8"))
        rule.onNodeWithText("Первый результат с повторениями в истории").assertExists()
        rule.onNodeWithText("Рекорд до тренировки: 60 сек.").assertDoesNotExist()
    }
}
