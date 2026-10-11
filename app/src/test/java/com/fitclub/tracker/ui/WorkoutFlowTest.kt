package com.fitclub.tracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.workout.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class WorkoutFlowTest {
    @get:Rule val rule = createComposeRule()
    private fun waitFor(text: String) = rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun draft() = ScheduledDraft(epochDay = 22000, items = listOf(
        ItemDraft("bench", 1, RepRange.R8_12, 0), ItemDraft("bbrow", 1, RepRange.R8_12, 0)))
    @Test fun startFromSecondExerciseAndReturnWithValuesPreserved() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val id = runBlocking { app.container.schedule.save(draft()) }
        rule.setContent { FitClubTheme { WorkoutScreen(id, {}, {}) } }
        waitFor("УПРАЖНЕНИЕ 1 ИЗ 2")
        rule.onNodeWithText("План").performClick()
        waitFor("Идёт тренировка")
        rule.onNodeWithText("Тяга штанги в наклоне").performClick()
        waitFor("УПРАЖНЕНИЕ 2 ИЗ 2")
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("75")
        rule.onNodeWithText("План").performClick()
        waitFor("Начато · продолжить")
        rule.onNodeWithText("Тяга штанги в наклоне").performClick()
        waitFor("УПРАЖНЕНИЕ 2 ИЗ 2")
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").assertTextEquals("75")
    }
    @Test fun dragChangesExerciseOrder() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val id = runBlocking { app.container.schedule.save(draft()) }
        rule.setContent { FitClubTheme { WorkoutScreen(id, {}, {}) } }
        waitFor("УПРАЖНЕНИЕ 1 ИЗ 2")
        rule.onNodeWithText("План").performClick()
        waitFor("Идёт тренировка")
        rule.onNodeWithTag("drag-exercise-1", useUnmergedTree = true).performTouchInput {
            down(center); advanceEventTime(650); moveBy(androidx.compose.ui.geometry.Offset(0f, -350f)); up()
        }
        rule.waitForIdle()
        assertTrue(rule.onNodeWithText("Тяга штанги в наклоне").fetchSemanticsNode().boundsInRoot.top <
            rule.onNodeWithText("Жим штанги лёжа").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun resultEditorChangesExistingSessionAndUpdatesTotalVolume() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val now = System.currentTimeMillis()
        val id = runBlocking { app.container.fitness.finishWorkout(null, null, now - 90_000,
            listOf(CompletedSet("bench", 0, 60f, 8))) }
        rule.setContent { FitClubTheme { WorkoutResultScreen(id, {}) } }
        waitFor("Общий тоннаж: 480 кг")
        rule.onNodeWithText("Редактировать результат").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("70")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить изменения"))
        rule.onNodeWithText("Сохранить изменения").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Общий тоннаж: 560 кг"))
        waitFor("Общий тоннаж: 560 кг")
        waitFor("Редактировать результат")
        val logs = runBlocking { app.container.fitness.observeWorkouts().first() }
        assertEquals(1, logs.size)
        assertEquals(id, logs.single().session.id)
    }
    @Test fun reorderAndRemoveKeepLogsAttachedToTheirExerciseIncludingDuplicates() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        rule.runOnIdle {
            val vm = WorkoutViewModel(app.container, 0)
            val p = draft().copy(items = draft().items + draft().items.first())
            vm.installPlan(p)
            vm.logs[0] = androidx.compose.runtime.mutableStateListOf(SetEntry("60", "8", true))
            vm.logs[1] = androidx.compose.runtime.mutableStateListOf(SetEntry("75", "10", true))
            vm.logs[2] = androidx.compose.runtime.mutableStateListOf(SetEntry("65", "6", true))
            vm.completed[0] = true
            vm.started[1] = true
            vm.reorder(0, 2)
            assertEquals(listOf("bbrow", "bench", "bench"), vm.editedPlan!!.items.map { it.exerciseId })
            assertEquals("75", vm.logs[0]!!.single().weight)
            assertEquals("65", vm.logs[1]!!.single().weight)
            assertTrue(vm.completed[2] == true)
            assertTrue(vm.started[0] == true)
            vm.removeExercise(1)
            val sets = vm.completedSets(vm.editedPlan!!, true)
            assertEquals(listOf("bbrow", "bench"), sets.map { it.exerciseId })
            assertEquals(listOf(75f, 60f), sets.map { it.weightKg })
            vm.addExercise("plank")
            assertEquals(3, vm.editedPlan!!.items.size)
            assertEquals(2, vm.completedSets(vm.editedPlan!!, true).size)
        }
    }
}

