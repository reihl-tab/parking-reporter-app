package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.i18n.PluralCategory.FEW
import il.sidewalks.reporter.i18n.PluralCategory.MANY
import il.sidewalks.reporter.i18n.PluralCategory.ONE
import il.sidewalks.reporter.i18n.PluralCategory.OTHER
import il.sidewalks.reporter.i18n.PluralCategory.TWO
import il.sidewalks.reporter.i18n.PluralCategory.ZERO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CLDR integer plural tables at boundary numbers, one table per [PluralFamily]. Which family
 * a language uses is its catalog block's `plural` field (td424), pinned at the end.
 */
class PluralRulesTest {

    private val numbers = listOf(0L, 1, 2, 3, 5, 10, 11, 12, 14, 21, 22, 25, 99, 100, 101, 102, 111)

    private fun check(family: PluralFamily, expected: List<PluralCategory>) {
        assertEquals(numbers.size, expected.size)
        val actual = numbers.map { PluralRules.categoryOf(family, it) }
        assertEquals("${family.id} for $numbers", expected, actual)
    }

    @Test
    fun oneTwoOther() = check(
        PluralFamily.ONE_TWO_OTHER,
        //   0      1    2    3      5      10     11     12     14     21     22     25     99     100    101    102    111
        listOf(OTHER, ONE, TWO, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER),
    )

    @Test
    fun arabic6() = check(
        PluralFamily.ARABIC_6,
        listOf(ZERO, ONE, TWO, FEW, FEW, FEW, MANY, MANY, MANY, MANY, MANY, MANY, MANY, OTHER, OTHER, OTHER, MANY),
    )

    @Test
    fun slavicOneFewMany() = check(
        PluralFamily.SLAVIC_ONE_FEW_MANY,
        listOf(MANY, ONE, FEW, FEW, MANY, MANY, MANY, MANY, MANY, ONE, FEW, MANY, MANY, MANY, ONE, FEW, MANY),
    )

    @Test
    fun oneIs1() = check(
        PluralFamily.ONE_IS_1,
        listOf(OTHER, ONE, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER),
    )

    @Test
    fun oneIs01() = check(
        PluralFamily.ONE_IS_0_1,
        listOf(ONE, ONE, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER),
    )

    @Test
    fun noFamilyCountsOneAsOne() {
        assertEquals(ONE, PluralRules.categoryOf(null, 1))
        assertEquals(OTHER, PluralRules.categoryOf(null, 0))
        assertEquals(OTHER, PluralRules.categoryOf(null, 2))
        assertEquals(null, PluralFamily.byId("no_such_family"))
        assertEquals(null, PluralFamily.byId(null))
        for (family in PluralFamily.entries) assertEquals(family, PluralFamily.byId(family.id))
    }

    @Test
    fun moreArabicAndSlavicEdges() {
        assertEquals(FEW, PluralRules.categoryOf(PluralFamily.ARABIC_6, 103))
        assertEquals(FEW, PluralRules.categoryOf(PluralFamily.ARABIC_6, 110))
        assertEquals(MANY, PluralRules.categoryOf(PluralFamily.ARABIC_6, 199))
        assertEquals(OTHER, PluralRules.categoryOf(PluralFamily.ARABIC_6, 200))
        assertEquals(MANY, PluralRules.categoryOf(PluralFamily.SLAVIC_ONE_FEW_MANY, 112))
        assertEquals(FEW, PluralRules.categoryOf(PluralFamily.SLAVIC_ONE_FEW_MANY, 124))
        assertEquals(ONE, PluralRules.categoryOf(PluralFamily.SLAVIC_ONE_FEW_MANY, 1001))
        assertEquals(ONE, PluralRules.categoryOf(PluralFamily.ONE_TWO_OTHER, -1))
    }

    /** The family each shipped language names in its block, by its own name. */
    @Test
    fun catalogLanguagesNameTheirFamilies() {
        val c = I18nTestFiles.realCatalog()
        val pinned = mapOf(
            "עברית" to PluralFamily.ONE_TWO_OTHER,
            "English" to PluralFamily.ONE_IS_1,
            "العربية" to PluralFamily.ARABIC_6,
            "Русский" to PluralFamily.SLAVIC_ONE_FEW_MANY,
            "አማርኛ" to PluralFamily.ONE_IS_0_1,
        )
        for ((name, family) in pinned) assertEquals(name, family, c.language(I18nTestFiles.codeNamed(name))!!.plural)
    }

    @Test
    fun realCatalogPluralForms() {
        val c = I18nTestFiles.realCatalog()
        val lri = I18nTestFiles.LRI
        val pdi = I18nTestFiles.PDI
        val he = I18nTestFiles.codeNamed("עברית")
        val ar = I18nTestFiles.codeNamed("العربية")
        val ru = I18nTestFiles.codeNamed("Русский")
        val en = I18nTestFiles.codeNamed("English")
        val am = I18nTestFiles.codeNamed("አማርኛ")
        assertEquals("דיווח אחד", c.tp(he, "common.reports_count", 1))
        assertEquals("שני דיווחים", c.tp(he, "common.reports_count", 2))
        assertEquals("${lri}5$pdi דיווחים", c.tp(he, "common.reports_count", 5))
        assertEquals("لا توجد بلاغات", c.tp(ar, "common.reports_count", 0))
        assertEquals("بلاغان", c.tp(ar, "common.reports_count", 2))
        assertEquals("${lri}3$pdi بلاغات", c.tp(ar, "common.reports_count", 3))
        assertEquals("${lri}11$pdi بلاغًا", c.tp(ar, "common.reports_count", 11))
        assertEquals("${lri}100$pdi بلاغ", c.tp(ar, "common.reports_count", 100))
        assertEquals("21 обращение", c.tp(ru, "common.reports_count", 21))
        assertEquals("22 обращения", c.tp(ru, "common.reports_count", 22))
        assertEquals("25 обращений", c.tp(ru, "common.reports_count", 25))
        assertEquals("1 report", c.tp(en, "common.reports_count", 1))
        assertEquals("0 reports", c.tp(en, "common.reports_count", 0))
        assertEquals("0 ሪፖርት", c.tp(am, "common.reports_count", 0))
        assertEquals("2 ሪፖርቶች", c.tp(am, "common.reports_count", 2))
    }
}
