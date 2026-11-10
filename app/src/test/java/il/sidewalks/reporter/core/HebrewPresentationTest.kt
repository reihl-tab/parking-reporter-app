package il.sidewalks.reporter.core

import il.sidewalks.reporter.i18n.I18nTestFiles
import il.sidewalks.reporter.i18n.Strings
import il.sidewalks.reporter.ui.HebrewPresentation
import org.junit.Assert.*
import org.junit.Test

class HebrewPresentationTest {
    private val catalog = I18nTestFiles.realCatalog()
    private val he = Strings(catalog, "he")
    private val en = Strings(catalog, "en")

    @Test fun formatKeepsEvidenceOffsetAndDoesNotGuessTimezone() {
        assertEquals("01/01/2026 14:00", HebrewPresentation.evidenceTime(he, "2026-01-01T12:00:00Z"))
        assertEquals("01/01/2026 14:30", HebrewPresentation.evidenceTime(he, "2026-01-01T14:30:00+02:00"))
        assertEquals("01/01/2026 12:00 (אזור זמן לא ידוע)", HebrewPresentation.evidenceTime(he, "2026-01-01T12:00:00"))
        assertEquals("חסר", HebrewPresentation.evidenceTime(he, null))
        assertEquals("unknown", HebrewPresentation.evidenceTime(he, "unknown"))
    }
    @Test fun categoryIsHebrewWithoutChangingTransportCode() {
        assertEquals("חניה על המדרכה", HebrewPresentation.category(he, "sidewalk_parking"))
        assertEquals("נדרש תיקון קטגוריה", HebrewPresentation.category(he, "unknown"))
        assertEquals("sidewalk_parking", ReportCategory.SIDEWALK_PARKING.code)
        assertEquals("1145", ReportCategory.SIDEWALK_PARKING.municipalSubjectCode)
    }
    /** Issue #115 PR-2c: the same values in another UI language (machine drafts). */
    @Test fun englishUiShowsTheEnglishLabels() {
        assertEquals("01/01/2026 14:00", HebrewPresentation.evidenceTime(en, "2026-01-01T12:00:00Z"))
        assertEquals("01/01/2026 12:00 (time zone unknown)", HebrewPresentation.evidenceTime(en, "2026-01-01T12:00:00"))
        assertEquals("missing", HebrewPresentation.evidenceTime(en, null))
        assertEquals("missing", HebrewPresentation.evidenceTime(en, "  "))
        assertEquals("Parking on the sidewalk", HebrewPresentation.category(en, "sidewalk_parking"))
        assertEquals("Category needs correcting", HebrewPresentation.category(en, "unknown"))
    }
}
