package il.sidewalks.reporter

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.ui.ReviewScreen
import org.junit.Rule
import org.junit.Test

/** The M-B send gate on the review screen (issue #153, owner 19:49). */
class SubmissionGateTest {
    @get:Rule val compose = createComposeRule()

    private fun approvedDraft(): ReviewDraft {
        val evidence = Evidence("stable-gate", "a".repeat(64), "2026-11-08T10:00:00+02:00", 31.7683, 35.2137)
        return ReviewDraft(evidence, "12345678", "רחוב 12", "sidewalk_parking",
            "רכב חנה על המדרכה", emptySet()).approve()
    }

    @Test fun sendRequiresApprovalAndGate() {
        var submitted = 0
        compose.setContent {
            MaterialTheme {
                ReviewScreen(approvedDraft(), null, onSave = {}, onBack = {},
                    onSubmit = { submitted++ })
            }
        }
        // The draft is approved, so the gated send is live.
        compose.onNodeWithText("שלח לעירייה").assertIsEnabled()
        compose.onNodeWithText("שלח לעירייה").assertExists()
    }

    @Test fun sendStaysDisabledUntilApproved() {
        compose.setContent {
            MaterialTheme {
                ReviewScreen(approvedDraft().copy(approvedDigest = null), null,
                    onSave = {}, onBack = {}, onSubmit = {})
            }
        }
        compose.onNodeWithText("שלח לעירייה").assertIsNotEnabled()
    }

    @Test fun sendButtonSubmitsTheShownDraft() {
        // The screen hands the draft it actually shows to the submit
        // callback - AppShell then refuses anything but the saved copy.
        val shown = approvedDraft()
        var sent: ReviewDraft? = null
        compose.setContent {
            MaterialTheme {
                ReviewScreen(shown, null, onSave = {}, onBack = {},
                    onSubmit = { sent = it })
            }
        }
        compose.onNodeWithText("שלח לעירייה").performClick()
        org.junit.Assert.assertTrue(sent == shown)
    }

    @Test fun noGateKeepsTheDisabledPlaceholder() {
        compose.setContent {
            MaterialTheme {
                ReviewScreen(approvedDraft(), null, onSave = {}, onBack = {})
            }
        }
        compose.onNodeWithText("שליחה אינה זמינה בגרסת הבדיקה").assertExists()
    }
}
