package il.sidewalks.reporter.core

import androidx.compose.ui.text.AnnotatedString
import il.sidewalks.reporter.ui.PlateEntry
import org.junit.Assert.*
import org.junit.Test

class PlateEntryTest {
    @Test fun digitsOnlyAndBothLengths() {
        assertEquals("123456789", PlateEntry.digits("12-345-678x9"))
        assertEquals("12123", PlateEntry.digits("abc١٢123"))
        assertEquals("12345678", PlateEntry.digits("۱۲۳۴۵۶۷۸"))
        assertEquals("12-345-67", PlateEntry.format("1234567"))
        assertEquals("123-45-678", PlateEntry.format("12345678"))
        assertEquals("12-3", PlateEntry.format("123"))
    }
    @Test fun cursorMappingsRoundTripAtEveryLength() {
        for (length in 0..8) {
            val text = "12345678".take(length)
            val transformed = PlateEntry.filter(AnnotatedString(text))
            for (offset in 0..length) {
                val visual = transformed.offsetMapping.originalToTransformed(offset)
                assertTrue(visual in 0..transformed.text.length)
                assertEquals(offset, transformed.offsetMapping.transformedToOriginal(visual))
            }
            for (offset in 0..transformed.text.length)
                assertTrue(transformed.offsetMapping.transformedToOriginal(offset) in 0..length)
        }
    }
    private fun draft(plate: String = "") = ReviewDraft(
        Evidence("plate-test", "a".repeat(64), "2026-01-01T12:00:00+02:00", 31.0, 35.0),
        plate, "test 1", "sidewalk_parking", "synthetic", emptySet())
    @Test fun overlongInputNeverBecomesDifferentValidPlate() {
        val value = PlateEntry.digits("123456789")
        assertEquals("123456789", value)
        assertFalse(draft(value).isReady())
    }
    @Test fun nonPlateCategoryNeverPrefills() {
        val d = draft().copy(subject = "red_white_parking")
        // Explicit plate-less category below, independent of parking category names.
        val noPlate = d.copy(subject = ReportCategory.entries.first { !it.needsPlate }.code)
        assertEquals(noPlate, PlateEntry.prefill(noPlate, listOf("1234567"), ""))
    }
    @Test fun oneOcrCandidatePrefillsDigitsButNeverApprovesOrOverwritesTyping() {
        val d = draft()
        val prefilled = PlateEntry.prefill(d, listOf("1234567"), "")
        assertEquals("1234567", prefilled.plate)
        assertNull(prefilled.approvedDigest)
        assertFalse(prefilled.isApproved())
        assertEquals(d, PlateEntry.prefill(d, listOf("1234567", "12345678"), ""))
        assertEquals(d, PlateEntry.prefill(d, listOf("bad"), ""))
        val typed = draft("7654321")
        assertEquals(typed, PlateEntry.prefill(typed, listOf("1234567"), ""))
        assertEquals(typed, PlateEntry.prefill(typed, listOf("1234567"), "7654321"))
    }
}
