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
        waitFor("+  Создать программу")
        rule.onNodeWithText("+  Создать программу").performClick()
        waitFor("Название программы")
    }
    @Test fun categoriesComeFirstAndCommentSurvivesProgramSaveAndReopen() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        app.container.profile.update(app.container.profile.settings.value.copy(sex = Sex.FEMALE))
        editor()
        rule.onNodeWithText("Название программы").performTextReplacement("Личный план")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+  Добавить упражнение"))
        rule.onNodeWithText("+  Добавить упражнение").performClick()
        waitFor("Выберите группу мышц")
        rule.onAllNodesWithText("Жим штанги лёжа").assertCountEquals(0)
        rule.onNodeWithContentDescription("Грудь, женский атлас").performClick()
        waitFor("${ExerciseSeed.all.count { it.group == MuscleGroup.CHEST }} упражнений")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Жим штанги лёжа"))
        rule.onNodeWithText("Жим штанги лёжа").performClick()
        rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("exercise-comment-bench"))
        rule.onNodeWithContentDescription("Жим штанги лёжа — миниатюра техники").assertExists()
        rule.onNodeWithTag("exercise-comment-bench").performTextInput("Без рывков")
        rule.onAllNodesWithText("Соответствие цели «Масса»").assertCountEquals(0)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("program-fit-conclusion"))
        rule.onNodeWithText("Итог для цели «Масса»").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить программу"))
        rule.onNodeWithText("Сохранить программу").performClick()
        waitFor("+  Создать программу")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Личный план"))
        rule.onNodeWithText("Личный план").performClick()
        waitFor("Название программы")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("exercise-comment-bench"))
        rule.onNodeWithTag("exercise-comment-bench").assertTextContains("Без рывков")
    }
    @Test fun cameraButtonOffersPhotoSourcesAndGalleryRequiresConsent() {
        editor()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Фото обложки"))
        rule.onNodeWithContentDescription("Фото обложки").performClick()
        rule.onNodeWithText("Сделать фото").assertExists()
        rule.onNodeWithText("Выбрать из галереи").performClick()
        rule.onNodeWithText("Разрешить использовать фото?").assertExists()
        rule.onNodeWithText("Отмена").performClick()
        rule.onAllNodesWithText("Разрешить использовать фото?").assertCountEquals(0)
    }
    @Test fun categoryBackReturnsToGridWithoutDiscardingEditor() {
        editor()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+  Добавить упражнение"))
        rule.onNodeWithText("+  Добавить упражнение").performClick()
        waitFor("Выберите группу мышц")
        val sex = ApplicationProvider.getApplicationContext<FitApp>().container.profile.settings.value.sex.title.lowercase()
        rule.onNodeWithContentDescription("Грудь, $sex атлас").performClick()
        waitFor("Группы мышц")
        rule.onNodeWithText("Группы мышц").performClick()
        rule.onNodeWithText("Выберите группу мышц").assertExists()
        rule.onNodeWithText("К программе").performClick()
        rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Название программы"))
        rule.onNodeWithText("Название программы").assertExists()
    }
}
