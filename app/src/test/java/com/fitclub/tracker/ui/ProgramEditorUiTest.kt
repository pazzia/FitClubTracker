package com.fitclub.tracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class ProgramEditorUiTest {
    @get:Rule val rule = createComposeRule()
    private fun waitFor(text: String) = rule.waitUntil(15_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun editor() {
        rule.setContent { FitClubTheme { AppRoot() } }
        rule.onNodeWithText("Программы").performClick()
        waitFor("+ Новая")
        rule.onNodeWithText("+ Новая").performClick()
        waitFor("Название программы")
    }
    @Test fun bulkPickerAndCommentSurviveProgramSaveAndReopen() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        app.container.profile.update(app.container.profile.settings.value.copy(sex = Sex.FEMALE))
        editor()
        rule.onNodeWithText("Название программы").performTextReplacement("Личный план")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+  Добавить упражнение"))
        rule.onNodeWithText("+  Добавить упражнение").performClick()
        waitFor("Добавить упражнения")
        rule.onNodeWithTag("exercise-search").performTextReplacement("штанг лежа")
        waitFor("Жим штанги лёжа")
        rule.onNodeWithTag("picker-exercise-bench").performClick()
        rule.onNodeWithTag("exercise-search").performTextReplacement("жим гант")
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Поиск…").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("picker-list").performScrollToNode(hasTestTag("picker-exercise-dbbench"))
        rule.onNodeWithTag("picker-exercise-dbbench").performClick()
        rule.onNodeWithText("Выбрано: 2").assertExists()
        rule.onNodeWithTag("picker-add").performClick()
        rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("exercise-comment-bench"))
        rule.onNodeWithContentDescription("Жим штанги лёжа — миниатюра техники").assertExists()
        rule.onNodeWithTag("exercise-comment-bench").performTextInput("Без рывков")
        rule.onAllNodesWithText("Соответствие цели «Масса»").assertCountEquals(0)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Разбор программы +"))
        rule.onNodeWithText("Разбор программы +").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("program-fit-conclusion"))
        rule.onNodeWithText("Итог для цели «Масса»").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить программу"))
        rule.onNodeWithText("Сохранить программу").performClick()
        waitFor("+ Новая")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Личный план"))
        rule.onNodeWithText("Личный план").performClick()
        waitFor("Название программы")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("exercise-comment-bench"))
        rule.onNodeWithTag("exercise-comment-bench").assertTextContains("Без рывков")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("exercise-comment-dbbench"))
        rule.onNodeWithTag("exercise-comment-dbbench").assertExists()
    }
    @Test fun cameraButtonOffersPhotoSourcesAndGalleryRequiresConsent() {
        editor()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Оформление программы +"))
        rule.onNodeWithText("Оформление программы +").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Фото обложки"))
        rule.onNodeWithContentDescription("Фото обложки").performClick()
        rule.onNodeWithText("Сделать фото").assertExists()
        rule.onNodeWithText("Выбрать из галереи").performClick()
        rule.onNodeWithText("Разрешить использовать фото?").assertExists()
        rule.onNodeWithText("Отмена").performClick()
        rule.onAllNodesWithText("Разрешить использовать фото?").assertCountEquals(0)
    }
    @Test fun emptyPickerBackPreservesEditor() {
        editor()
        rule.onNodeWithText("Название программы").performTextReplacement("Мой новый план")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+  Добавить упражнение"))
        rule.onNodeWithText("+  Добавить упражнение").performClick()
        waitFor("Добавить упражнения")
        rule.onNodeWithText("К программе").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Название программы"))
        rule.onNodeWithText("Название программы").assertTextContains("Мой новый план")
    }
}
