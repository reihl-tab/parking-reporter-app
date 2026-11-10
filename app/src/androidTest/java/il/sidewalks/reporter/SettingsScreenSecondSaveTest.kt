package il.sidewalks.reporter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import il.sidewalks.reporter.security.ReporterSettings
import il.sidewalks.reporter.ui.SettingsScreen
import il.sidewalks.reporter.ui.mergeReporterSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Mirrors AppShell's settings save callback: after a save the screen clears itself
 *  and the stored profile is dropped, so the callback merges blank personal fields
 *  against a fresh read; a municipal credential field saves as typed only when the
 *  user touched it, and an untouched one keeps its stored value (owner 2026-11-09:
 *  clearing a credential field and saving removes its value, and a save that never
 *  touched it keeps it). */
class SettingsScreenSecondSaveTest {
    @get:Rule val compose = createComposeRule()

    private val stored = ReporterSettings("בדיקה", "מדווח", "0500000000", "000000018", "synthetic@example.invalid",
        municipalSubscriptionKey = "synthetic-subscription-key", municipalUsername = "synthetic-api-user", municipalPassword = "synthetic-api-pass")

    @Test
    fun secondSaveAfterPersonalEditKeepsCredentials() {
        val profile = mutableStateOf<ReporterSettings?>(stored)
        var saved: ReporterSettings? = null
        compose.setContent { MaterialTheme { SettingsScreen(profile.value, { s, touched, done -> saved = mergeReporterSettings(s, stored, touched); profile.value = null; done() }, {}) } }
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().performClick()
        compose.onNodeWithText("טלפון").performScrollTo().performTextReplacement("0500000001")
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("+972500000001", saved?.phone)
            // The user never touched the credential fields: they stay stored.
            assertEquals("synthetic-api-pass", saved?.municipalPassword)
            assertEquals("synthetic-subscription-key", saved?.municipalSubscriptionKey)
            assertEquals("synthetic-api-user", saved?.municipalUsername)
            assertEquals("בדיקה", saved?.firstName)
        }
    }

    @Test
    fun clearingAPrefilledCredentialOnFirstSaveRemovesIt() {
        val profile = mutableStateOf<ReporterSettings?>(stored)
        var saved: ReporterSettings? = null
        compose.setContent { MaterialTheme { SettingsScreen(profile.value, { s, touched, done -> saved = mergeReporterSettings(s, stored, touched); profile.value = null; done() }, {}) } }
        compose.onNodeWithText("חיבור 106 מתקדם").performScrollTo().performClick()
        compose.onNodeWithText("סיסמת API").performScrollTo().performTextReplacement("")
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().performClick()
        compose.runOnIdle {
            // The user cleared this field in full view: blank means cleared.
            assertEquals("", saved?.municipalPassword)
            assertEquals("synthetic-subscription-key", saved?.municipalSubscriptionKey)
            assertEquals("synthetic-api-user", saved?.municipalUsername)
        }
    }
}
