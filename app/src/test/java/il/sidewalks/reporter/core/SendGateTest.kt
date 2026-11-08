package il.sidewalks.reporter.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shown draft is what gets sent - never a stale or unsaved copy. */
class SendGateTest {

    private fun approvedDraft(): ReviewDraft {
        val evidence = Evidence("stable-gate", "a".repeat(64), "2026-11-08T10:00:00+02:00", 31.7683, 35.2137)
        return ReviewDraft(evidence, "12345678", "רחוב 12", "sidewalk_parking",
            "רכב חנה על המדרכה", emptySet()).approve()
    }

    @Test fun shownEqualsSavedApprovedIsReady() {
        val saved = approvedDraft()
        assertTrue(SendGate.readyToSend(saved, saved))
    }

    @Test fun saveMismatchRefused() {
        // The screen shows edits a failed save never persisted: refuse.
        val saved = approvedDraft()
        val shown = saved.copy(exactHebrewText = "נוסח שלא נשמר").approve()
        assertFalse(SendGate.readyToSend(shown, saved))
    }

    @Test fun unapprovedRefused() {
        val saved = approvedDraft().copy(approvedDigest = null)
        assertFalse(SendGate.readyToSend(saved, saved))
    }

    @Test fun missingDraftRefused() {
        val saved = approvedDraft()
        assertFalse(SendGate.readyToSend(null, saved))
        assertFalse(SendGate.readyToSend(saved, null))
    }
}
