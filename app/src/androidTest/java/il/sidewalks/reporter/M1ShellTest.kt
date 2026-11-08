package il.sidewalks.reporter

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.ledger.ReportDatabase
import il.sidewalks.reporter.ledger.ReportRecord
import il.sidewalks.reporter.security.ReporterSettings
import il.sidewalks.reporter.security.SecureSettings
import il.sidewalks.reporter.ui.ReviewScreen
import il.sidewalks.reporter.ui.SettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class M1ShellTest {
    @get:Rule val compose = createComposeRule()
    private fun draft() = ReviewDraft(Evidence("synthetic", "a".repeat(64), "2026-01-01T12:00:00Z", 31.0, 35.0),
        "1234567", "רחוב בדיקה 1", "sidewalk_parking", "רכב חונה על המדרכה", emptySet())
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun reviewApprovalThenEditClears() {
        var saved: ReviewDraft? = null
        compose.setContent { MaterialTheme { ReviewScreen(draft(), null, { saved = it }, onBack = {}) } }
        screenshot("m1-review-synthetic.png")
        compose.onNodeWithText("אשר את התוכן לבדיקה").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(saved?.isApproved() == true) }
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("לוחית ללא מקפים").performScrollTo().performTextReplacement("7654321")
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(saved?.isApproved() == true) }
    }
    @Test fun offlinePlateCandidateRequiresSelectionAndInvalidatesReview() {
        val bitmap = Bitmap.createBitmap(1200, 260, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawText("12345678", 65f, 190f, android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK; textSize = 160f; isAntiAlias = true
            typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD)
        })
        var saved: ReviewDraft? = null
        compose.setContent { MaterialTheme { ReviewScreen(draft().approve(), bitmap.asImageBitmap(), { saved = it }, {}) } }
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("הצע לוחית מתוך הצילום").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("הצעה בלבד: \u206612345678\u2069").fetchSemanticsNodes().isNotEmpty() }
        screenshot("m2-ocr-synthetic.png")
        compose.onNodeWithText("הצעה בלבד: \u206612345678\u2069").performScrollTo().performClick()
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("12345678", saved?.plate); assertFalse(saved?.isApproved() == true) }
    }
    @Test fun settingsStoreManuallyEnteredCredentialsAndClearAfterSave() {
        // Owner decision 2026-11-08: municipal API keys are entered manually here and
        // stored only inside the encrypted settings. The screenshot is taken before any
        // value is typed, so no credential ever reaches a captured image.
        var saved: ReporterSettings? = null
        compose.setContent { MaterialTheme { SettingsScreen(null, { value, clear -> saved = value; clear() }, {}) } }
        screenshot("m1-settings-synthetic.png")
        compose.onNodeWithText("שם פרטי").performTextInput("בדיקה")
        compose.onNodeWithText("שם משפחה").performTextInput("מדווח")
        compose.onNodeWithText("מפתח מנוי").performScrollTo().performTextInput("synthetic-subscription-key")
        compose.onNodeWithText("שם משתמש API").performScrollTo().performTextInput("synthetic-api-user")
        compose.onNodeWithText("סיסמת API").performScrollTo().performTextInput("synthetic-api-pass")
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("בדיקה", saved?.firstName); assertEquals("מדווח", saved?.lastName)
            assertEquals("synthetic-subscription-key", saved?.municipalSubscriptionKey)
            assertEquals("synthetic-api-user", saved?.municipalUsername)
            assertEquals("synthetic-api-pass", saved?.municipalPassword)
            assertTrue(saved?.hasMunicipalCredentials() == true)
        }
        compose.onNodeWithText("בדיקה").assertDoesNotExist()
        compose.onNodeWithText("synthetic-subscription-key").assertDoesNotExist()
        compose.onNodeWithText("synthetic-api-pass").assertDoesNotExist()
        // The unmasked username field is checked too; masked fields expose only bullets.
        compose.onNodeWithText("synthetic-api-user").assertDoesNotExist()
    }
    @Test fun duplicateAndStaleRevisionBlockedAcrossReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "synthetic-ledger-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, ReportDatabase::class.java, name).build()
        val record = ReportRecord("synthetic", "a".repeat(64), "originals/synthetic.jpg", 0, "{}", null)
        db.reports().reserve(record)
        var duplicateRejected = false
        try { db.reports().reserve(record.copy(stableId = "other")) } catch (_: Exception) { duplicateRejected = true }
        assertTrue(duplicateRejected)
        assertEquals(1, db.reports().store("synthetic", 0, "synthetic-draft", "synthetic-digest"))
        assertEquals(0, db.reports().store("synthetic", 0, "stale", null))
        db.close()
        db = Room.databaseBuilder(context, ReportDatabase::class.java, name).build()
        assertEquals("synthetic-digest", db.reports().find("synthetic")?.reviewedDigest)
        assertEquals(1, db.reports().store("synthetic", 1, "edit", null))
        assertNull(db.reports().find("synthetic")?.reviewedDigest)
        db.close(); context.deleteDatabase(name); Unit
    }
    @Test fun settingsWithoutRecentAuthenticationNeverStorePlaintext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "reporter-settings.enc")
        file.delete()
        var failed = false
        try { SecureSettings(context).save(ReporterSettings("synthetic-first", "synthetic-last", "0000000", "000000000", "synthetic@example.invalid")) }
        catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertFalse(file.exists())
    }
    @Test fun rtlReviewUsesHebrewCategoryAndReadableDate() {
        compose.setContent { MaterialTheme { ReviewScreen(draft(), null, {}, {}) } }
        compose.onNodeWithText("חניה על המדרכה ▾").assertExists()
        compose.onNodeWithText("sidewalk_parking").assertDoesNotExist()
        compose.onNodeWithText("זמן: \u206601/01/2026 12:00 (UTC+00:00)\u2069").assertExists()
    }
    @Test fun offlineEncryptedDemoAndDurableDuplicateCheckpoint() {
        assertTrue(il.sidewalks.reporter.intake.SyntheticIntakeDemo.run().contains("הבדיקה הסינתטית הצליחה"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "synthetic-intake-journal"); directory.deleteRecursively()
        val upload = il.sidewalks.reporter.intake.SealedUpload("synthetic-upload", "synthetic-key", "synthetic-device", "a".repeat(64), byteArrayOf(1, 2, 3))
        var journal = il.sidewalks.reporter.intake.FileUploadJournal(directory)
        val checkpoint = journal.reserve(upload)
        journal.store(checkpoint.copy(phase = il.sidewalks.reporter.intake.UploadPhase.CREATING))
        journal = il.sidewalks.reporter.intake.FileUploadJournal(directory)
        var blocked = false
        try { journal.reserve(upload) } catch (_: Exception) { blocked = true }
        assertTrue(blocked)
        directory.deleteRecursively()
    }
    @Test fun outgoingDerivativePreservesOriginalAndStripsExif() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "synthetic-oriented.jpg")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        androidx.exifinterface.media.ExifInterface(file).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "6")
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:01 12:00:00")
            setLatLong(31.0, 35.0); saveAttributes()
        }
        val original = file.readBytes()
        val output = il.sidewalks.reporter.evidence.OutgoingEvidence.prepare(file)
        assertArrayEquals(original, file.readBytes())
        assertEquals(40, output.width); assertEquals(80, output.height)
        val stripped = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(output.jpeg))
        assertNull(stripped.latLong)
        assertNull(stripped.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals(il.sidewalks.reporter.intake.EncryptedPackage.sha256(original), output.originalHash)
        file.delete()
    }
    @Test fun debugOnlyDemoHiddenFromHomeAndRunsFromSettings() {
        compose.setContent { MaterialTheme {
            il.sidewalks.reporter.ui.AppShell(authenticate = { it() }, requestCamera = {}, readSettings = { null }, saveSettings = {})
        } }
        compose.onNodeWithText("טיוטות וכלי בדיקה").assertDoesNotExist()
        compose.onNodeWithText("בדיקת פיתוח: חבילה מוצפנת ללא רשת").assertDoesNotExist()
        compose.onNodeWithText("הגדרות").performClick()
        compose.onNodeWithText("הגדרות פרטיות").performScrollTo().assertExists()
        compose.onNodeWithText("בדיקת פיתוח: חבילה מוצפנת ללא רשת").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("הבדיקה הסינתטית הצליחה", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("הבדיקה הסינתטית הצליחה", substring = true).performScrollTo()
        screenshot("m1-home-intake-synthetic.png")
    }
    @Test fun nonVehicleReviewHasNoPlateAndCorrectionClearsApproval() {
        var saved: ReviewDraft? = null
        val bitmap = Bitmap.createBitmap(700, 300, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(235, 235, 235))
        canvas.drawRect(325f, 120f, 350f, 290f, android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY })
        canvas.drawRect(230f, 20f, 455f, 125f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        canvas.drawText("SYNTHETIC SIGN", 248f, 75f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 22f })
        compose.setContent { MaterialTheme { ReviewScreen(draft().approve(), bitmap.asImageBitmap(), { saved = it }, {}) } }
        compose.onNodeWithText("חניה על המדרכה ▾").performClick()
        compose.onNodeWithText("תמרור שבור").performClick()
        compose.onNodeWithText("לוחית:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("רכב חונה על המדרכה").assertDoesNotExist()
        compose.onNodeWithText("אשר את התוכן לבדיקה").assertIsNotEnabled()
        screenshot("design-sign-synthetic.png")
        compose.onNodeWithText("שלח").performScrollTo()
        screenshot("design-sign-actions-synthetic.png")
        compose.onNodeWithText("שמור טיוטה").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", saved?.plate); assertFalse(saved?.isApproved() == true) }
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        compose.onNodeWithText("לוחית ללא מקפים").assertDoesNotExist()
    }
    @Test fun photoFirstHomeScreenshot() {
        compose.setContent { MaterialTheme {
            il.sidewalks.reporter.ui.AppShell(authenticate = { it() }, requestCamera = {}, readSettings = { null }, saveSettings = {})
        } }
        compose.onNodeWithText("צלם תמונה").assertExists()
        compose.onNodeWithText("שם פרטי").assertDoesNotExist()
        compose.onNodeWithText("בית").assertExists()
        compose.onNodeWithText("הגדרות").assertExists()
        compose.onNodeWithText("בדיקת פיתוח: חבילה מוצפנת ללא רשת").assertDoesNotExist()
        screenshot("design-home-synthetic.png")
    }
    @Test fun originalEvidenceHashAndMissingMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "synthetic-evidence.jpg")
        file.writeText("synthetic original, not a JPEG")
        val reader = il.sidewalks.reporter.evidence.EvidenceReader()
        val a = reader.read(file, "synthetic")
        val b = reader.read(file, "synthetic")
        assertEquals(a, b); assertNull(a.capturedAtIso); assertNull(a.latitude)
        file.appendText("changed")
        assertNotEquals(a.originalSha256, reader.read(file, "synthetic").originalSha256)
        file.delete()
    }

    @Test fun settingsSaveDisabledWhenAllBlankAndAfterSave() {
        var saved: ReporterSettings? = null
        compose.setContent { MaterialTheme { SettingsScreen(null, { s, done -> saved = s; done() }, {}) } }
        compose.onNodeWithText("אמת זהות ושמור מוצפן").assertIsNotEnabled()
        compose.onNodeWithText("שם פרטי").performTextInput("בדיקה")
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("בדיקה", saved?.firstName) }
        compose.onNodeWithText("אמת זהות ושמור מוצפן").performScrollTo().assertIsNotEnabled()
    }
    @Test fun homeShowsTwoCaptureButtonsAndReportsNav() {
        compose.setContent { MaterialTheme {
            il.sidewalks.reporter.ui.AppShell(authenticate = { it() }, requestCamera = {}, readSettings = { null }, saveSettings = {})
        } }
        compose.onNodeWithText("צלם תמונה").assertExists()
        compose.onNodeWithText("העלה תמונה").assertExists()
        compose.onNodeWithText("הדיווחים שלי").assertExists()
        compose.onNodeWithText("הדיווחים שלי").performClick()
        compose.onNodeWithText("טיוטות שלא אושרו").assertExists()
        compose.onNodeWithText("אין טיוטות ממתינות").assertExists()
        compose.onNodeWithText("אושרו וממתינים לשליחה").assertExists()
        compose.onNodeWithText("היסטוריית דיווחים").assertExists()
        compose.onNodeWithText("בית").performClick()
        compose.onNodeWithText("צלם תמונה").assertExists()
    }
    @Test fun reportsScreenSeededDraftOpens() {
        val records = listOf(
            ReportRecord("aaaabbbb", "a".repeat(64), "originals/a.jpg", 0, "{}", null),
            ReportRecord("ccccdddd", "b".repeat(64), "originals/c.jpg", 0, "{}", "digest"),
        )
        var opened: String? = null
        compose.setContent { MaterialTheme { il.sidewalks.reporter.ui.ReportsScreen(records, { opened = it.stableId }) } }
        compose.onNodeWithText("פתח טיוטה aaaabbbb").performClick()
        compose.runOnIdle { assertEquals("aaaabbbb", opened) }
        compose.onNodeWithText("פתח דיווח ccccdddd").assertExists()
    }
    @Test fun galleryImportPreservesBytesAndExifMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val src = File(context.cacheDir, "synthetic-pick.jpg")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        src.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val exif = androidx.exifinterface.media.ExifInterface(src)
        exif.setLatLong(31.77, 35.21)
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:01 12:00:00")
        exif.saveAttributes()
        val srcBytes = src.readBytes()
        val originals = File(context.noBackupFilesDir, "test-originals")
        val copied = il.sidewalks.reporter.evidence.GalleryImport.copyToOriginals({ src.inputStream() }, originals)
        assertArrayEquals(srcBytes, copied.readBytes())
        val evidence = il.sidewalks.reporter.evidence.EvidenceReader().read(copied, "gallery")
        assertNotNull(evidence.capturedAtIso)
        assertEquals(31.77, evidence.latitude!!, 0.001)
        assertEquals(35.21, evidence.longitude!!, 0.001)
        copied.delete(); src.delete()
    }
}
