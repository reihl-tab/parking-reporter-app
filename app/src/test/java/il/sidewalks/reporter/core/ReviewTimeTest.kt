package il.sidewalks.reporter.core

import il.sidewalks.reporter.ui.ReviewTime
import org.junit.Assert.*
import org.junit.Test

class ReviewTimeTest {
    @Test fun displayUsesRecordedLocal24HourClock() {
        assertEquals("01/01/2026 00:05", ReviewTime.display("2026-01-01T00:05:47+02:00"))
        assertEquals("31/12/2026 23:59", ReviewTime.display("2026-12-31T23:59:00+03:00"))
    }
    @Test fun unchangedDisplayPreservesExactSecondsAndOffset() {
        val original = "2026-01-01T12:00:47.123+02:00"
        assertEquals(original, ReviewTime.toIso("01/01/2026 12:00", original))
    }
    @Test fun missingZoneRequiresExplicitCheckedOffset() {
        assertEquals("2026-01-01T12:00+02:00", ReviewTime.toIso("01/01/2026 12:00", "2026-01-01T12:00:00", "+02:00"))
        assertEquals("2026-01-01T12:00+03:00", ReviewTime.toIso("01/01/2026 12:00", null, "+03:00"))
        assertNull(ReviewTime.toIso("01/01/2026 12:00", null, "unknown"))
    }
    @Test fun editedMinutesUseKnownOffsetAndStrictDate() {
        assertEquals("2026-01-02T21:15+02:00", ReviewTime.toIso("02/01/2026 21:15", "2026-01-01T12:00:00+02:00"))
        for (bad in listOf("31/02/2026 12:00", "01/01/2026 24:00", "01/01/2026 12:00 PM", "2026-01-01T12:00:00Z"))
            assertNull(ReviewTime.toIso(bad, "2026-01-01T12:00:00Z"))
        assertNull(ReviewTime.toIso("01/01/2026 12:00", null))
        assertNull(ReviewTime.toIso("01/01/2026 12:00", "2026-01-01T12:00:00"))
    }
}
