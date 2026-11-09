package il.sidewalks.reporter.core

import org.junit.Assert.*
import org.junit.Test

class ReviewDraftTest {
    private fun draft() = ReviewDraft(
        Evidence("synthetic-id", "a".repeat(64), "2026-01-01T12:00:00Z", 31.0, 35.0),
        "1234567", "רחוב בדיקה 1", "sidewalk_parking", "רכב חונה על המדרכה", emptySet(),
    )
    @Test fun exactApprovalAndEditsInvalidate() {
        val approved = draft().approve()
        assertTrue(approved.isApproved())
        assertFalse(approved.edit(plate = "7654321").isApproved())
        assertFalse(approved.edit(address = "רחוב בדיקה 2").isApproved())
        assertFalse(approved.edit(subject = "other").isApproved())
        assertFalse(approved.edit(text = "תוכן אחר").isApproved())
        assertFalse(approved.edit().isApproved())
    }
    @Test fun unresolvedEvidenceBlocksApproval() {
        val incomplete = draft().copy(unresolved = setOf("plate"))
        assertFalse(incomplete.isReady())
        assertThrows(IllegalStateException::class.java) { incomplete.approve() }
        assertFalse(draft().copy(confirmedLatitude = null).isReady())
    }
    @Test fun stableIdentityOrPhotoCannotInheritApproval() {
        val approved = draft().approve()
        assertFalse(approved.copy(evidence = approved.evidence.copy(stableId = "changed")).isApproved())
        assertFalse(approved.copy(evidence = approved.evidence.copy(originalSha256 = "b".repeat(64))).isApproved())
    }
    @Test fun digestUsesUtf8AndIsStable() {
        assertEquals(draft().digest(), draft().digest())
        assertEquals(64, draft().digest().length)
        assertNotEquals(draft().digest(), draft().copy(exactHebrewText = "תיאור שונה").digest())
    }
    @Test fun evidenceCorrectionsInvalidateAndRequireOffset() {
        val approved = draft().approve()
        assertFalse(approved.confirmEvidence("2026-01-01T12:00:00", 31.0, 35.0).isReady())
        assertFalse(approved.confirmEvidence("2026-01-01T12:00:00Z", 91.0, 35.0).isReady())
        assertFalse(approved.confirmEvidence("2026-01-01T12:00:00Z", 31.0, 35.0).isApproved())
    }
    @Test fun codecPreservesOriginalAndCorrection() {
        val d = draft().confirmEvidence("2026-01-01T13:00:00+02:00", 32.0, 34.0)
        assertEquals(d, il.sidewalks.reporter.ledger.DraftCodec.decode(il.sidewalks.reporter.ledger.DraftCodec.encode(d)))
    }
    @Test fun missingOriginalMetadataRoundTripsWithoutInventedValues() {
        val d = draft().copy(evidence = Evidence("synthetic-id", "a".repeat(64), null, null, null),
            confirmedTime = null, confirmedLatitude = null, confirmedLongitude = null,
            unresolved = setOf("missing-time", "missing-location"))
        val encoded = il.sidewalks.reporter.ledger.DraftCodec.encode(d)
        val decoded = il.sidewalks.reporter.ledger.DraftCodec.decode(encoded)
        assertEquals(d, decoded)
        assertFalse(decoded.isReady())
        assertNull(decoded.evidence.capturedAtIso)
        assertNull(decoded.confirmedLatitude)
    }
    @Test fun correctionChangesDigestWithoutChangingOriginalEvidence() {
        val d = draft()
        val corrected = d.confirmEvidence("2026-01-01T14:00:00+02:00", 32.0, 34.0)
        assertEquals(d.evidence, corrected.evidence)
        assertNotEquals(d.digest(), corrected.digest())
        assertTrue(corrected.isReady())
    }
    @Test fun codecNeverRestoresApprovalFromUntrustedJson() {
        val approved = draft().approve()
        val encoded = il.sidewalks.reporter.ledger.DraftCodec.encode(approved)
        val decoded = il.sidewalks.reporter.ledger.DraftCodec.decode(encoded)
        assertFalse(decoded.isApproved())
        assertNull(decoded.approvedDigest)
    }
    @Test fun categoriesDriveRequiredPlateAndClearStaleVehicleData() {
        for (code in listOf("broken_sign", "faded_crosswalk", "bollard_request")) {
            val changed = draft().approve().edit(subject = code)
            assertEquals("", changed.plate)
            assertEquals("", changed.exactHebrewText)
            assertFalse(changed.isReady())
            assertTrue(changed.edit(text = "תיאור בדיקה מתאים").isReady())
            assertFalse(changed.isApproved())
            assertFalse(changed.copy(plate = "1234567").isReady())
            assertEquals(changed, il.sidewalks.reporter.ledger.DraftCodec.decode(il.sidewalks.reporter.ledger.DraftCodec.encode(changed)))
        }
        assertFalse(draft().copy(subject = "unknown").isReady())
        assertFalse(draft().copy(plate = "").isReady())
        assertFalse(draft().edit(subject = "broken_sign").edit(subject = "sidewalk_parking").isReady())
    }
    @Test fun badCoordinatesAndHashRejected() {
        assertThrows(IllegalArgumentException::class.java) { draft().evidence.copy(latitude = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { draft().evidence.copy(longitude = 181.0) }
        assertThrows(IllegalArgumentException::class.java) { draft().evidence.copy(originalSha256 = "bad") }
    }
}
