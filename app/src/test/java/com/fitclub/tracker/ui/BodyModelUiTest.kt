package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.Sex
import com.fitclub.tracker.ui.dashboard.ProfileScreen
import com.fitclub.tracker.ui.profile.MeasureScreen
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.theme.FitClubTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BodyModelUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FitApp>()

    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out = File("build/ui-preview/$name.png"); out.parentFile.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }

    private fun seed() = runBlocking {
        val today = LocalDate.now().toEpochDay()
        val rows = listOf(
            listOf(84.2f, 88f, 21f, 101f, 36f, 102f, 60f), listOf(83.5f, 86.5f, 20.5f, 101.5f, 36f, 101.5f, 60f),
            listOf(82.6f, 85f, 19.5f, 102f, 36.5f, 101f, 60.5f), listOf(81.4f, 82f, 18f, 104f, 37.5f, 100f, 61.5f)
        )
        rows.forEachIndexed { i, r ->
            app.container.fitness.addMeasurement(r[0], r[1], r[2], r[3], r[4], r[5], r[6], epochDay = today - 80 + i * 20L)
        }
    }

    private fun profile(light: Boolean, sex: Sex, onMeasure: (String?) -> Unit = {}) {
        app.container.profile.update(app.container.profile.settings.value.copy(sex = sex, unitsKg = true))
        rule.setContent { FitClubTheme(lightTheme = light) { Surface(color = Fc.Bg) { ProfileScreen({}, onMeasure) } } }
    }

    @Test fun emptyProfileInvitesTheFirstMeasurement() {
        profile(light = true, sex = Sex.MALE)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("girth-waist"))
        rule.onNodeWithTag("girth-waist").assertContentDescriptionContains("нет замера", substring = true)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Записать первый замер"))
        rule.onNodeWithText("Записать первый замер").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("girth-chest"))
        screenshot("body-model-empty")
    }

    @Test fun girthsShowLatestValueAndChangeSinceTheFirstDate() {
        seed()
        var opened: String? = "none"
        profile(light = true, sex = Sex.MALE) { opened = it }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("girth-waist"))
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Талия: 82 см", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("girth-waist").assertContentDescriptionContains("Талия: 82 см, −6 см", substring = true)
        rule.onNodeWithTag("girth-hips").assertContentDescriptionContains("Бёдра: 100 см, −2 см", substring = true)
        rule.onNodeWithTag("girth-chest").assertContentDescriptionContains("+3 см", substring = true)
        screenshot("body-model-male-light")
        rule.onNodeWithTag("girth-waist").performClick()
        rule.runOnIdle { assertEquals("waist", opened) }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Записать замер"))
        rule.onNodeWithText("Записать замер").performClick()
        rule.runOnIdle { assertNull(opened) }
    }

    @Test fun choosingAnotherBeforeDateRedrawsTheComparison() {
        seed()
        profile(light = false, sex = Sex.FEMALE)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("girth-waist"))
        rule.waitUntil(10000) { rule.onAllNodes(hasContentDescription("Талия: 82 см", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        screenshot("body-model-female-dark")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("measure-date-2"))
        rule.onNodeWithText("Было").performClick()
        rule.onNodeWithTag("measure-date-2").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("girth-waist"))
        rule.onNodeWithTag("girth-waist").assertContentDescriptionContains("−3 см", substring = true)
    }

    @Test fun measurementFlowPrefillsLastValuesSkipsAndSaves() {
        seed()
        app.container.profile.update(app.container.profile.settings.value.copy(sex = Sex.MALE, unitsKg = true))
        var closed = false
        rule.setContent { FitClubTheme(lightTheme = true) { Surface(color = Fc.Bg) { MeasureScreen(null) { closed = true } } } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 из 7").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("measure-value").assertTextEquals("81,4")
        rule.onNodeWithContentDescription("Уменьшить на 0,1 кг").performClick()
        rule.onNodeWithContentDescription("Уменьшить на 0,1 кг").performClick()
        rule.onNodeWithTag("measure-value").assertTextEquals("81,2")
        rule.onNodeWithText("−0,2 кг", substring = true).assertExists()
        rule.onNodeWithTag("measure-next").performClick()   // weight
        rule.onNodeWithText("Грудь").assertExists()
        rule.onNodeWithTag("measure-next").performClick()   // chest
        rule.onNodeWithTag("measure-value").performTextReplacement("81,5")
        screenshot("measure-sheet-waist")
        rule.onNodeWithTag("measure-next").performClick()   // waist
        rule.onNodeWithText("Пропустить").performClick()    // hips
        rule.onNodeWithTag("measure-next").performClick()   // arm
        rule.onNodeWithTag("measure-next").performClick()   // thigh
        rule.onNodeWithText("Сохранить замер").assertExists()
        rule.onNodeWithText("Пропустить").performClick()    // fat
        rule.waitUntil(10000) { rule.onAllNodesWithText("Замер записан").fetchSemanticsNodes().isNotEmpty() }
        screenshot("measure-sheet-done")
        val last = runBlocking { app.container.fitness.observeMeasurements().first().last() }
        assertEquals(LocalDate.now().toEpochDay(), last.epochDay)
        assertEquals(81.2f, last.weightKg!!, 0.001f)
        assertEquals(81.5f, last.waistCm!!, 0.001f)
        assertEquals(104f, last.chestCm!!, 0.001f)
        assertNull(last.hipsCm)
        assertNull(last.fatPercent)
        rule.onNodeWithTag("measure-done").performClick()
        rule.runOnIdle { assertTrue(closed) }
    }

    @Test fun openingFromAGirthStartsAtThatStep() {
        app.container.profile.update(app.container.profile.settings.value.copy(sex = Sex.FEMALE))
        rule.setContent { FitClubTheme(lightTheme = false) { Surface(color = Fc.Bg) { MeasureScreen("hips") {} } } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("4 из 7").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Бёдра").assertExists()
        rule.onNodeWithText("Это первый замер", substring = true).assertExists()
        screenshot("measure-sheet-first-dark")
    }
}
