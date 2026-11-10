package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.i18n.I18nTestFiles.FSI
import il.sidewalks.reporter.i18n.I18nTestFiles.LRI
import il.sidewalks.reporter.i18n.I18nTestFiles.PDI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unicode isolates around interpolated values: every value in an RTL language, and in an
 * LTR language only a value with a strong-RTL character (td424, the optional bidi item).
 * Languages are taken from the catalog by direction. Synthetic plates only.
 */
class BidiIsolateTest {

    private val c = I18nTestFiles.realCatalog()
    private val plate = mapOf("plate" to "12-345-67")
    private val isolates = listOf('\u2066', '\u2067', '\u2068', '\u2069')
    private val he = I18nTestFiles.codeNamed("עברית")
    private val ar = I18nTestFiles.codeNamed("العربية")
    private val en = I18nTestFiles.codeNamed("English")
    private val rtl = c.languages.filter { it.dir == TextDir.RTL }.map { it.code }
    private val ltr = c.languages.filter { it.dir == TextDir.LTR }.map { it.code }

    @Test
    fun theCatalogHasBothDirections() {
        assertTrue(rtl.isNotEmpty() && ltr.isNotEmpty())
    }

    @Test
    fun plateInHebrewSentenceIsWrappedInLri() {
        assertEquals("מספר רכב: ${LRI}12-345-67$PDI", c.t(he, "common.plate_label", plate))
    }

    @Test
    fun plateInArabicSentenceIsWrappedInLri() {
        assertEquals("رقم اللوحة: ${LRI}12-345-67$PDI", c.t(ar, "common.plate_label", plate))
    }

    @Test
    fun everyRtlLanguageIsolatesEveryValue() {
        for (code in rtl) {
            assertTrue(code, c.t(code, "common.plate_label", plate).endsWith("${LRI}12-345-67$PDI"))
            assertTrue(code, c.t(code, "common.plate_label", mapOf("plate" to "שלום")).endsWith("${FSI}שלום$PDI"))
        }
    }

    @Test
    fun rtlValueIsWrappedInFsi() {
        assertEquals("رقم اللوحة: ${FSI}مرحبا$PDI", c.t(ar, "common.plate_label", mapOf("plate" to "مرحبا")))
        assertEquals("מספר רכב: ${FSI}שלום 12$PDI", c.t(he, "common.plate_label", mapOf("plate" to "שלום 12")))
    }

    @Test
    fun ltrLanguagesLeaveLtrValuesBare() {
        for (code in ltr) {
            val out = c.t(code, "common.plate_label", plate)
            assertTrue(out.endsWith("12-345-67"))
            assertFalse("$code: $out", out.any { it in isolates })
        }
        assertEquals("Plate number: 12-345-67", c.t(en, "common.plate_label", plate))
    }

    @Test
    fun ltrLanguagesIsolateAValueWithStrongRtl() {
        // A Hebrew street name (or a Hebrew draft value) inside an LTR sentence keeps its order.
        for (code in ltr) {
            val out = c.t(code, "common.plate_label", mapOf("plate" to "רחוב יפו 5"))
            assertTrue("$code: $out", out.endsWith("${FSI}רחוב יפו 5$PDI"))
            assertEquals("$code: one isolate pair", 2, out.count { it in isolates })
        }
        assertEquals("Plate number: ${FSI}שלום$PDI", c.t(en, "common.plate_label", mapOf("plate" to "שלום")))
        assertEquals("Plate number: ${FSI}مرحبا 12$PDI", c.t(en, "common.plate_label", mapOf("plate" to "مرحبا 12")))
    }

    @Test
    fun templateTextItselfIsNeverWrapped() {
        for (code in c.languages.map { it.code }) {
            assertFalse(code, c.t(code, "common.ok").any { it in isolates })
        }
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
