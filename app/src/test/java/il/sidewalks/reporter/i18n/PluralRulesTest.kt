package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.i18n.PluralCategory.FEW
import il.sidewalks.reporter.i18n.PluralCategory.MANY
import il.sidewalks.reporter.i18n.PluralCategory.ONE
import il.sidewalks.reporter.i18n.PluralCategory.OTHER
import il.sidewalks.reporter.i18n.PluralCategory.TWO
import il.sidewalks.reporter.i18n.PluralCategory.ZERO
import org.junit.Assert.assertEquals
import org.junit.Test

/** CLDR integer plural tables at boundary numbers, one table per language. */
class PluralRulesTest {

    private val numbers = listOf(0L, 1, 2, 3, 5, 10, 11, 12, 14, 21, 22, 25, 99, 100, 101, 102, 111)

    private fun check(code: String, expected: List<PluralCategory>) {
        assertEquals(numbers.size, expected.size)
        val actual = numbers.map { PluralRules.categoryOf(code, it) }
        assertEquals("$code for $numbers", expected, actual)
    }

    @Test
    fun hebrew() = check(
        "he",
        //   0      1    2    3      5      10     11     12     14     21     22     25     99     100    101    102    111
        listOf(OTHER, ONE, TWO, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER),
    )

    @Test
    fun arabic() = check(
        "ar",
        listOf(ZERO, ONE, TWO, FEW, FEW, FEW, MANY, MANY, MANY, MANY, MANY, MANY, MANY, OTHER, OTHER, OTHER, MANY),
    )

    @Test
    fun russian() = check(
        "ru",
        listOf(MANY, ONE, FEW, FEW, MANY, MANY, MANY, MANY, MANY, ONE, FEW, MANY, MANY, MANY, ONE, FEW, MANY),
    )

    @Test
    fun english() = check(
        "en",
        listOf(OTHER, ONE, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER),
    )

    @Test
    fun amharic() = check(
        "am",
        listOf(ONE, ONE, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER, OTHER),
    )

    @Test
    fun moreArabicAndRussianEdges() {
        assertEquals(FEW, PluralRules.categoryOf("ar", 103))
        assertEquals(FEW, PluralRules.categoryOf("ar", 110))
        assertEquals(MANY, PluralRules.categoryOf("ar", 199))
        assertEquals(OTHER, PluralRules.categoryOf("ar", 200))
        assertEquals(MANY, PluralRules.categoryOf("ru", 112))
        assertEquals(FEW, PluralRules.categoryOf("ru", 124))
        assertEquals(ONE, PluralRules.categoryOf("ru", 1001))
        assertEquals(ONE, PluralRules.categoryOf("he", -1))
    }

    @Test
    fun realCatalogPluralForms() {
        val c = I18nTestFiles.realCatalog()
        val lri = I18nTestFiles.LRI
        val pdi = I18nTestFiles.PDI
        assertEquals("דיווח אחד", c.tp("he", "common.reports_count", 1))
        assertEquals("שני דיווחים", c.tp("he", "common.reports_count", 2))
        assertEquals("${lri}5$pdi דיווחים", c.tp("he", "common.reports_count", 5))
        assertEquals("لا توجد بلاغات", c.tp("ar", "common.reports_count", 0))
        assertEquals("بلاغان", c.tp("ar", "common.reports_count", 2))
        assertEquals("${lri}3$pdi بلاغات", c.tp("ar", "common.reports_count", 3))
        assertEquals("${lri}11$pdi بلاغًا", c.tp("ar", "common.reports_count", 11))
        assertEquals("${lri}100$pdi بلاغ", c.tp("ar", "common.reports_count", 100))
        assertEquals("21 обращение", c.tp("ru", "common.reports_count", 21))
        assertEquals("22 обращения", c.tp("ru", "common.reports_count", 22))
        assertEquals("25 обращений", c.tp("ru", "common.reports_count", 25))
        assertEquals("1 report", c.tp("en", "common.reports_count", 1))
        assertEquals("0 reports", c.tp("en", "common.reports_count", 0))
        assertEquals("0 ሪፖርት", c.tp("am", "common.reports_count", 0))
        assertEquals("2 ሪፖርቶች", c.tp("am", "common.reports_count", 2))
    }
}
