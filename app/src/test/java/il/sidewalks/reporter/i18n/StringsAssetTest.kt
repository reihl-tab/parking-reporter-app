package il.sidewalks.reporter.i18n

import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coverage and direction checks on the real assets/i18n/strings.json (issue #115). */
/** android.jar's org.json (the compile classpath) has keys() but no keySet(). */
private fun JSONObject.keySetCompat(): Set<String> = keys().asSequence().toSet()

class StringsAssetTest {

    private val expectedDirs = linkedMapOf("he" to "rtl", "en" to "ltr", "ar" to "rtl", "ru" to "ltr", "am" to "ltr")
    private val expectedNames = mapOf(
        "he" to "עברית",
        "en" to "English",
        "ar" to "العربية",
        "ru" to "Русский",
        "am" to "አማርኛ",
    )
    private val machineDraft = "machine-draft, needs native review"

    private val raw = JSONObject(I18nTestFiles.realCatalogText())
    private val catalog = I18nTestFiles.realCatalog()

    private fun placeholders(text: String): Set<String> =
        StringCatalog.PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toSet()

    @Test
    fun languagesBlockDeclaresTheFiveLanguagesWithExpectedDirections() {
        val langs = raw.getJSONArray("languages")
        val seen = linkedMapOf<String, String>()
        for (i in 0 until langs.length()) {
            val l = langs.getJSONObject(i)
            seen[l.getString("code")] = l.getString("dir")
            assertEquals("native name of ${l.getString("code")}", expectedNames[l.getString("code")], l.getString("name"))
        }
        assertEquals(expectedDirs, seen)
        assertEquals(expectedDirs.keys.toList(), catalog.languages.map { it.code })
    }

    @Test
    fun hebrewIsTheOnlyDefaultAndEveryOtherLanguageIsAMachineDraft() {
        val langs = raw.getJSONArray("languages")
        for (i in 0 until langs.length()) {
            val l = langs.getJSONObject(i)
            val code = l.getString("code")
            if (code == "he") {
                assertTrue(l.getBoolean("default"))
                assertFalse("he is the source language", l.has("status"))
            } else {
                assertFalse("$code must not be default", l.optBoolean("default", false))
                assertEquals("$code status", machineDraft, l.getString("status"))
            }
        }
        assertEquals("he", catalog.defaultLanguage?.code)
    }

    @Test
    fun languageDirectionsMapToLayoutDirection() {
        assertEquals(LayoutDirection.Rtl, catalog.language("he")?.layoutDirection)
        assertEquals(LayoutDirection.Rtl, catalog.language("ar")?.layoutDirection)
        assertEquals(LayoutDirection.Ltr, catalog.language("en")?.layoutDirection)
        assertEquals(LayoutDirection.Ltr, catalog.language("ru")?.layoutDirection)
        assertEquals(LayoutDirection.Ltr, catalog.language("am")?.layoutDirection)
        assertEquals(LayoutDirection.Rtl, Language("x", "x", TextDir.RTL, false, null).layoutDirection)
        assertEquals(LayoutDirection.Ltr, Language("y", "y", TextDir.LTR, false, null).layoutDirection)
    }

    @Test
    fun everyKeyIsPresentAndNonEmptyInAllFiveLanguages() {
        val strings = raw.getJSONObject("strings")
        assertTrue("seed keys present", strings.length() >= 6)
        for (key in strings.keys()) {
            assertTrue("key form screen.element.purpose: $key", Regex("""^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$""").matches(key))
            val entry = strings.getJSONObject(key)
            assertEquals("$key has exactly the five languages", expectedDirs.keys, entry.keySetCompat())
            for (code in expectedDirs.keys) {
                when (val v = entry.get(code)) {
                    is String -> assertTrue("$key/$code is empty", v.isNotBlank())
                    is JSONObject -> {
                        assertTrue("$key/$code plural is empty", v.length() > 0)
                        for (cat in v.keys()) {
                            assertTrue("$key/$code unknown category $cat", PluralCategory.entries.any { it.key == cat })
                            assertTrue("$key/$code/$cat is empty", v.getString(cat).isNotBlank())
                        }
                    }
                    else -> throw AssertionError("$key/$code has type ${v.javaClass.simpleName}")
                }
            }
        }
    }

    @Test
    fun pluralObjectsCarryTheCategoriesEachLanguageNeeds() {
        val strings = raw.getJSONObject("strings")
        var plurals = 0
        for (key in strings.keys()) {
            val entry = strings.getJSONObject(key)
            val isPlural = entry.get("he") is JSONObject
            for (code in expectedDirs.keys) {
                assertEquals("$key/$code plural-ness matches he", isPlural, entry.get(code) is JSONObject)
                if (!isPlural) continue
                val have = entry.getJSONObject(code).keySetCompat()
                val need = PluralRules.requiredCategories(code).map { it.key }.toSet()
                assertTrue("$key/$code needs $need, has $have", have.containsAll(need))
            }
            if (isPlural) plurals++
        }
        assertTrue("at least one plural key is seeded", plurals >= 1)
        assertEquals(setOf("one", "two", "other"), PluralRules.requiredCategories("he").map { it.key }.toSet())
        assertEquals(6, PluralRules.requiredCategories("ar").size)
        assertEquals(setOf("one", "few", "many", "other"), PluralRules.requiredCategories("ru").map { it.key }.toSet())
        assertEquals(setOf("one", "other"), PluralRules.requiredCategories("en").map { it.key }.toSet())
        assertEquals(setOf("one", "other"), PluralRules.requiredCategories("am").map { it.key }.toSet())
    }

    @Test
    fun placeholderSetsAreIdenticalAcrossLanguages() {
        val strings = raw.getJSONObject("strings")
        var withPlaceholders = 0
        for (key in strings.keys()) {
            val entry = strings.getJSONObject(key)
            val sets = expectedDirs.keys.associateWith { code ->
                when (val v = entry.get(code)) {
                    is JSONObject -> v.keySetCompat().flatMap { cat -> placeholders(v.getString(cat)) }.toSet()
                    else -> placeholders(v as String)
                }
            }
            assertEquals("$key placeholder sets differ: $sets", 1, sets.values.toSet().size)
            if (sets.getValue("he").isNotEmpty()) withPlaceholders++
        }
        assertTrue("a placeholder key is seeded", withPlaceholders >= 2)
    }

    @Test
    fun realCatalogRendersSeedKeys() {
        assertEquals("אישור", catalog.t("he", "common.ok"))
        assertEquals("OK", catalog.t("en", "common.ok"))
        assertEquals("Plate number: 12-345-67", catalog.t("en", "common.plate_label", mapOf("plate" to "12-345-67")))
        assertNull(catalog.rawEntry("no.such.key"))
    }
}
