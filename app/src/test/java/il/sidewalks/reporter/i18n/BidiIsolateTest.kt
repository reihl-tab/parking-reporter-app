package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.i18n.I18nTestFiles.FSI
import il.sidewalks.reporter.i18n.I18nTestFiles.LRI
import il.sidewalks.reporter.i18n.I18nTestFiles.PDI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unicode isolates around interpolated values in RTL languages only. Synthetic plates only. */
class BidiIsolateTest {

    private val c = I18nTestFiles.realCatalog()
    private val plate = mapOf("plate" to "12-345-67")
    private val isolates = listOf('\u2066', '\u2067', '\u2068', '\u2069')

    @Test
    fun plateInHebrewSentenceIsWrappedInLri() {
        assertEquals("מספר רכב: ${LRI}12-345-67$PDI", c.t("he", "common.plate_label", plate))
    }

    @Test
    fun plateInArabicSentenceIsWrappedInLri() {
        assertEquals("رقم اللوحة: ${LRI}12-345-67$PDI", c.t("ar", "common.plate_label", plate))
    }

    @Test
    fun rtlValueIsWrappedInFsi() {
        assertEquals("رقم اللوحة: ${FSI}مرحبا$PDI", c.t("ar", "common.plate_label", mapOf("plate" to "مرحبا")))
        assertEquals("מספר רכב: ${FSI}שלום 12$PDI", c.t("he", "common.plate_label", mapOf("plate" to "שלום 12")))
    }

    @Test
    fun ltrLanguagesGetNoIsolates() {
        for (code in listOf("en", "ru", "am")) {
            val out = c.t(code, "common.plate_label", plate)
            assertTrue(out.endsWith("12-345-67"))
            assertFalse("$code: $out", out.any { it in isolates })
        }
        assertEquals("Plate number: 12-345-67", c.t("en", "common.plate_label", plate))
        assertEquals("Plate number: שלום", c.t("en", "common.plate_label", mapOf("plate" to "שלום")))
    }

    @Test
    fun templateTextItselfIsNeverWrapped() {
        val out = c.t("he", "common.ok")
        assertFalse(out.any { it in isolates })
    }

    @Test
    fun strongRtlDetection() {
        assertTrue(Bidi.hasStrongRtl("א"))
        assertTrue(Bidi.hasStrongRtl("abc ب"))
        assertFalse(Bidi.hasStrongRtl("12-345-67"))
        assertFalse(Bidi.hasStrongRtl("Hello"))
        assertFalse(Bidi.hasStrongRtl(""))
        assertEquals("${LRI}AB 1$PDI", Bidi.isolate("AB 1"))
        assertEquals("${FSI}א$PDI", Bidi.isolate("א"))
    }
}
