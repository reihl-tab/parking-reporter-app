package il.sidewalks.reporter.ui

import il.sidewalks.reporter.security.ReporterSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeReporterSettingsTest {
    private val stored = ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid",
        municipalSubscriptionKey = "synthetic-key", municipalUsername = "synthetic-user", municipalPassword = "synthetic-pass")

    @Test
    fun blankPersonalFieldsKeepStoredValues() {
        val cleared = ReporterSettings("", "", "", "", "")
        val merged = mergeReporterSettings(cleared, stored)
        assertEquals("א", merged.firstName)
        assertEquals("050", merged.phone)
        assertEquals("a@b.invalid", merged.email)
    }

    @Test
    fun blankMunicipalFieldsClearStoredValues() {
        // Owner 2026-11-09: clearing a municipal credential field and saving
        // removes the stored value - no dedicated delete action.
        val cleared = ReporterSettings("", "", "", "", "")
        val merged = mergeReporterSettings(cleared, stored)
        assertEquals("", merged.municipalSubscriptionKey)
        assertEquals("", merged.municipalUsername)
        assertEquals("", merged.municipalPassword)
        val partial = mergeReporterSettings(
            ReporterSettings("", "", "", "", "", municipalUsername = "new-user"), stored)
        assertEquals("", partial.municipalSubscriptionKey)
        assertEquals("new-user", partial.municipalUsername)
        assertEquals("", partial.municipalPassword)
    }

    @Test
    fun typedFieldsWinOverStored() {
        val merged = mergeReporterSettings(
            ReporterSettings("", "", "0500000001", "", "", municipalPassword = "new-pass"), stored)
        assertEquals("0500000001", merged.phone)
        assertEquals("א", merged.firstName)
        assertEquals("new-pass", merged.municipalPassword)
        assertEquals("", merged.municipalSubscriptionKey)
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

    @Test
    fun untouchedMunicipalFieldsKeepStoredValues() {
        // Only a field the user actually edited may clear a stored credential.
        val cleared = ReporterSettings("", "", "", "", "")
        val merged = mergeReporterSettings(cleared, stored, emptySet())
        assertEquals("synthetic-key", merged.municipalSubscriptionKey)
        assertEquals("synthetic-user", merged.municipalUsername)
        assertEquals("synthetic-pass", merged.municipalPassword)
        val touched = mergeReporterSettings(cleared, stored, setOf("api_password"))
        assertEquals("", touched.municipalPassword)
        assertEquals("synthetic-key", touched.municipalSubscriptionKey)
        assertEquals("synthetic-user", touched.municipalUsername)
    }
}
