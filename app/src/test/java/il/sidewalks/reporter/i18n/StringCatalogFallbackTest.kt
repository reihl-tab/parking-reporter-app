package il.sidewalks.reporter.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fallback chain, placeholders and malformed data on a small synthetic catalog. */
class StringCatalogFallbackTest {

    private val json = """
        {
          "languages": [
            {"code": "he", "name": "עברית", "dir": "rtl", "default": true},
            {"code": "en", "name": "English", "dir": "ltr"},
            {"code": "ar", "name": "العربية", "dir": "rtl"},
            {"code": "ru", "name": "Русский", "dir": "ltr"},
            {"code": "xx", "name": "bad dir", "dir": "up"},
            {"name": "no code", "dir": "ltr"},
            "not an object"
          ],
          "strings": {
            "all": {"he": "א", "en": "B", "ar": "ج", "ru": "Д"},
            "no.ru": {"he": "שלום", "en": "Hello"},
            "only.en": {"en": "English only"},
            "only.he": {"he": "עברית בלבד", "en": "English too"},
            "only.he.strict": {"he": "עברית בלבד"},
            "empty.ru": {"he": "ריק", "ru": ""},
            "bad.types": {"ru": 42, "he": ["x"], "en": "fallback en"},
            "null.ru": {"ru": null, "he": "עברית"},
            "all.bad": {"ru": 1, "he": false, "en": {}},
            "not.object": "just a string",
            "greet": {"he": "שלום {name}", "en": "Hi {name}, {unknown}"},
            "plural.ru.partial": {
              "ru": {"one": "{count} один"},
              "he": {"one": "אחד", "two": "שניים", "other": "{count} רבים"}
            },
            "plural.he.many": {"he": {"one": "אחד", "many": "{count} רבים"}},
            "plural.he.only": {"he": {"one": "אחד", "two": "שניים", "other": "{count} רבים"}},
            "plain.used.as.plural": {"en": "{count} things"}
          }
        }
    """.trimIndent()

    private val c = StringCatalog.parse(json)

    @Test
    fun activeLanguageWinsWhenPresent() {
        assertEquals("Д", c.t("ru", "all"))
        assertEquals("B", c.t("en", "all"))
        assertEquals("א", c.t("he", "all"))
    }

    @Test
    fun missingInActiveFallsToHebrewBeforeEnglish() {
        assertEquals("שלום", c.t("ru", "no.ru"))
        assertEquals("עברית בלבד", c.t("ar", "only.he"))
    }

    @Test
    fun missingInActiveAndHebrewFallsToEnglish() {
        assertEquals("English only", c.t("ru", "only.en"))
        assertEquals("English only", c.t("he", "only.en"))
    }

    @Test
    fun englishActiveFallsToHebrew() {
        assertEquals("עברית בלבד", c.t("en", "only.he.strict"))
    }

    @Test
    fun missingEverywhereShowsTheKey() {
        assertEquals("no.such.key", c.t("ru", "no.such.key"))
        assertEquals("all.bad", c.t("ru", "all.bad"))
        assertEquals("not.object", c.t("ru", "not.object"))
        assertEquals("no.such.key", c.tp("ru", "no.such.key", 3))
        assertEquals("all.bad", c.tp("ru", "all.bad", 3))
    }

    @Test
    fun unknownLanguageCodeUsesTheChain() {
        assertEquals("א", c.t("fr", "all"))
        assertEquals("English only", c.t("fr", "only.en"))
    }

    @Test
    fun emptyAndWronglyTypedValuesFallThrough() {
        assertEquals("ריק", c.t("ru", "empty.ru"))
        assertEquals("fallback en", c.t("ru", "bad.types"))
        assertEquals("עברית", c.t("ru", "null.ru"))
    }

    @Test
    fun unknownAndMissingPlaceholdersStayVisible() {
        assertEquals("Hi Dana, {unknown}", c.t("en", "greet", mapOf("name" to "Dana")))
        assertEquals("Hi {name}, {unknown}", c.t("en", "greet"))
        assertEquals("Hi {name}, {unknown}", c.t("en", "greet", mapOf("name" to null)))
        assertEquals("שלום {name}", c.t("he", "greet"))
    }

    @Test
    fun parametersAreInterpolatedInOrderOfAppearance() {
        assertEquals("Hi 7, {unknown}", c.t("en", "greet", mapOf("name" to 7, "other" to "x")))
    }

    @Test
    fun malformedDocumentsNeverThrow() {
        for (bad in listOf("", "{not json", "[]", "null", """{"languages": "x", "strings": []}""", """{"strings": {"a": 5}}""")) {
            val cat = StringCatalog.parse(bad)
            assertEquals("a", cat.t("he", "a"))
            assertEquals("a", cat.tp("he", "a", 2))
        }
        assertEquals("k", StringCatalog.EMPTY.t("he", "k"))
    }

    @Test
    fun malformedLanguageEntriesAreSkipped() {
        assertEquals(listOf("he", "en", "ar", "ru"), c.languages.map { it.code })
        assertEquals("he", c.defaultLanguage?.code)
    }

    @Test
    fun pluralMissingCategoryFallsToOtherThenNextLanguage() {
        assertEquals("1 один", c.tp("ru", "plural.ru.partial", 1))
        // ru has no "many" and no "other" for 5: next in the chain is he, whose rule gives other.
        assertEquals("5 רבים", c.tp("ru", "plural.ru.partial", 5))
        // he picks its own category for the count: 2 -> two.
        assertEquals("שניים", c.tp("ru", "plural.ru.partial", 2))
    }

    @Test
    fun hebrewManyFormIsAcceptedAsOther() {
        assertEquals("${I18nTestFiles.LRI}5${I18nTestFiles.PDI} רבים", c.tp("he", "plural.he.many", 5))
        assertEquals("אחד", c.tp("he", "plural.he.many", 1))
    }

    @Test
    fun pluralObjectReadThroughTUsesOther() {
        assertEquals("{count} רבים", c.t("he", "plural.he.only"))
    }

    @Test
    fun plainStringReadThroughTpStillInterpolatesCount() {
        assertEquals("3 things", c.tp("en", "plain.used.as.plural", 3))
        assertEquals("9 things", c.tp("en", "plain.used.as.plural", 3, mapOf("count" to 9)))
    }

    @Test
    fun stringsWrapperBindsTheLanguage() {
        val s = Strings(c, "ru")
        assertEquals("Д", s.t("all"))
        assertEquals("1 один", s.tp("plural.ru.partial", 1))
        assertEquals("1 один", s.tp("plural.ru.partial", 1L))
        assertTrue(c.keys.contains("greet"))
    }
}
