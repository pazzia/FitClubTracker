package com.fitclub.tracker.ui

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import com.fitclub.tracker.data.model.*
import com.fitclub.tracker.ui.components.*
import com.fitclub.tracker.ui.library.ExerciseMediaCard
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = Application::class)
class ExerciseMediaUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun photosSwipeBothWaysAndButtonsUseSameCarousel() {
        rule.setContent { FitClubTheme {
            ExerciseMediaCard("bench", "Жим", FreeExerciseCatalog.media.getValue("bench"))
        } }
        rule.onNodeWithText("1 / 2 · Листайте свайпом").assertExists()
        rule.waitUntil(10_000) {
            val bounds = rule.onNodeWithContentDescription("Жим — фото 1").fetchSemanticsNode().boundsInRoot
            kotlin.math.abs(bounds.width / bounds.height - 850f / 567f) < .03f
        }
        rule.onNodeWithTag("exercise-photo-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("2 / 2 · Листайте свайпом").assertExists()
        rule.onNodeWithTag("exercise-photo-pager").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText("1 / 2 · Листайте свайпом").assertExists()
        rule.onNodeWithText("Фото 2").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2 / 2 · Листайте свайпом").assertExists()
        rule.onNodeWithText("Free Exercise DB", substring = true).assertDoesNotExist()
    }

    @Test fun exerciseThumbnailDescribesTheSpecificExercise() {
        val exercise = ExerciseSeed.all.first { it.id == "bench" }
        rule.setContent { FitClubTheme { ExerciseThumbnail(exercise, Modifier.size(64.dp)) } }
        rule.onNodeWithContentDescription("${exercise.name} — миниатюра техники").assertExists()
        assertTrue(FreeExerciseCatalog.media.getValue(exercise.id).images.first().contains("Barbell_Bench_Press"))
    }

    @Test fun figureHighlightsOnlyExerciseMetadataAndSupportsEveryRegion() {
        val (primary, auxiliary) = highlightedMuscles(listOf(Muscle.CHEST), listOf(Muscle.TRICEPS, Muscle.CHEST))
        assertEquals(setOf(Muscle.CHEST), primary)
        assertEquals(setOf(Muscle.TRICEPS), auxiliary)
        for (sex in Sex.entries) {
            val supported = BodyView.entries.flatMap { AthleteMusclePaths.forFigure(sex, it).parts.keys }.toSet()
            assertEquals(Muscle.entries.toSet(), supported)
        }
        rule.setContent { FitClubTheme {
            ExerciseMuscleMap(Sex.MALE, listOf(Muscle.CHEST), listOf(Muscle.TRICEPS))
        } }
        rule.onNodeWithContentDescription("Тело спереди. Основные: грудные. Дополнительные: ").assertExists()
        rule.onNodeWithContentDescription("Тело сзади. Основные: . Дополнительные: трицепс").assertExists()
        rule.onNodeWithText("● Основные: грудные").assertExists()
        rule.onNodeWithText("● Дополнительные: трицепс").assertExists()
        rule.onNodeWithText("широчайшие", substring = true).assertDoesNotExist()
    }
}
