package com.fitclub.tracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.BuildConfig
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.dashboard.ProfileScreen
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
class ReviewUiTest {
    @get:Rule val rule = createComposeRule()
    private fun waitFor(text: String) = rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    @Test fun settingsShowVersionAndExplicitUnitChoice() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        rule.setContent { FitClubTheme { ProfileScreen({}) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Версия приложения: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        rule.onNodeWithText("Фунты (lb)").performClick()
        rule.runOnIdle { assertFalse(app.container.profile.settings.value.unitsKg) }
        rule.onNodeWithText("Килограммы (кг)").performClick()
        rule.runOnIdle { assertTrue(app.container.profile.settings.value.unitsKg) }
    }
    @Test fun historyEditorAcceptsPoundsAndPreservesCanonicalKilograms() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        app.container.profile.update(app.container.profile.settings.value.copy(unitsKg = false))
        val id = runBlocking { app.container.fitness.finishWorkout(null, null, System.currentTimeMillis() - 60000, listOf(CompletedSet("bench", 0, 60f, 8))) }
        rule.setContent { FitClubTheme { WorkoutResultScreen(id, {}) } }
        waitFor("Общий тоннаж: 1058.2 фунт.")
        rule.onNodeWithText("Редактировать результат").performClick()
        waitFor("Сохранить изменения")
        rule.onNodeWithTag("weight-0").assertTextEquals("132.3")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить изменения"))
        rule.onNodeWithText("Сохранить изменения").performClick()
        waitFor("Редактировать результат")
        assertEquals(60f, runBlocking { app.container.fitness.observeWorkouts().first().single().sets.single().weightKg }, 0f)
    }
    @Test fun durableSnapshotRestoresDuplicatesUnitsAndRestAndZeroSetsCannotComplete() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        rule.runOnIdle {
            val vm = WorkoutViewModel(app.container, 0)
            vm.installPlan(ScheduledDraft(epochDay = 22000, items = listOf(ItemDraft("bench", 1, RepRange.R3_5, 90), ItemDraft("bench", 1, RepRange.R8_12, 90))))
            vm.moveTo(0); vm.completeExercise()
            assertFalse(vm.completed[0] == true)
            vm.logs[0] = androidx.compose.runtime.mutableStateListOf(SetEntry("60", "5", true, true, "3"))
            vm.logs[1] = androidx.compose.runtime.mutableStateListOf(SetEntry("65", "10", true))
            vm.startRest(90); vm.overview()
            assertEquals(90, vm.rest)
            val snapshot = vm.snapshot()
            runBlocking { app.container.fitness.saveActive(0, "План", snapshot) }
            val restored = WorkoutViewModel(app.container, 0)
            restored.restore(runBlocking { app.container.fitness.getActive(0)!!.snapshot })
            assertEquals(vm.startedAt, restored.startedAt)
            assertEquals(listOf(60f, 65f), restored.completedSets(restored.editedPlan!!, true).map { it.weightKg })
            assertTrue(restored.logs[0]!!.first().warmup)
            assertEquals("3", restored.logs[0]!!.first().rir)
            assertEquals(vm.draftUnitsKg, restored.draftUnitsKg)
            assertEquals(2, restored.tokens.size)
        }
    }
}
