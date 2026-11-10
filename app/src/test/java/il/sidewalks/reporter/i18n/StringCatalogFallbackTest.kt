package il.sidewalks.reporter.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fallback chain, placeholders and malformed data on a small synthetic catalog. Its codes
 * (xh, xe, xa, xr) are deliberately no real language: real languages live only in
 * assets/i18n/strings.json. xh plays the default (Hebrew-like, RTL), xe the `fallback`
 * language (English-like), xa an RTL and xr an LTR language with a Slavic plural.
 */
class StringCatalogFallbackTest {

    private val json = """
        {
          "languages": [
            {"code": "xh", "name": "עברית", "dir": "rtl", "default": true, "plural": "one_two_other"},
            {"code": "xe", "name": "English", "dir": "ltr", "plural": "one_is_1", "fallback": true},
            {"code": "xa", "name": "العربية", "dir": "rtl", "plural": "arabic_6"},
            {"code": "xr", "name": "Русский", "dir": "ltr", "plural": "slavic_one_few_many"},
            {"code": "xx", "name": "bad dir", "dir": "up"},
            {"name": "no code", "dir": "ltr"},
            "not an object"
          ],
          "strings": {
            "all": {"xh": "א", "xe": "B", "xa": "ج", "xr": "Д"},
            "no.ru": {"xh": "שלום", "xe": "Hello"},
            "only.en": {"xe": "English only"},
            "only.he": {"xh": "עברית בלבד", "xe": "English too"},
            "only.he.strict": {"xh": "עברית בלבד"},
            "empty.ru": {"xh": "ריק", "xr": ""},
            "bad.types": {"xr": 42, "xh": ["x"], "xe": "fallback en"},
            "null.ru": {"xr": null, "xh": "עברית"},
            "all.bad": {"xr": 1, "xh": false, "xe": {}},
            "not.object": "just a string",
            "greet": {"xh": "שלום {name}", "xe": "Hi {name}, {unknown}"},
            "plural.ru.partial": {
              "xr": {"one": "{count} один"},
              "xh": {"one": "אחד", "two": "שניים", "other": "{count} רבים"}
            },
            "plural.he.many": {"xh": {"one": "אחד", "many": "{count} רבים"}},
            "plural.he.only": {"xh": {"one": "אחד", "two": "שניים", "other": "{count} רבים"}},
            "plain.used.as.plural": {"xe": "{count} things"}
          }
        }
    """.trimIndent()

    private val c = StringCatalog.parse(json)

    @Test
    fun activeLanguageWinsWhenPresent() {
        assertEquals("Д", c.t("xr", "all"))
        assertEquals("B", c.t("xe", "all"))
        assertEquals("א", c.t("xh", "all"))
    }

    @Test
    fun missingInActiveFallsToTheDefaultBeforeTheFallbackLanguage() {
        assertEquals("שלום", c.t("xr", "no.ru"))
        assertEquals("עברית בלבד", c.t("xa", "only.he"))
    }

    @Test
    fun missingInActiveAndDefaultFallsToTheFallbackLanguage() {
        assertEquals("English only", c.t("xr", "only.en"))
        assertEquals("English only", c.t("xh", "only.en"))
    }

    @Test
    fun fallbackLanguageActiveFallsToTheDefault() {
        assertEquals("עברית בלבד", c.t("xe", "only.he.strict"))
    }

    @Test
    fun missingEverywhereShowsTheKey() {
        assertEquals("no.such.key", c.t("xr", "no.such.key"))
        assertEquals("all.bad", c.t("xr", "all.bad"))
        assertEquals("not.object", c.t("xr", "not.object"))
        assertEquals("no.such.key", c.tp("xr", "no.such.key", 3))
        assertEquals("all.bad", c.tp("xr", "all.bad", 3))
    }

    @Test
    fun unknownLanguageCodeUsesTheChain() {
        assertEquals("א", c.t("zz", "all"))
        assertEquals("English only", c.t("zz", "only.en"))
    }

    @Test
    fun emptyAndWronglyTypedValuesFallThrough() {
        assertEquals("ריק", c.t("xr", "empty.ru"))
        assertEquals("fallback en", c.t("xr", "bad.types"))
        assertEquals("עברית", c.t("xr", "null.ru"))
    }

    @Test
    fun unknownAndMissingPlaceholdersStayVisible() {
        assertEquals("Hi Dana, {unknown}", c.t("xe", "greet", mapOf("name" to "Dana")))
        assertEquals("Hi {name}, {unknown}", c.t("xe", "greet"))
        assertEquals("Hi {name}, {unknown}", c.t("xe", "greet", mapOf("name" to null)))
        assertEquals("שלום {name}", c.t("xh", "greet"))
    }

    @Test
    fun parametersAreInterpolatedInOrderOfAppearance() {
        assertEquals("Hi 7, {unknown}", c.t("xe", "greet", mapOf("name" to 7, "other" to "x")))
    }

