package il.sidewalks.reporter

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.ui.SettingsScreen
import il.sidewalks.reporter.ui.theme.AppTheme
import il.sidewalks.reporter.i18n.UiPrefsLanguageStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import il.sidewalks.reporter.security.ReporterSettings

class ContactInputValidationTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, name).outputStream().use { compose.onNode(isRoot() and hasAnyDescendant(hasText("טלפון"))).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun invalidIdAndEmailBlockSaveAndPhoneStoresCanonical() {
        UiPrefsLanguageStore.of(InstrumentationRegistry.getInstrumentation().targetContext).set("he")
        var saved: ReporterSettings? = null
        compose.setContent { AppTheme { SettingsScreen(null, save = { value, _, clear -> saved = value; clear() }, back = {}, basicSetup = true) } }
        compose.onNodeWithText("שם פרטי").performTextInput("בדיקה1")
        compose.onNodeWithText("מספר זהות").performScrollTo().performTextReplacement("000000019")
        compose.onNodeWithText("שמור והמשך").performScrollTo().assertIsNotEnabled()
        screenshot("contact-invalid-synthetic.png")
        compose.onNodeWithText("מספר זהות").performScrollTo().performTextReplacement("000000018")
        compose.onNodeWithText("אימייל").performScrollTo().performTextReplacement("bad@@example.com")
        compose.onNodeWithText("שמור והמשך").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("אימייל").performScrollTo().performTextReplacement("synthetic@example.com")
        compose.onNodeWithText("טלפון").performScrollTo().performTextReplacement("050-123-4567")
        compose.onNodeWithText("אימייל").performScrollTo().performClick()
        compose.onNodeWithText("טלפון").performScrollTo()
        screenshot("contact-phone-formatted-synthetic.png")
        compose.onNodeWithText("שמור והמשך").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("בדיקה", saved?.firstName); assertEquals("+972501234567", saved?.phone); assertEquals("000000018", saved?.idNumber) }
    }
}
