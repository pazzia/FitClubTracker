package com.fitclub.tracker.ui.anatomy

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class AnatomyUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun groupTilesAndSearchOpenMuscles() {
        var group = ""
        var muscle = ""
        rule.setContent { FitClubTheme { AtlasHomeScreen({}, { group = it }, { muscle = it }) } }
        waitFor("121 мышца · 12 групп")
        rule.onNodeWithText("Грудь").performClick()
        assertEquals("chest", group)
        rule.onNodeWithContentDescription("Поиск мышцы").performTextInput("serratus")
        waitFor("Передняя зубчатая мышца")
        rule.onNodeWithText("Передняя зубчатая мышца").performClick()
        assertEquals("m004", muscle)
    }

    @Test fun everyMuscleOfTheGroupIsClickable() {
        val opened = mutableListOf<String>()
        rule.setContent { FitClubTheme(lightTheme = true) { AtlasGroupScreen("chest", {}, { opened += it }) } }
        waitFor("Подключичная мышца")
        listOf("Большая грудная мышца", "Малая грудная мышца", "Подключичная мышца", "Передняя зубчатая мышца").forEach {
            rule.onNodeWithTag("atlas-group-list").performScrollToNode(hasText(it))
            rule.onNodeWithText(it).performClick()
        }
        assertEquals(listOf("m001", "m002", "m003", "m004"), opened)
    }

    @Test fun muscleCardListsExercisesFilteredByPart() {
        var exercise = ""
        rule.setContent { FitClubTheme { AtlasMuscleScreen("m001", {}, {}, { exercise = it }) } }
        waitFor("11 упражнений ↓")
        rule.onNodeWithTag("atlas-muscle-list").performScrollToNode(hasText("Жим штанги лёжа"))
        rule.onNodeWithText("Жим штанги лёжа").performClick()
        assertEquals("bench", exercise)
        rule.onNodeWithTag("atlas-muscle-list").performScrollToNode(hasTestTag("atlas-exercises"))
        rule.onNodeWithText("Низ").performClick()
        rule.onNodeWithTag("atlas-muscle-list").performScrollToNode(hasText("Отжимания на брусьях"))
        assertTrue(rule.onAllNodesWithText("Жим штанги лёжа").fetchSemanticsNodes().isEmpty())
    }

    @Test fun stabilizerExplainsHowItIsTrained() {
        rule.setContent { FitClubTheme { AtlasMuscleScreen("m002", {}, {}, {}) } }
        waitFor("3 упражнения ↓")
        rule.onNodeWithTag("atlas-muscle-list").performScrollToNode(hasText("Изолированно", substring = true))
        rule.onAllNodesWithText("помогает").onFirst().assertExists()
    }
}
