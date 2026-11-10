package il.sidewalks.reporter

import android.net.Uri
import il.sidewalks.reporter.ui.theme.AppTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.ledger.ReportDatabase
import il.sidewalks.reporter.municipal.StreetDirectory
import il.sidewalks.reporter.municipal.MunicipalClient
import il.sidewalks.reporter.municipal.OkHttpMunicipalClient
import il.sidewalks.reporter.security.ReporterSettings
import il.sidewalks.reporter.ui.AppShell
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Full report flow, end to end, on the real AppShell: shared-photo entry,
 * review, evidence confirmation, approval, send, municipal submission.
 *
 * Stub-only: an on-device loopback MockWebServer answers the four municipal
 * calls with canned responses. No network outside loopback, no credentials,
 * no private data, and no live mode. The app manifest forbids cleartext;
 * app/src/debug scopes a loopback-only exception to test builds.
 * Device-unlock is replaced by a pass-through only in this synthetic test.
 * The UI driving itself lives in FlowDriver, shared with the opt-in live
 * submission E2E so both exercise the identical flow code path.
 */
class FullFlowE2eTest {
    @get:Rule val compose = createComposeRule()

    @Before fun pinHebrewUiLanguage() {
        il.sidewalks.reporter.i18n.UiPrefsLanguageStore.of(InstrumentationRegistry.getInstrumentation().targetContext).set("he")
    }

    private val sharedUri = Uri.parse("content://il.sidewalks.reporter.testshare/with-metadata.jpg")
    private var ownedPhotoHash: String? = null

