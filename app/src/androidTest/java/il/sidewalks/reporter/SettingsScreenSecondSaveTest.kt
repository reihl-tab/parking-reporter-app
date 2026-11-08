package il.sidewalks.reporter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import il.sidewalks.reporter.security.ReporterSettings
import il.sidewalks.reporter.ui.SettingsScreen
import il.sidewalks.reporter.ui.mergeReporterSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Mirrors AppShell's settings save callback: after a save the screen clears itself
 *  and the stored profile is dropped, so the callback merges blanks against a fresh read. */
class SettingsScreenSecondSaveTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun secondSaveAfterClearKeepsStoredValues() {
        val stored = ReporterSettings("בדיקה", "מדווח", "0500000000", "000000000", "synthetic@example.invalid",
            municipalSubscriptionKey = "synthetic-subscription-key", municipalUsername = "synthetic-api-user", municipalPassword = "synthetic-api-pass")
        val profile = mutableStateOf<ReporterSettings?>(stored)
        var saved: ReporterSettings? = null
        compose.setContent { MaterialTheme { SettingsScreen(profile.value, { s, done -> saved = mergeReporterSettings(s, stored); profile.value = null; done() }, {}) } }
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().performClick()
        compose.onNodeWithText("טלפון").performScrollTo().performTextInput("0500000001")
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("0500000001", saved?.phone)
            assertEquals("synthetic-api-pass", saved?.municipalPassword)
            assertEquals("בדיקה", saved?.firstName)
        }
    }
}
