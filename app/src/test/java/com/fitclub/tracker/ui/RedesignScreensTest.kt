package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.*
import com.fitclub.tracker.ui.anatomy.AtlasHomeScreen
import com.fitclub.tracker.ui.dashboard.TrainingPlannerScreen
import com.fitclub.tracker.ui.library.ExerciseDetailScreen
import com.fitclub.tracker.ui.library.LibraryGroupScreen
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.profile.BodyScreen
import com.fitclub.tracker.ui.programs.ProgramEditorScreen
import com.fitclub.tracker.ui.startup.OnboardingScreen
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.workout.WorkoutResultScreen
import com.fitclub.tracker.ui.workout.WorkoutScreen
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renders the remaining screens in the new design for review (dark by default, plus light). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedesignScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FitApp>()

    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Загрузка…").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out = File("build/ui-preview/redesign-$name.png"); out.parentFile.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }

    private fun show(light: Boolean = false, content: @Composable () -> Unit) =
        rule.setContent { FitClubTheme(lightTheme = light) { Surface(color = Fc.Bg) { content() } } }

    @Test fun exerciseDetail() {
        show { ExerciseDetailScreen("bench", {}, {}, {}) }
        waitFor("Жим штанги лёжа")
        shot("exercise-detail")
    }

    @Test fun libraryGroup() {
        show { LibraryGroupScreen(MuscleGroup.CHEST, {}, {}) }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("library-group-list").fetchSemanticsNodes().isNotEmpty() }
        shot("library-group")
    }

    @Test fun programEditor() {
        show { ProgramEditorScreen(0L, {}) }
        waitFor("Сохранить программу")
        shot("program-editor")
    }

    @Test fun planner() {
        show { TrainingPlannerScreen(onChooseProgram = {}, scheduleId = 0L, selectedProgramId = 0L, onSelectionConsumed = {},
            epochDay = LocalDate.now().toEpochDay(), onClose = {}, onStart = {}) }
        waitFor("Сохранить план")
        shot("planner")
    }

    @Test fun atlas() {
        show { AtlasHomeScreen({}, {}, {}) }
        rule.waitForIdle()
        shot("atlas")
    }

    @Test fun body() {
        show { BodyScreen({}, {}) }
        rule.waitForIdle()
        shot("body")
    }

    @Test fun onboarding() {
        show { OnboardingScreen(app) {} }
        rule.waitForIdle()
        shot("onboarding")
    }

    @Test fun restTimer() {
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22000, title = "Грудь и спина",
            items = listOf(ItemDraft("bench", 3, RepRange.R8_12, 90), ItemDraft("bbrow", 3, RepRange.R8_12, 90)))) }
        show { WorkoutScreen(id, {}, {}) }
        waitFor("Упражнение 1 из 2")
        rule.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("weight-0"))
        rule.onNodeWithTag("weight-0").performTextReplacement("60")
        rule.onNodeWithTag("done-0").performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasContentDescription("Открыть таймер", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("workout-rest-bar")
        rule.onNode(hasContentDescription("Открыть таймер", substring = true)).performClick()
        waitFor("Готов к подходу")
        shot("workout-rest")
    }

    @Test fun todayLight() {
        runBlocking {
            app.container.schedule.save(ScheduledDraft(epochDay = LocalDate.now().toEpochDay(), title = "Фулбоди 3× · день A", startMinute = 1080,
                items = listOf(ItemDraft("bench", 3, RepRange.R8_12, 90), ItemDraft("bbrow", 3, RepRange.R8_12, 90))))
        }
        rule.setContent { FitClubTheme(lightTheme = true) { AppRoot() } }
        waitFor("Начать тренировку")
        shot("today-light")
    }

    @Test fun todayWithoutProgram() {
        rule.setContent { FitClubTheme { AppRoot() } }
        waitFor("Выбрать программу")
        shot("today-empty")
    }

    @Test fun completionLight() {
        val id = runBlocking {
            app.container.fitness.finishWorkout(null, null, System.currentTimeMillis() - 30 * 60_000L,
                listOf(CompletedSet("bench", 0, 60f, 8), CompletedSet("bbrow", 0, 50f, 10, exercisePosition = 1)), title = "Грудь и спина")
        }
        rule.setContent { FitClubTheme(lightTheme = true) { Surface(color = Fc.Bg) { WorkoutResultScreen(id, {}, celebrate = true) } } }
        rule.waitUntil(15_000) { runCatching { rule.onNodeWithTag("result-progress").assertIsEnabled() }.isSuccess }
        shot("complete-light")
    }

    @Test fun resultFromHistory() {
        val id = runBlocking {
            app.container.fitness.finishWorkout(null, null, System.currentTimeMillis() - 30 * 60_000L,
                listOf(CompletedSet("bench", 0, 60f, 8), CompletedSet("bench", 1, 65f, 6)), title = "Грудь и спина")
        }
        show { WorkoutResultScreen(id, {}) }
        waitFor("Редактировать результат")
        shot("result-history")
    }
}
