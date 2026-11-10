package il.sidewalks.reporter

import il.sidewalks.reporter.ui.theme.AppTheme
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
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
        UiPrefsLanguageStore.of(context).set("he")
        clearFirstRunFlag()
    }

    @After fun restore() {
        context.getSharedPreferences(UiPrefsLanguageStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putString(UiPrefsLanguageStore.KEY_LANGUAGE, "he").commit()
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
        compose.onNodeWithText("הגדרות").assertExists()
        compose.onNodeWithText("ברוכים הבאים למדרכות פנויות").assertDoesNotExist()
    }

    @Test fun storePersistsTheSeenFlag() {
        val store = FirstRunStore(context)
        assertFalse(store.seen())
        store.markSeen()
        assertEquals(true, store.seen())
        assertEquals(true, FirstRunStore(context).seen())
    }
}
