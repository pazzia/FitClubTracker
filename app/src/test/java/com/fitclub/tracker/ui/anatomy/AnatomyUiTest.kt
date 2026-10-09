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
@Config(sdk = [28], qualifiers = "w375dp-h812dp", application = FitApp::class)
class AnatomyUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun searchOpensTheCorrectMuscle() {
        var selected = ""
        rule.setContent { FitClubTheme { AnatomyCatalogScreen({}, { selected = it }) } }
        rule.waitUntil(15000) { rule.onAllNodesWithText("274 из 274 записей").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("бицепс")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Двуглавая мышца плеча"))
        rule.onNodeWithText("Двуглавая мышца плеча").performClick()
        assertEquals("TA2:2464", selected)
    }
    @Test fun functionsAndExerciseNavigationAreAvailable() {
        var exercises = false
        rule.setContent { FitClubTheme(lightTheme = true) { AnatomyDetailScreen("TA2:2464", {}, {}, { exercises = true }) } }
        rule.waitUntil(15000) { rule.onAllNodesWithText("Функции").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Функции").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("За что отвечает"))
        rule.onNodeWithText("За что отвечает").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        rule.onNodeWithText("В фитнесе").performClick()
        rule.onNodeWithText("Упражнения на бицепс").performClick()
        assertTrue(exercises)
    }
}
