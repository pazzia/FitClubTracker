package com.fitclub.tracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.ui.dashboard.calendarDate
import com.fitclub.tracker.ui.navigation.AppRoot
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class PlanningUiTest {
    @get:Rule val rule = createComposeRule()
    private fun open() { rule.setContent { FitClubTheme { AppRoot() } } }
    private fun waitFor(text: String) = rule.waitUntil(15_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun dateShowsDayAndExplicitActionOpensIndependentPlanner() {
        open()
        val date = calendarDate(LocalDate.now().toEpochDay())
        rule.onNodeWithContentDescription("$date, запланировать тренировку").performClick()
        rule.onNodeWithText("План тренировки").assertDoesNotExist()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+ Запланировать тренировку"))
        rule.onNodeWithText("+ Запланировать тренировку").performClick()
        waitFor("План тренировки")
        rule.onNodeWithText("Выбрать программу →").performClick()
        waitFor("Выберите программу")
        waitFor("Фулбоди 3×")
        rule.onNodeWithText("Фулбоди 3×").performClick()
        waitFor("План тренировки")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить план"))
        rule.onNodeWithText("Сохранить план").performClick()
        waitFor("Изменить план")
        rule.onNodeWithText("Фулбоди 3× · день A").assertExists()
        rule.onNodeWithContentDescription("$date, тренировка запланирована").performClick()
        rule.onNodeWithText("Изменить план").performClick()
        waitFor("Тренировка")
        rule.onNodeWithText("Фулбоди 3× · день A").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Сохранить план"))
        rule.onNodeWithText("Сохранить план").assertIsEnabled()
    }
    @Test fun sexChosenInProfileControlsLibraryAndExercise() {
        open()
        rule.onNodeWithText("Профиль").performClick()
        waitFor("Женский")
        rule.onNodeWithText("Женский").performClick()
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        rule.runOnIdle { assertEquals(Sex.FEMALE, app.container.profile.settings.value.sex) }
        rule.onNodeWithText("Справочник").performClick()
        rule.onNodeWithContentDescription("Грудь, женский атлас").performClick()
        waitFor("${ExerciseSeed.all.count { it.group == MuscleGroup.CHEST }} упражнений")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Жим штанги лёжа"))
        rule.onNodeWithText("Жим штанги лёжа").performClick()
        rule.onAllNodesWithContentDescription("Грудь, женский атлас").onFirst().assertExists()
        rule.onAllNodesWithText("Мужская").assertCountEquals(0)
        rule.onAllNodesWithText("Женская").assertCountEquals(0)
    }
}


