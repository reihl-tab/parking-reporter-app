package il.sidewalks.reporter

import il.sidewalks.reporter.ui.theme.AppTheme
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import il.sidewalks.reporter.security.ReporterSettings
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.i18n.AppCatalog
import il.sidewalks.reporter.i18n.UiPrefsLanguageStore
import il.sidewalks.reporter.ui.AppShell
import il.sidewalks.reporter.ui.FirstRunStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Issue #187: the one-time first-run welcome, through the real AppShell wiring. */
class FirstRunScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clearFirstRunFlag() {
        context.getSharedPreferences(FirstRunStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().remove(FirstRunStore.KEY_FIRST_RUN_SEEN).commit()
    }

    @Before fun pinHebrewAndClearFlag() {
        UiPrefsLanguageStore.of(context).set(AppCatalog.defaultCode(context))
        clearFirstRunFlag()
    }

    @After fun restore() {
        il.sidewalks.reporter.security.SettingsProfileStore(context).clear()
        context.getSharedPreferences(UiPrefsLanguageStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putString(UiPrefsLanguageStore.KEY_LANGUAGE, AppCatalog.defaultCode(context)).commit()
        clearFirstRunFlag()
    }

    @Test fun firstRunShowsWelcomeAndStartEntersTheShell() {
        val done = androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { AppTheme {
            AppShell(
                authenticate = { _, _, action -> action() },
                requestCamera = {},
                readSettings = { null },
                saveSettings = {},
                showFirstRun = !done.value,
                onFirstRunDone = { done.value = true },
            )
        } }
        compose.onNodeWithText("ברוכים הבאים למדרכות פנויות").assertExists()
        compose.onNodeWithText("מתחילים").assertExists()
        File(context.filesDir, "first-run-welcome-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        // The plain shell is not underneath: no navigation bar yet.
        compose.onNodeWithText("הגדרות").assertDoesNotExist()
        compose.onNodeWithText("מתחילים").performClick()
        compose.onNodeWithText("הגדרות בסיסיות").assertExists()
        compose.onNodeWithText("ברוכים הבאים למדרכות פנויות").assertDoesNotExist()
        compose.onNodeWithText("מפתח מנוי לעירייה").assertDoesNotExist()
        compose.onNodeWithText("Google ML Kit", substring = true).assertDoesNotExist()
        File(context.filesDir, "first-run-basic-setup-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("אחר כך").performScrollTo().performClick()
        compose.onNodeWithText("הגדרות").assertExists()
        compose.runOnIdle { assertEquals(true, done.value) }
    }

    @Test fun setupSaveUsesFreshAuthenticationPreservesApiAndDisposesForm() {
        val done = androidx.compose.runtime.mutableStateOf(false)
        var reads = 0
        var authentications = 0
        var saved: ReporterSettings? = null
        val existing = ReporterSettings("", "", "", "", "", "synthetic-key", "synthetic-user", "synthetic-pass")
        compose.setContent { AppTheme {
            AppShell(authenticate = { _, _, action -> authentications++; action() }, requestCamera = {},
                readSettings = { reads++; existing }, saveSettings = { saved = it },
                showFirstRun = !done.value, onFirstRunDone = { done.value = true })
        } }
        compose.onNodeWithText("מתחילים").performClick()
        compose.runOnIdle { assertEquals(0, reads); assertEquals(0, authentications) }
        compose.onNodeWithText("שם פרטי").performScrollTo().performTextInput("בדיקה")
        compose.onNodeWithText("שמור והמשך").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, authentications)
            assertEquals(1, reads)
            assertEquals("בדיקה", saved?.firstName)
            assertEquals("synthetic-key", saved?.municipalSubscriptionKey)
            assertEquals("synthetic-user", saved?.municipalUsername)
            assertEquals("synthetic-pass", saved?.municipalPassword)
            assertEquals(true, done.value)
        }
        compose.onNodeWithText("הגדרות בסיסיות").assertDoesNotExist()
    }

    @Test fun cancelledAuthenticationDoesNotSaveOrCompleteSetup() {
        var reads = 0
        var saves = 0
        var done = false
        compose.setContent { AppTheme {
            AppShell(authenticate = { _, _, _ -> }, requestCamera = {},
                readSettings = { reads++; null }, saveSettings = { saves++ },
                showFirstRun = true, onFirstRunDone = { done = true })
        } }
        compose.onNodeWithText("מתחילים").performClick()
        compose.onNodeWithText("שם פרטי").performScrollTo().performTextInput("בדיקה")
        compose.onNodeWithText("שמור והמשך").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, reads); assertEquals(0, saves); assertFalse(done) }
        compose.onNodeWithText("הגדרות בסיסיות").assertExists()
    }

    @Test fun failedSaveKeepsSetupOpenAndAllowsRetry() {
        var saves = 0
        var done = false
        compose.setContent { AppTheme {
            AppShell(authenticate = { _, _, action -> action() }, requestCamera = {},
                readSettings = { null }, saveSettings = { saves++; error("synthetic failure") },
                showFirstRun = true, onFirstRunDone = { done = true })
        } }
        compose.onNodeWithText("מתחילים").performClick()
        compose.onNodeWithText("שם פרטי").performScrollTo().performTextInput("בדיקה")
        compose.onNodeWithText("שמור והמשך").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves); assertFalse(done) }
        compose.onNodeWithText("שמור והמשך").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, saves); assertFalse(done) }
        compose.onNodeWithText("הגדרות בסיסיות").assertExists()
    }

    @Test fun storePersistsTheSeenFlag() {
        val store = FirstRunStore(context)
        assertFalse(store.seen())
        store.markSeen()
        assertEquals(true, store.seen())
        assertEquals(true, FirstRunStore(context).seen())
    }
}
