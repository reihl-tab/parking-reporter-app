package il.sidewalks.reporter

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.fail

/** Field values the flow driver types into the review screen. */
internal data class FlowFields(
    val text: String,
    val plate: String?,
    val address: String,
    val capturedAtIso: String,
    val latitude: String,
    val longitude: String,
    val categoryLabel: String,
)

/**
 * Drives the real AppShell review -> approval -> send flow. Shared by the
 * synthetic stub E2E (FullFlowE2eTest) and the opt-in live submission E2E
 * (live/LiveEmulatorSubmissionTest): ONE flow code path for both, so a UI
 * change breaks the stub first, never only the live run.
 */
internal class FlowDriver(private val compose: ComposeTestRule) {

    /**
     * Best-effort root screenshot into the APP's files dir. A failed or
     * empty capture is logged to stdout (instrumentation output) and never
     * aborts the flow; only real files are listed in the pull manifest the
     * workflow reads.
     */
    fun screenshot(name: String) {
        runCatching {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.filesDir, name)
            file.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            if (file.isFile && file.length() > 0) {
                File(context.filesDir, SHOTS_MANIFEST).appendText("$name\n")
            } else {
                println("FLOW_DRIVER screenshot $name empty - skipped")
            }
        }.onFailure {
            println("FLOW_DRIVER screenshot $name failed: ${it.javaClass.simpleName}")
        }
    }

    /**
     * Clicks the ENABLED button carrying [text] through its semantics action,
     * never an injected motion event. The approval and send buttons sit where
     * an open soft keyboard can cover them; a coordinate tap there is eaten
     * by the IME window and nothing happens (run 42769339528: the send tap
     * never reached the app and the run read as a municipal timeout). The
     * matcher requires an ENABLED node: a disabled button keeps its click
     * action in semantics, so without isEnabled a click there would silently
     * no-op. Waits briefly, then throws when no enabled clickable node
     * exists, which is how a disabled button surfaces instead of a miss.
     */
    private fun clickButton(text: String) {
        compose.waitUntil(SEND_FIRED_WAIT_MS) { hasClickableButton(text) }
        compose.onNode(hasText(text) and hasClickAction() and isEnabled())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    /** True when an ENABLED (clickable) button carrying [text] exists. */
    private fun hasClickableButton(text: String): Boolean =
        compose.onAllNodes(hasText(text) and hasClickAction() and isEnabled())
            .fetchSemanticsNodes().isNotEmpty()

    /** Shared-photo entry through a locally approved draft. */
    fun driveToApproved(fields: FlowFields) {
        // A photo with embedded GPS (every real live photo) first raises the
        // location prompt; a synthetic photo goes straight to the review.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("בדיקת הדיווח").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("נמצא מיקום בתמונה").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("נמצא מיקום בתמונה").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("השתמש במיקום").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("בדיקת הדיווח").fetchSemanticsNodes().isNotEmpty()
            }
        }
        // This scripted flow already has an approved address; no provider
        // lookup is needed. Dismiss the optional address consent if shown.
        if (compose.onAllNodesWithText("למצוא רחוב ומספר בית?").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("לא עכשיו").performClick()
        }
        compose.onNodeWithText("נדרש תיקון קטגוריה ▾").performScrollTo().performClick()
        compose.onNodeWithText(fields.categoryLabel).performClick()
        compose.onNodeWithText("ערוך").performScrollTo().performClick()
        if (fields.plate != null) {
            compose.onNodeWithText("לוחית ללא מקפים").performScrollTo().performTextReplacement(fields.plate)
        }
        compose.onNodeWithText("רחוב ומספר בית שאישרת").performScrollTo().performTextReplacement(fields.address)
        compose.onNodeWithText("הטקסט המדויק לבדיקה").performScrollTo().performTextReplacement(fields.text)
        compose.onNodeWithText("זמן מאושר עם אזור זמן (ISO)").performScrollTo().performTextReplacement(fields.capturedAtIso)
        // Missing-GPS drafts already expose Advanced; GPS drafts keep it closed.
        if (compose.onAllNodesWithText("תיקון מיקום מתקדם").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("תיקון מיקום מתקדם").performScrollTo().performClick()
        }
        compose.onNodeWithText("קו רוחב").performScrollTo().performTextReplacement(fields.latitude)
        compose.onNodeWithText("קו אורך").performScrollTo().performTextReplacement(fields.longitude)
        clickButton("אשר זמן, מיקום וכתובת שבדקת")
        compose.waitUntil(10_000) { hasClickableButton("אשר את התוכן לבדיקה") }
        clickButton("אשר את התוכן לבדיקה")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("התוכן אושר מקומית, לא נשלח").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** What the UI showed after one send tap. */
    internal enum class SendOutcome { SUBMITTED, DRAFT_NOT_SAVED, TIMEOUT, SEND_NOT_FIRED }

    private fun outcomeShown(): Boolean =
        compose.onAllNodesWithText("נשלח לעירייה. מספר קריאה", substring = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("הטיוטה לא נשמרה", substring = true)
                .fetchSemanticsNodes().isNotEmpty()

    /**
     * Tap send once and wait up to [outcomeWaitMs] for the outcome. Never
     * throws on timeout: after a real send tap a timeout is ambiguous (the
     * request may already have reached the city) and the caller decides what
     * that means - the stub fails, the live run keeps its evidence.
     *
     * SEND_NOT_FIRED is a UI-only signal (no enabled button, or the sending
     * label never showed): it is a strong lead that nothing was sent, but
     * label absence alone is not proof. The live caller MUST corroborate it
     * against the app's submission-state record (written before the first
     * network call) before treating a re-run as safe, and must never retry
     * it in-run. When [screenshotEveryMs] > 0 the long outcome wait is
     * captured every that many ms into live-e2e-wait-NN.png files.
     */
    fun tapSendAndAwaitOnce(outcomeWaitMs: Long, screenshotEveryMs: Long = 0): SendOutcome {
        if (!hasClickableButton("שלח לעירייה")) return SendOutcome.SEND_NOT_FIRED
        clickButton("שלח לעירייה")
        // The button must flip to its sending label (or an outcome must
        // appear) within a few seconds; otherwise the tap never reached the
        // app's submit path and retrying is safe - no request was made.
        val fired = try {
            compose.waitUntil(SEND_FIRED_WAIT_MS) {
                compose.onAllNodesWithText("שולח לעירייה", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() || outcomeShown()
            }; true
        } catch (e: ComposeTimeoutException) { false }
        if (!fired) return SendOutcome.SEND_NOT_FIRED
        screenshot("live-e2e-after-send-tap.png")
        val startedAt = System.currentTimeMillis()
        var shot = 0
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAt
            val remaining = outcomeWaitMs - elapsed
            if (remaining <= 0) return SendOutcome.TIMEOUT
            val slice = if (screenshotEveryMs > 0) minOf(screenshotEveryMs, remaining) else remaining
            try {
                compose.waitUntil(slice) { outcomeShown() }
                break
            } catch (e: ComposeTimeoutException) {
                if (screenshotEveryMs <= 0 || System.currentTimeMillis() - startedAt >= outcomeWaitMs) {
                    return SendOutcome.TIMEOUT
                }
                shot += 1
                screenshot("live-e2e-wait-%02d.png".format(shot))
            }
        }
        return if (compose.onAllNodesWithText("נשלח לעירייה. מספר קריאה", substring = true)
            .fetchSemanticsNodes().isNotEmpty()) SendOutcome.SUBMITTED else SendOutcome.DRAFT_NOT_SAVED
    }

    fun tapSendAndAwaitCaseNumber() {
        // The send gate compares the shown draft to the last SAVED one and
        // the post-approval save is asynchronous; retry the tap if the gate
        // answers "not saved" before the save lands.
        var attempt = 0
        while (true) {
            attempt += 1
            when (tapSendAndAwaitOnce(45_000)) {
                SendOutcome.SUBMITTED -> return
                SendOutcome.DRAFT_NOT_SAVED -> {
                    if (attempt >= 3) fail("draft-not-saved persisted across retries")
                    Thread.sleep(500)
                }
                SendOutcome.TIMEOUT -> fail("no submission outcome shown")
                SendOutcome.SEND_NOT_FIRED -> fail("send tap never fired")
            }
        }
    }

    /** Full text of the first node containing [substring], or null. */
    fun nodeTextContaining(substring: String): String? {
        val node = compose.onAllNodesWithText(substring, substring = true)
            .fetchSemanticsNodes().firstOrNull() ?: return null
        if (SemanticsProperties.Text !in node.config) return null
        return node.config[SemanticsProperties.Text].joinToString(separator = "") { it.text }
    }

    private companion object {
        /** Lists the screenshots that actually landed, one filename per line; the workflow pulls by it. */
        const val SHOTS_MANIFEST = "live-e2e-shots.txt"
        /** How long the app may take to enter its sending state after the tap before we call the tap missed. */
        const val SEND_FIRED_WAIT_MS = 10_000L
    }
}
