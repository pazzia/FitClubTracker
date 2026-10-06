package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.FitClubTheme
import com.fitclub.tracker.ui.dashboard.calendarDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.model.RepRange
import com.fitclub.tracker.data.repo.ItemDraft
import com.fitclub.tracker.data.repo.ScheduledDraft
import com.fitclub.tracker.ui.workout.WorkoutScreen
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Preview66UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(text: String) = rule.waitUntil(15_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val globalClass = Class.forName("android.view.WindowManagerGlobal")
            val global = globalClass.getDeclaredMethod("getInstance").invoke(null)
            val viewsField = globalClass.getDeclaredField("mViews").apply { isAccessible = true }
            val windows = viewsField.get(global) as List<*>
            val view = (windows.lastOrNull() as? android.view.View) ?: rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/ui-preview/$name.png")
            output.parentFile.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun workoutShowsOriginalTechniqueCarouselAndResetsForNextExercise() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val id = runBlocking { app.container.schedule.save(ScheduledDraft(epochDay = 22001,
            items = listOf(ItemDraft("bench", 1, RepRange.R8_12, 0), ItemDraft("bbrow", 1, RepRange.R8_12, 0)))) }
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) {
            WorkoutScreen(id, onClose = {}, onTechnique = {})
        } }
        waitFor("Выберите любое упражнение. Удерживайте ≡, чтобы изменить порядок.")
        rule.onNodeWithText("Жим штанги лёжа").performClick()
        waitFor("УПРАЖНЕНИЕ 1 ИЗ 2")
        rule.onNodeWithTag("exercise-photo-pager").assertIsDisplayed()
        screenshot("workout-technique-1")
        rule.onNodeWithText("Фото 2").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2 / 2 · Листайте свайпом").assertExists()
        screenshot("workout-technique-2")
        rule.onNodeWithTag("next-exercise").performClick()
        waitFor("УПРАЖНЕНИЕ 2 ИЗ 2")
        rule.onNodeWithText("1 / 2 · Листайте свайпом").assertExists()
    }
    @Test fun longPressSupportsSingleAndMultipleDeletionWithCancel() {
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) { AppRoot() } }
        rule.onNodeWithText("Программы").performClick()
        waitFor("Фулбоди 3×")
        rule.onNodeWithText("Фулбоди 3×").performTouchInput { longClick() }
        waitFor("Выбрано: 1")
        rule.onNodeWithText("Удалить").performClick()
        waitFor("Удалить программу?")
        screenshot("single-delete")
        rule.onAllNodesWithText("Отмена").onLast().performClick()
        rule.onNodeWithText("Фулбоди 3×").assertExists()
        rule.onNodeWithText("Сила 5×5").performClick()
        waitFor("Выбрано: 2")
        screenshot("selected-programs")
        rule.onNodeWithText("Удалить").performClick()
        waitFor("Удалить выбранные программы?")
        screenshot("multiple-delete")
        rule.onAllNodesWithText("Удалить").onLast().performClick()
        waitFor("+ Создать программу")
        rule.onAllNodesWithText("Фулбоди 3×").assertCountEquals(0)
        rule.onAllNodesWithText("Сила 5×5").assertCountEquals(0)
        rule.onNodeWithText("Летний рельеф").assertExists()
    }
    @Test fun editorPrioritizesExercisesAndKeepsAppearanceCollapsed() {
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) { AppRoot() } }
        rule.onNodeWithText("Программы").performClick()
        waitFor("+ Создать программу")
        rule.onNodeWithText("+ Создать программу").performClick()
        waitFor("Название программы")
        rule.onNodeWithText("+  Добавить упражнение").assertIsDisplayed()
        rule.onAllNodesWithText("Цвет").assertCountEquals(0)
        screenshot("new-program")
        rule.onNodeWithText("+  Добавить упражнение").performClick()
        waitFor("Выберите группу мышц")
        rule.onNodeWithText("Выберите группу мышц").assertExists()
    }
    @Test fun plannerShowsPrimaryProgramChoiceAndSecondaryManualAdd() {
        rule.setContent { FitClubTheme(lightTheme = true, accent = 0xFF236B9D) { AppRoot() } }
        val date = calendarDate(LocalDate.now().toEpochDay())
        rule.onNodeWithContentDescription("$date, запланировать тренировку").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+ Запланировать тренировку"))
        rule.onNodeWithText("+ Запланировать тренировку").performClick()
        waitFor("Выбрать программу →")
        screenshot("training-plan")
        rule.onNodeWithText("Выбрать программу →").performClick()
        waitFor("Выберите программу")
        waitFor("Фулбоди 3×")
        rule.onNodeWithText("Фулбоди 3×").performClick()
        waitFor("План тренировки")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+ Добавить упражнение"))
        rule.onNodeWithText("+ Добавить упражнение").performClick()
        waitFor("Выберите группу мышц")
    }
}

