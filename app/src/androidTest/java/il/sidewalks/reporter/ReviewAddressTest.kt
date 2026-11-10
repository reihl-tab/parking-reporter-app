package il.sidewalks.reporter

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.evidence.AddressSuggestion
import il.sidewalks.reporter.ui.ReviewScreen
import il.sidewalks.reporter.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReviewAddressTest {
    @get:Rule val compose = createComposeRule()
    private fun draft() = ReviewDraft(
        Evidence("address-fixture", "a".repeat(64), "2026-01-01T12:00:00+02:00", 31.77, 35.21),
        "1234567", "", "sidewalk_parking", "תיאור בדיקה בלבד", setOf("address_not_confirmed"))

    @Test fun gpsSuggestsStreetHouseButNeverApprovesOrChangesEvidence() {
        val initial = draft()
        var saved: ReviewDraft? = null
        compose.setContent { AppTheme { ReviewScreen(initial, null, { saved = it }, {}, addressLookup = { lat, lon ->
            assertEquals(31.77, lat, 0.0); assertEquals(35.21, lon, 0.0)
            listOf(AddressSuggestion("רחוב בדיקה", "12", "provider full address should not be used"))
        }) } }
        compose.onNodeWithText("כן, חפש כתובת").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("הצעה בלבד: \u2068רחוב בדיקה 12\u2069").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("הצעה בלבד: \u2068רחוב בדיקה 12\u2069").performScrollTo().assertExists()
        compose.onNodeWithText("קו רוחב").assertDoesNotExist()
        compose.onNodeWithText("קו אורך").assertDoesNotExist()
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("הצעה בלבד: \u2068רחוב בדיקה 12\u2069").performScrollTo().performClick()
        compose.onNodeWithText("קו רוחב").assertDoesNotExist()
        compose.onNodeWithText("קו אורך").assertDoesNotExist()
        compose.onNodeWithText("רחוב ומספר בית שאישרת").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "review-resolved-address-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("סיום עריכה").performScrollTo().performClick()
        File(context.filesDir, "review-address-summary-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("רחוב בדיקה 12", saved?.address)
            assertEquals(initial.evidence, saved?.evidence)
            assertEquals(initial.confirmedLatitude, saved?.confirmedLatitude)
            assertEquals(initial.confirmedLongitude, saved?.confirmedLongitude)
            assertNull(saved?.approvedDigest)
        }
        compose.onNodeWithText("תיקון מיקום מתקדם").performScrollTo().performClick()
        compose.onNodeWithText("קו רוחב").performScrollTo().assertExists()
        compose.onNodeWithText("קו אורך").performScrollTo().assertExists()
    }

    @Test fun lookupFailureLeavesManualAddressAndNoInventedNumber() {
        compose.setContent { AppTheme { ReviewScreen(draft(), null, {}, {}, addressLookup = { _, _ -> error("offline") }) } }
        compose.onNodeWithText("כן, חפש כתובת").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("אין הצעות, הזן כתובת שבדקת").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("אין הצעות, הזן כתובת שבדקת").performScrollTo().assertExists()
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("רחוב ומספר בית שאישרת").performScrollTo().assertExists()
        compose.onNodeWithText("קו רוחב").assertDoesNotExist()
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
    }
    @Test fun decliningProviderConsentDoesNotLookup() {
        var calls = 0
        compose.setContent { AppTheme { ReviewScreen(draft(), null, {}, {}, addressLookup = { _, _ -> calls += 1; emptyList() }) } }
        compose.onNodeWithText("לא עכשיו").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("קו רוחב").assertDoesNotExist()
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
    }
    @Test fun missingGpsHasVisibleCorrectionRouteAndNeverInventsCoordinates() {
        val initial = draft().copy(evidence = draft().evidence.copy(latitude = null, longitude = null), confirmedLatitude = null, confirmedLongitude = null)
        var saved: ReviewDraft? = null
        compose.setContent { AppTheme { ReviewScreen(initial, null, { saved = it }, {}) } }
        compose.onNodeWithText("השלם מיקום חסר").performScrollTo().performClick()
        compose.onNodeWithText("קו רוחב").performScrollTo().performTextReplacement("31.77")
        compose.onNodeWithText("קו אורך").performScrollTo().performTextReplacement("35.21")
        compose.onNodeWithText("אשר זמן, מיקום וכתובת שבדקת").performScrollTo().performClick()
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(saved?.evidence?.latitude)
            assertNull(saved?.evidence?.longitude)
            assertEquals(31.77, saved!!.confirmedLatitude!!, 0.0)
            assertEquals(35.21, saved!!.confirmedLongitude!!, 0.0)
            assertNull(saved?.approvedDigest)
        }
    }
    @Test fun decliningConsentSurvivesSavedStateRestoration() {
        var calls = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AppTheme { ReviewScreen(draft(), null, {}, {}, addressLookup = { _, _ -> calls += 1; emptyList() }) } }
        compose.onNodeWithText("לא עכשיו").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("למצוא רחוב ומספר בית?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