    @Test
    fun malformedDocumentsNeverThrow() {
        for (bad in listOf("", "{not json", "[]", "null", """{"languages": "x", "strings": []}""", """{"strings": {"a": 5}}""")) {
            val cat = StringCatalog.parse(bad)
            assertEquals("a", cat.t("xh", "a"))
            assertEquals("a", cat.tp("xh", "a", 2))
        }
        assertEquals("k", StringCatalog.EMPTY.t("xh", "k"))
    }

    @Test
    fun malformedLanguageEntriesAreSkipped() {
        assertEquals(listOf("xh", "xe", "xa", "xr"), c.languages.map { it.code })
        assertEquals("xh", c.defaultLanguage?.code)
    }

    @Test
    fun pluralMissingCategoryFallsToOtherThenNextLanguage() {
        assertEquals("1 один", c.tp("xr", "plural.ru.partial", 1))
        // ru has no "many" and no "other" for 5: next in the chain is he, whose rule gives other.
        assertEquals("5 רבים", c.tp("xr", "plural.ru.partial", 5))
        // he picks its own category for the count: 2 -> two.
        assertEquals("שניים", c.tp("xr", "plural.ru.partial", 2))
    }

    @Test
    fun oneTwoOtherFamilyAcceptsAManyFormAsOther() {
        assertEquals("${I18nTestFiles.LRI}5${I18nTestFiles.PDI} רבים", c.tp("xh", "plural.he.many", 5))
        assertEquals("אחד", c.tp("xh", "plural.he.many", 1))
    }

    @Test
    fun pluralObjectReadThroughTUsesOther() {
        assertEquals("{count} רבים", c.t("xh", "plural.he.only"))
    }

    @Test
    fun plainStringReadThroughTpStillInterpolatesCount() {
        assertEquals("3 things", c.tp("xe", "plain.used.as.plural", 3))
        assertEquals("9 things", c.tp("xe", "plain.used.as.plural", 3, mapOf("count" to 9)))
    }

    @Test
    fun stringsWrapperBindsTheLanguage() {
        val s = Strings(c, "xr")
        assertEquals("Д", s.t("all"))
        assertEquals("1 один", s.tp("plural.ru.partial", 1))
        assertEquals("1 один", s.tp("plural.ru.partial", 1L))
        assertTrue(c.keys.contains("greet"))
    }

    @Test
    fun theChainIsReadFromTheCatalog() {
        // The same values with another default and another fallback language give another chain.
        val other = StringCatalog.parse(
            """
            {
              "languages": [
                {"code": "xa", "name": "A", "dir": "rtl", "plural": "arabic_6", "fallback": true},
                {"code": "xr", "name": "R", "dir": "ltr", "default": true, "plural": "slavic_one_few_many"},
                {"code": "xe", "name": "E", "dir": "ltr", "plural": "one_is_1"}
              ],
              "strings": {
                "k": {"xa": "from xa", "xr": "from xr"},
                "only.xa": {"xa": "xa only", "xe": "xe too"},
                "only.xe": {"xe": "xe only"}
              }
            }
            """.trimIndent(),
        )
        assertEquals("xr", other.defaultLanguage?.code)
        assertEquals("from xr", other.t("xe", "k"))
        assertEquals("xa only", other.t("xr", "only.xa"))
        // xe is neither active nor default nor a fallback: its value is never borrowed.
        assertEquals("only.xe", other.t("xr", "only.xe"))
        // No block marked default: the first block is the default.
        val noDefault = StringCatalog.parse("""{"languages": [{"code": "xe", "dir": "ltr"}, {"code": "xh", "dir": "rtl"}], "strings": {"k": {"xe": "e", "xh": "h"}}}""")
        assertEquals("xe", noDefault.defaultLanguage?.code)
        assertEquals("e", noDefault.t("zz", "k"))
    }

    @Test
    fun blocksCarryFlagsFamiliesAndAliases() {
        val parsed = StringCatalog.parse(
            """{"languages": [{"code": "xh", "dir": "rtl", "default": true, "machine_draft": false, "plural": "one_two_other", "aliases": ["xo", 3, ""]},
               {"code": "xe", "dir": "ltr", "machine_draft": true, "plural": "nonsense"}], "strings": {}}""",
        )
        val xh = parsed.language("xh")!!
        assertEquals(PluralFamily.ONE_TWO_OTHER, xh.plural)
        assertEquals(listOf("xo"), xh.aliases)
        assertEquals(false, xh.machineDraft)
        assertEquals(xh, parsed.languageOrAlias("xo"))
        val xe = parsed.language("xe")!!
        assertEquals(true, xe.machineDraft)
        assertEquals(null, xe.plural)
        assertEquals(null, parsed.languageOrAlias("zz"))
    }
}
