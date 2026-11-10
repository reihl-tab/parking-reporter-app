package il.sidewalks.reporter

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.ui.SettingsScreen
import il.sidewalks.reporter.ui.theme.AppTheme
import il.sidewalks.reporter.i18n.AppCatalog
import il.sidewalks.reporter.i18n.UiPrefsLanguageStore
import il.sidewalks.reporter.security.ReporterSettings
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PassportLocalProfileTest {
    @get:Rule val compose = createComposeRule()
    @Test fun passportSavesLocalProfileWithoutAnIdAndNoInventedIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UiPrefsLanguageStore.of(context).set(requireNotNull(AppCatalog.get(InstrumentationRegistry.getInstrumentation().targetContext).defaultLanguage).code)
        var saved: ReporterSettings? = null
        compose.setContent { AppTheme { SettingsScreen(null, save = { value, _, clear -> saved = value; clear() }, back = {}, basicSetup = true) } }
        compose.onNodeWithText("ללא תעודת זהות ישראלית").performScrollTo()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("מספר דרכון").performScrollTo().performTextInput("SYNTHETIC-PASSPORT")
        compose.onNodeWithText("במצב דרכון נשלח בממשק", substring = true).assertDoesNotExist()
        File(context.filesDir, "passport-local-profile-synthetic.png").outputStream().use { compose.onNode(isRoot() and hasAnyDescendant(hasText("טלפון"))).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("שמור והמשך").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(saved!!.passportMode); assertEquals("SYNTHETIC-PASSPORT", saved?.passportNumber); assertEquals("", saved?.idNumber) }
    }
}
