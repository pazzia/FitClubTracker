package com.fitclub.tracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.repo.ProfileRepository
import com.fitclub.tracker.ui.dashboard.ProfileScreen
import com.fitclub.tracker.ui.theme.FitClubTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w390dp-h844dp", application = FitApp::class)
class RestSoundUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun restSoundIsOnByDefaultAndTheProfileSwitchPersists() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        assertTrue(app.container.profile.settings.value.restSound)
        rule.setContent { FitClubTheme { ProfileScreen({}) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("rest-sound"))
        rule.onNodeWithTag("rest-sound").assertIsOn()
        rule.onNodeWithText("Проверить звук").assertExists()
        rule.onNodeWithTag("rest-sound").performClick()
        rule.onNodeWithTag("rest-sound").assertIsOff()
        rule.onNodeWithText("Проверить звук").assertDoesNotExist()
        rule.runOnIdle {
            assertFalse(app.container.profile.settings.value.restSound)
            assertFalse(ProfileRepository(app).settings.value.restSound)
        }
        rule.onNodeWithTag("rest-sound").performClick()
        rule.runOnIdle { assertTrue(ProfileRepository(app).settings.value.restSound) }
    }
}
