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
import androidx.compose.ui.graphics.asImageBitmap
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
        "1234567", "", "sidewalk_parking", "תיאור בדיקה בלבד", setOf("address_needs_confirmation"))

    @Test fun noGpsAddressRequiresExplicitConfirmationAndPreservesOriginal() {
        val initial = draft().copy(evidence = draft().evidence.copy(latitude = null, longitude = null),
            address = "", confirmedLatitude = null, confirmedLongitude = null, unresolved = setOf("missing_gps", "address_needs_confirmation"))
        var saved: ReviewDraft? = null
        compose.setContent { AppTheme { ReviewScreen(initial, null, { saved = it }, {}, manualAddressLookup = { street, house ->
            assertEquals("רחוב בדיקה", street); assertEquals("12", house)
            il.sidewalks.reporter.municipal.StreetDirectory.ConfirmableAddress("רחוב בדיקה 12", 31.77, 35.21)
        }) } }
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("רחוב בירושלים").performScrollTo().performTextReplacement("רחוב בדיקה")
        compose.onNodeWithText("מספר בית").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("חפש כתובת מדויקת").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("אשר את כתובת האירוע").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("אשר את כתובת האירוע").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "no-gps-address-confirm-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("אשר את כתובת האירוע").performClick()
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("חפש הצעות כתובת לפי המיקום").assertDoesNotExist()
        compose.onNodeWithText("רחוב ומספר בית שאישרת").performScrollTo().performTextReplacement("רחוב בדיקה 14")
        compose.onNodeWithText("אשר זמן, מיקום וכתובת שבדקת").performScrollTo().performClick()
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(initial.evidence, saved?.evidence)
            assertEquals("רחוב בדיקה 14", saved?.address)
            assertNull(saved?.confirmedLatitude)
            assertNull(saved?.confirmedLongitude)
            assertNull(saved?.approvedDigest)
        }
    }

    @Test fun localTimeFieldKeepsOriginalSecondsAndRemovesOnlyApprovalNote() {
        val initial = draft().copy(address = "רחוב בדיקה 12", confirmedTime = "2026-01-01T12:00:47+02:00")
        var saved: ReviewDraft? = null
        compose.setContent { AppTheme { ReviewScreen(initial, null, { saved = it }, {}, addressLookup = { _, _ -> emptyList() }) } }
        compose.onNodeWithText("זמן: \u206601/01/2026 12:00\u2069").assertExists()
        compose.onNodeWithText("כל שינוי מבטל אישור", substring = true).assertDoesNotExist()
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("זמן הצילום (24 שעות)").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "review-local-time-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("אשר זמן, מיקום וכתובת שבדקת").performScrollTo().performClick()
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.evidence, saved?.evidence); assertEquals(initial.confirmedTime, saved?.confirmedTime); assertNull(saved?.approvedDigest) }
    }

    @Test fun plateEntryFormatsDigitsButStoresNoDashes() {
        val initial = draft().copy(address = "רחוב בדיקה 12")
        var saved: ReviewDraft? = null
        compose.setContent { AppTheme { ReviewScreen(initial, null, { saved = it }, {}) } }
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("מספר לוחית (ספרות בלבד)").performScrollTo().performTextReplacement("ab123-45-678")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "review-formatted-plate-synthetic.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("12345678", saved?.plate); assertNull(saved?.approvedDigest) }
    }

    @Test fun unambiguousOcrPrefillsWithoutSuggestionTapOrApproval() {
        val initial = draft().copy(address = "רחוב בדיקה 12", plate = "")
        var saved: ReviewDraft? = null
        val bitmap = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
        compose.setContent { AppTheme { ReviewScreen(initial, bitmap.asImageBitmap(), { saved = it }, {}, plateRecognizer = { listOf("1234567") }) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("לוחית: \u206612-345-67\u2069").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("1234567", saved?.plate); assertNull(saved?.approvedDigest) }
    }

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
        compose.onNodeWithText("קו רוחב").assertDoesNotExist()
        compose.onNodeWithText("קו אורך").assertDoesNotExist()
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
        compose.onNodeWithText("לא נמצא מיקום בתמונה.", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("קו רוחב").assertDoesNotExist()
        compose.onNodeWithText("קו אורך").assertDoesNotExist()
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(saved?.evidence?.latitude)
            assertNull(saved?.evidence?.longitude)
            assertNull(saved?.confirmedLatitude)
            assertNull(saved?.confirmedLongitude)
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
