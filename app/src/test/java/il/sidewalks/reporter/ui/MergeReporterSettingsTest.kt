package il.sidewalks.reporter.ui

import il.sidewalks.reporter.security.ReporterSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeReporterSettingsTest {
    private val stored = ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid",
        municipalSubscriptionKey = "synthetic-key", municipalUsername = "synthetic-user", municipalPassword = "synthetic-pass")

    @Test
    fun blankFieldsKeepStoredValues() {
        val cleared = ReporterSettings("", "", "", "", "")
        assertEquals(stored, mergeReporterSettings(cleared, stored))
    }

    @Test
    fun typedFieldsWinOverStored() {
        val merged = mergeReporterSettings(ReporterSettings("", "", "0500000001", "", ""), stored)
        assertEquals("0500000001", merged.phone)
        assertEquals("א", merged.firstName)
        assertEquals("synthetic-pass", merged.municipalPassword)
    }

    @Test
    fun nullStoredKeepsSubmittedValues() {
        val edited = ReporterSettings("א", "", "", "", "")
        assertEquals(edited, mergeReporterSettings(edited, null))
    }

    @Test
    fun whitespaceOnlyCountsAsBlank() {
        val merged = mergeReporterSettings(ReporterSettings(" ", "", "", "", ""), stored)
        assertEquals("א", merged.firstName)
    }
}
