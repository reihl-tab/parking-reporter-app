package il.sidewalks.reporter.live

import android.content.Context
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.EntryIntents
import il.sidewalks.reporter.EntrySignal
import il.sidewalks.reporter.FlowDriver
import il.sidewalks.reporter.FlowFields
import il.sidewalks.reporter.ledger.ReportDatabase
import il.sidewalks.reporter.security.ReporterSettings
import il.sidewalks.reporter.ui.AppShell
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * Live submission E2E: the full UI flow against the REAL municipal endpoint,
 * filing one real case from owner-approved staged data. Opt-in only: the
 * regular CI emulator job never stages live-params.json, so this returns
 * immediately there and the suite stays green. The dedicated manual
 * workflow (live-emulator-e2e.yml) stages the params from repository
 * secrets into the app's private files directory, records the emulator
 * screen and encrypts the recording before any upload.
 *
 * The flow uses the app's production defaults end to end: the real pinned
 * municipal endpoint, the real on-device street directory and the same
 * FlowDriver the synthetic stub E2E drives.
 *
 * Privacy: credentials, report details and the municipal case number never
 * reach logcat, assertion messages or stdout. The only public test output
 * is a scrubbed marker log (fixed step names, case=present|unparsed,
 * exception class names); the case number, screenshots and any preserved
 * submission state go only to the workflow's encrypted evidence bundle.
 */
class LiveEmulatorSubmissionTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var logFile: File

    /** Fixed markers only; this file lands in the workflow's public log. */
    private fun marker(step: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jerusalem")
        }.format(Date())
        logFile.appendText("$stamp LIVE_E2E $step\n")
    }

    @Test fun liveApprovedSubmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val paramsFile = File(context.filesDir, "live-params.json")
        // Not a live run: only the manual workflow stages the params file.
        if (!paramsFile.isFile) return
        logFile = File(context.filesDir, "live-e2e-log.txt")
        logFile.delete()
        try {
            runLive(context, JSONObject(paramsFile.readText(Charsets.UTF_8)))
        } catch (e: Throwable) {
            runCatching { marker("FAIL class=" + e.javaClass.simpleName) }
            // The real stack stays in instrumentation output, which the
            // workflow publishes only inside the encrypted bundle.
            throw e
        } finally {
            // The params carry repository secrets; never leave them on device.
            paramsFile.delete()
        }
    }

    private fun runLive(context: Context, params: JSONObject) {
        il.sidewalks.reporter.i18n.UiPrefsLanguageStore.of(context).set("he")
        marker("START")

        // Consent binding, same rule as the engine submit trial: the staged
        // text must hash to the exact digest the owner approved. Constant
        // message only: never print the digests.
        val text = params.getString("text")
        val expectedDigest = params.getString("approvedSha256").lowercase(Locale.US)
        val actualDigest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        if (expectedDigest != actualDigest) {
            throw AssertionError("approved text digest mismatch - refusing to submit")
        }
        marker("DIGEST_OK")

        val settings = ReporterSettings(
            firstName = params.getString("firstName"),
            lastName = params.getString("lastName"),
            phone = params.getString("phone"),
            idNumber = params.getString("idNumber"),
            email = params.getString("email"),
            municipalSubscriptionKey = params.getString("subscriptionKey"),
            municipalUsername = params.getString("username"),
            municipalPassword = params.getString("password"))

        // Claim the staged photo only if no report already owns it: a live
        // run never re-files a photo that already produced a case.
        val sharedUri = Uri.parse("content://il.sidewalks.reporter.liveshare/live-photo.jpg")
        val bytes = requireNotNull(context.contentResolver.openInputStream(sharedUri)) {
            "staged photo missing"
        }.use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val db = Room.databaseBuilder(context, ReportDatabase::class.java, "report-ledger.db").build()
        try {
            if (runBlocking { db.reports().findPhoto(hash) } != null) {
                throw AssertionError("staged photo already has a report; refusing to file again")
            }
        } finally {
            db.close()
        }
        marker("PHOTO_CLAIMED")

        val driver = FlowDriver(compose)
        val signal = mutableStateOf(EntrySignal(1, EntryIntents.Request.SharedPhoto(sharedUri.toString())))
        compose.setContent {
            MaterialTheme {
                AppShell(authenticate = { _, _, action -> action() }, requestCamera = { it() },
                    readSettings = { settings }, saveSettings = {}, entry = signal)
            }
        }
        var cleaned = false
        var preserveState = false
        var sendTapped = false
        var outcomeKnown = false
        try {
            driver.driveToApproved(FlowFields(
                text = text,
                plate = params.optString("plate").ifBlank { null },
                address = params.getString("address"),
                capturedAtIso = params.getString("capturedAt"),
                latitude = params.getString("lat"),
                longitude = params.getString("lon"),
                categoryLabel = params.getString("categoryLabel")))
            marker("APPROVED")
            driver.screenshot("live-e2e-approved.png")
            marker("SEND_TAPPED")
            sendTapped = true
            // A live call can be slow; the wait is deliberately longer than
            // the stub's. DRAFT_NOT_SAVED means the gate refused and nothing
            // was sent, so only then is a retry safe.
            // 300s: the municipal API through the owner tunnel can answer
            // slowly; a slow-but-successful response must still be caught.
            // The 120s wait is what turned run 42762339581 OUTCOME_UNKNOWN.
            var outcome = driver.tapSendAndAwaitOnce(300_000)
            var sendAttempts = 1
            while (outcome == FlowDriver.SendOutcome.DRAFT_NOT_SAVED && sendAttempts < 3) {
                Thread.sleep(500)
                sendAttempts += 1
                outcome = driver.tapSendAndAwaitOnce(300_000)
            }
            when (outcome) {
                FlowDriver.SendOutcome.SUBMITTED -> {
                    marker("SEND_CONFIRMED")
                    outcomeKnown = true
                    driver.screenshot("live-e2e-submitted.png")
                    // The case number is owner-private: the encrypted bundle
                    // only, never the public marker log.
                    val shown = driver.nodeTextContaining("מספר קריאה") ?: ""
                    val caseNumber = Regex("\\d{4}-\\d{7}").find(shown)?.value
                    File(context.filesDir, "live-e2e-case-number.txt").writeText(caseNumber ?: "")
                    marker("SUBMITTED case=" + if (caseNumber != null) "present" else "unparsed")
                    cleanOwnedPhoto(context, hash)
                    cleaned = true
                    marker("DONE")
                }
                FlowDriver.SendOutcome.DRAFT_NOT_SAVED -> {
                    outcomeKnown = true
                    throw AssertionError("draft-not-saved persisted across retries")
                }
                FlowDriver.SendOutcome.TIMEOUT -> {
                    // The request may already have reached the city: the case
                    // may exist. Keep the app's submission-state record for
                    // the encrypted bundle instead of cleaning it away.
                    marker("OUTCOME_UNKNOWN")
                    outcomeKnown = true
                    runCatching { driver.screenshot("live-e2e-outcome-unknown.png") }
                    preserveSubmissionState(context, hash)
                    preserveState = true
                    throw AssertionError("live outcome unknown after send - do not re-dispatch; check 106 first")
                }
            }
        } catch (t: Throwable) {
            if (sendTapped && !outcomeKnown) {
                // A failure after the send tap is as ambiguous as a timeout:
                // the request may have reached the city. Keep the evidence
                // instead of letting finally wipe it, and forbid re-runs.
                marker("OUTCOME_UNKNOWN")
                runCatching { driver.screenshot("live-e2e-outcome-unknown.png") }
                runCatching { preserveSubmissionState(context, hash) }
                preserveState = true
                throw AssertionError(
                    "live outcome unknown after send - do not re-dispatch; check 106 first", t)
            }
            throw t
        } finally {
            if (!cleaned && !preserveState) runCatching { cleanOwnedPhoto(context, hash) }
        }
    }

    /** Copy the app's own submission-state record for the encrypted bundle. */
    private fun preserveSubmissionState(context: Context, hash: String) {
        val db = Room.databaseBuilder(context, ReportDatabase::class.java, "report-ledger.db").build()
        try {
            val record = runBlocking { db.reports().findPhoto(hash) } ?: return
            val state = File(context.noBackupFilesDir, "submissions/${record.stableId}.json")
            if (state.isFile) {
                state.copyTo(File(context.filesDir, "live-e2e-submission-state.json"), overwrite = true)
            }
        } finally {
            db.close()
        }
    }

    /** Remove only the row and files created from this run's staged photo. */
    private fun cleanOwnedPhoto(context: Context, hash: String) {
        val db = Room.databaseBuilder(context, ReportDatabase::class.java, "report-ledger.db").build()
        try {
            val record = runBlocking { db.reports().findPhoto(hash) } ?: return
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM reports WHERE stableId = ? AND originalSha256 = ?", arrayOf(record.stableId, hash))
            File(context.noBackupFilesDir, record.originalRelativePath).delete()
            File(context.noBackupFilesDir, "submissions/${record.stableId}.json").delete()
        } finally {
            db.close()
        }
    }
}
