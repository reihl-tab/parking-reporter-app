package il.sidewalks.reporter.core

import il.sidewalks.reporter.ui.ReviewTime
import java.time.OffsetDateTime
import org.junit.Assert.*
import org.junit.Test

class ReviewTimeTest {
    @org.junit.Test fun unchangedOffsetlessEvidencePreservesSecondsAndNanos() {
        org.junit.Assert.assertEquals("2026-01-01T12:00:47+02:00", ReviewTime.toJerusalemIso("01/01/2026 12:00", "2026-01-01T12:00:47"))
        org.junit.Assert.assertEquals("2026-07-01T12:00:47.500+03:00", ReviewTime.toJerusalemIso("01/07/2026 12:00", "2026-07-01T12:00:47.5"))
        org.junit.Assert.assertEquals("2026-01-01T12:01+02:00", ReviewTime.toJerusalemIso("01/01/2026 12:01", "2026-01-01T12:00:47"))
        org.junit.Assert.assertNull(ReviewTime.toJerusalemIso("25/10/2026 01:30", "2026-10-25T01:30:47.5"))
        org.junit.Assert.assertEquals("2026-01-01T12:00:47+02:00", ReviewTime.toJerusalemIso("٠١/٠١/٢٠٢٦ ١٢:٠٠", "2026-01-01T12:00:47"))
    }
    @Test fun displayAndEditUseJerusalemForEveryRecordedOffset() {
        for (original in listOf("2026-01-01T10:00:47.123Z", "2026-01-01T15:30:47.123+05:30", "2026-01-01T12:00:47.123+02:00")) {
            assertEquals("01/01/2026 12:00", ReviewTime.display(original))
            assertEquals(original, ReviewTime.toJerusalemIso(ReviewTime.display(original), original))
            val edited = ReviewTime.toJerusalemIso("01/01/2026 12:01", original)
            assertEquals(OffsetDateTime.parse("2026-01-01T10:01:00Z").toInstant(), OffsetDateTime.parse(edited).toInstant())
        }
        assertEquals("01/01/2027 00:59", ReviewTime.display("2026-12-31T23:59:00+01:00"))
    }
    @Test fun jerusalemConfirmationUsesDateSpecificZoneAndRefusesDSTGapOverlap() {
        assertEquals("2026-01-01T12:00+02:00", ReviewTime.toJerusalemIso("01/01/2026 12:00", null))
        assertEquals("2026-07-01T12:00+03:00", ReviewTime.toJerusalemIso("01/07/2026 12:00", null))
        assertNull(ReviewTime.toJerusalemIso("27/03/2026 02:30", null))
        assertNull(ReviewTime.toJerusalemIso("25/10/2026 01:30", null))
        for (bad in listOf("31/02/2026 12:00", "01/01/2026 24:00", "01/01/2026 12:00 PM", "2026-01-01T12:00:00Z"))
            assertNull(ReviewTime.toJerusalemIso(bad, null))
    }
}
