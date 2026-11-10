package il.sidewalks.reporter

import il.sidewalks.reporter.ui.ReporterInput
import org.junit.Assert.*
import org.junit.Test

class ReporterInputTest {
    @Test fun optionalLeadingPlusDigitsAndIsraeliCanonicalization() {
        assertEquals("+972501234567", ReporterInput.canonicalPhone("050-123-4567"))
        assertEquals("+14155552671", ReporterInput.phoneInput("+1(415)-555-2671"))
        assertEquals("123", ReporterInput.phoneInput("1+2x3"))
        assertEquals("+972501234567", ReporterInput.phoneInput("+٩٧٢٥٠١٢٣٤٥٦٧"))
        assertFalse(ReporterInput.validPhone("123"))
        assertTrue(ReporterInput.validPhone("+14155552671"))
    }
    @Test fun namesAllowLanguagesButNotDigitsAndEmailRejectsMalformedAddresses() {
        assertEquals("בדיקה John محمد 李", ReporterInput.nameInput("בדיקה1 John٢ محمد۳ 李"))
        for (good in listOf("a+b@example.com", "name.last@sub.example.org", "אדם@example.co.il")) assertTrue(good, ReporterInput.validEmail(good))
        for (bad in listOf("a@@example.com", "a..b@example.com", "a@example", "a@-example.com", "a@exam_ple.com", "a b@example.com")) assertFalse(bad, ReporterInput.validEmail(bad))
    }
    @Test fun idChecksumRejectsWrongDigitsAndZeroId() {
        assertTrue(ReporterInput.validId("000000018"))
        assertTrue(ReporterInput.validId("18"))
        assertFalse(ReporterInput.validId("000000019"))
        assertFalse(ReporterInput.validId("000000000"))
        assertFalse(ReporterInput.validId("1234567890"))
        assertEquals("123", ReporterInput.digits("١٢3-x"))
    }
}