    /** Never take ownership of a pre-existing row, even one with the test photo. */
    private fun claimSyntheticPhoto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = requireNotNull(context.contentResolver.openInputStream(sharedUri)).use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val db = Room.databaseBuilder(context, ReportDatabase::class.java, "report-ledger.db").build()
        try {
            assumeTrue("Test photo already has a report; leave it untouched", runBlocking { db.reports().findPhoto(hash) } == null)
            ownedPhotoHash = hash
        } finally {
            db.close()
        }
    }

    /** Remove only the row and files created from this test's synthetic photo. */
    private fun cleanOwnedState() {
        val hash = ownedPhotoHash ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, ReportDatabase::class.java, "report-ledger.db").build()
        try {
            val record = runBlocking { db.reports().findPhoto(hash) } ?: return
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM reports WHERE stableId = ? AND originalSha256 = ?", arrayOf(record.stableId, hash))
            File(context.noBackupFilesDir, record.originalRelativePath).delete()
            File(context.noBackupFilesDir, "submissions/${record.stableId}.json").delete()
        } finally {
            db.close()
            ownedPhotoHash = null
        }
    }

    /** Synthetic directory injected in memory; never edits an installed file. */
    private fun syntheticStreetDirectory(): StreetDirectory = StreetDirectory.load(
        """{"version":1,"streets":[{"StreetCode":"900001","StreetName":"רחוב בדיקה"}],"addresses":[[31.77,35.2137,"1","רחוב בדיקה"]]}""")

    private fun launchFlow(settings: ReporterSettings, factory: ((ReporterSettings) -> MunicipalClient)?) {
        val uri = sharedUri
        val signal = mutableStateOf(EntrySignal(1, EntryIntents.Request.SharedPhoto(uri.toString())))
        compose.setContent {
            AppTheme {
                AppShell(authenticate = { _, _, action -> action() }, requestCamera = { it() },
                    readSettings = { settings }, saveSettings = {}, entry = signal,
                    municipalClientFactory = factory,
                    streetDirectoryLoader = { syntheticStreetDirectory() })
            }
        }
    }

    @Test fun fullReportFlowStubbedServer() {
        claimSyntheticPhoto()
        val settings = ReporterSettings(
            firstName = "ישראל", lastName = "ישראלי", phone = "+972501234567",
            idNumber = "000000018", email = "synthetic@example.com",
            municipalSubscriptionKey = "synthetic-key",
            municipalUsername = "synthetic-user",
            municipalPassword = "synthetic-pass")
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"Success":true,"Token":"synthetic-token"}"""))
            server.enqueue(MockResponse().setBody("""{"Success":true,"ContactIdInCRM":"synthetic-contact-id"}"""))
            server.enqueue(MockResponse().setBody(
                """{"Success":true,"CaseIdInCRM":"synthetic-case-id","WebCaseIdInCRM":"synthetic-webcase-id","CaseNumberInCRM":"2000-0000000"}"""))
            server.enqueue(MockResponse().setBody("""{"Success":true}"""))
            server.start()
            try {
                launchFlow(settings) { s ->
                    OkHttpMunicipalClient.forTesting(server.url("/"),
                        s.municipalSubscriptionKey, s.municipalUsername, s.municipalPassword)
                }
                val driver = FlowDriver(compose)
                val text = "רכב חונה על המדרכה בתרחיש בדיקה סינתטי"
                driver.driveToApproved(FlowFields(
                    text = text,
                    plate = "12345678",
                    address = "רחוב בדיקה 1",
                    capturedAtIso = "2026-01-01T12:00:00+02:00",
                    latitude = "31.77",
                    longitude = "35.2137",
                    categoryLabel = "חניה על המדרכה"))
                driver.screenshot("e2e-review-synthetic.png")
                driver.tapSendAndAwaitCaseNumber()
                driver.screenshot("e2e-submitted-synthetic.png")

                val login = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "Login never reached the stub" }
                val contact = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "contact never reached the stub" }
                val create = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "CreateCase never reached the stub" }
                val attach = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "AddFileToCase never reached the stub" }
                assertEquals("/Login", login.path)
                assertEquals("/createupdatecontact", contact.path)
                assertEquals("/CreateCase", create.path)
                assertEquals("/AddFileToCase", attach.path)
                assertEquals("synthetic-user", JSONObject(login.body.readUtf8()).getString("UserName"))
                val createBody = JSONObject(create.body.readUtf8())
                assertEquals(text, createBody.getString("CaseDescription"))
                assertEquals("900001", createBody.getString("CaseStreetCode"))
                assertEquals("1", createBody.getString("CaseHouseNumber"))
                assertEquals("1145", createBody.getString("CaseSubjectCode"))
                assertEquals("000000018", createBody.getJSONObject("contact").getString("ContactIdNumber"))
                val attachBody = JSONObject(attach.body.readUtf8())
                val jpeg = java.util.Base64.getDecoder().decode(attachBody.getString("fileContent"))
                assertTrue("attached file must be the original JPEG",
                    jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
                assertNotNull(attachBody.getString("caseIdInCRM"))
            } finally {
                cleanOwnedState()
            }
        }
    }

    /**
     * Regression for run 42769339528: a send tap that never reaches the
     * app's submit path (here: the send button is disabled because the draft
     * was never approved) must be reported as SEND_NOT_FIRED - provably no
     * request was made - and never as a municipal-looking timeout.
     */
    @Test fun sendTapWithoutApprovalIsNotFired() {
        claimSyntheticPhoto()
        val settings = ReporterSettings(
            firstName = "ישראל", lastName = "ישראלי", phone = "+972501234567",
            idNumber = "000000018", email = "synthetic@example.com",
            municipalSubscriptionKey = "synthetic-key",
            municipalUsername = "synthetic-user",
            municipalPassword = "synthetic-pass")
        try {
            launchFlow(settings, null)
            val driver = FlowDriver(compose)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("בדיקת הדיווח").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("נמצא מיקום בתמונה").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithText("נמצא מיקום בתמונה").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("השתמש במיקום").performClick()
            }
            if (compose.onAllNodesWithText("למצוא רחוב ומספר בית?").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("לא עכשיו").performClick()
            }
            assertEquals(FlowDriver.SendOutcome.SEND_NOT_FIRED, driver.tapSendAndAwaitOnce(5_000))
        } finally {
            cleanOwnedState()
        }
    }

}
