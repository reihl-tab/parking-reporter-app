package il.sidewalks.reporter.i18n

import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage and direction checks on the real assets/i18n/strings.json (issue #115). Every
 * language list here is read from the catalog's `languages` block (td423/td424): adding a
 * language is one block plus one value per key, and these tests then cover it.
 */
/** android.jar's org.json (the compile classpath) has keys() but no keySet(). */
private fun JSONObject.keySetCompat(): Set<String> = keys().asSequence().toSet()

class StringsAssetTest {

    private val machineDraft = "machine-draft, needs native review"

    private val raw = JSONObject(I18nTestFiles.realCatalogText())
    private val catalog = I18nTestFiles.realCatalog()
    private val langs: JSONArray = raw.getJSONArray("languages")
    private val codes: List<String> = (0 until langs.length()).map { langs.getJSONObject(it).getString("code") }

    private val blockFields = setOf("code", "name", "dir", "default", "machine_draft", "plural", "status", "aliases", "fallback")

    private fun placeholders(text: String): Set<String> =
        StringCatalog.PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toSet()

    @Test
    fun everyLanguageIsOneCompleteBlock() {
        assertTrue("the catalog declares languages", codes.isNotEmpty())
        assertEquals("codes are unique", codes.size, codes.toSet().size)
        for (i in 0 until langs.length()) {
            val l = langs.getJSONObject(i)
            val code = l.getString("code")
            assertTrue("$code: unknown fields ${l.keySetCompat() - blockFields}", blockFields.containsAll(l.keySetCompat()))
            assertTrue("$code is a lower-case ISO 639 code", Regex("""^[a-z]{2,3}$""").matches(code))
            assertTrue("$code has its own name", l.getString("name").isNotBlank())
            assertTrue("$code dir", l.getString("dir") in setOf("rtl", "ltr"))
            assertTrue("$code declares default", l.get("default") is Boolean)
            assertTrue("$code declares machine_draft", l.get("machine_draft") is Boolean)
            assertNotNull("$code names a known plural family, not ${l.opt("plural")}", PluralFamily.byId(l.optString("plural")))
        }
        // The parsed catalog keeps every block, in order, with the same facts.
        assertEquals(codes, catalog.languages.map { it.code })
        for (language in catalog.languages) {
            val l = langs.getJSONObject(codes.indexOf(language.code))
            assertEquals(l.getString("name"), language.nativeName)
            assertEquals(if (l.getString("dir") == "rtl") TextDir.RTL else TextDir.LTR, language.dir)
            assertEquals(l.getBoolean("machine_draft"), language.machineDraft)
            assertEquals(PluralFamily.byId(l.getString("plural")), language.plural)
        }
    }

    @Test
    fun exactlyOneDefaultWhichIsTheSourceAndEveryOtherLanguageIsAMachineDraft() {
        val defaults = catalog.languages.filter { it.isDefault }
        assertEquals("exactly one default language", 1, defaults.size)
        val source = defaults.single()
        assertEquals(source, catalog.defaultLanguage)
        assertFalse("${source.code} is the source language", source.machineDraft)
        assertNull("${source.code} is the source language", source.status)
        for (language in catalog.languages - source) {
            assertTrue("${language.code} is a machine draft", language.machineDraft)
            assertEquals("${language.code} status", machineDraft, language.status)
        }
    }

    @Test
    fun aliasesAndFallbacksNameNoOtherCatalogLanguage() {
        val aliases = catalog.languages.flatMap { it.aliases }
        assertEquals("aliases are unique", aliases.size, aliases.toSet().size)
        assertTrue("an alias is never a catalog code", aliases.none { it in codes })
        assertFalse("the default is not also a fallback", catalog.defaultLanguage!!.isFallback)
    }

    @Test
    fun languageDirectionsMapToLayoutDirection() {
        for (language in catalog.languages) {
            val expected = if (language.dir == TextDir.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
            assertEquals(language.code, expected, language.layoutDirection)
        }
        // Direction pins for the languages shipped today, named by their own names.
        val pinned = mapOf("עברית" to TextDir.RTL, "English" to TextDir.LTR, "العربية" to TextDir.RTL, "Русский" to TextDir.LTR, "አማርኛ" to TextDir.LTR)
        for ((name, dir) in pinned) assertEquals(name, dir, catalog.language(I18nTestFiles.codeNamed(name))!!.dir)
        assertEquals(LayoutDirection.Rtl, Language("x", "x", TextDir.RTL, false, null).layoutDirection)
        assertEquals(LayoutDirection.Ltr, Language("y", "y", TextDir.LTR, false, null).layoutDirection)
    }

    @Test
    fun everyKeyIsPresentAndNonEmptyInEveryCatalogLanguage() {
        val strings = raw.getJSONObject("strings")
        assertTrue("seed keys present", strings.length() >= 6)
        val missing = sortedSetOf<String>()
        for (key in strings.keys()) {
            assertTrue("key form screen.element.purpose: $key", Regex("""^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$""").matches(key))
            val entry = strings.getJSONObject(key)
            assertTrue("$key carries a language the catalog does not declare: ${entry.keySetCompat() - codes.toSet()}", codes.containsAll(entry.keySetCompat()))
            for (code in codes) {
                when (val v = entry.opt(code)) {
                    null -> missing += "$key/$code"
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
        assertTrue("keys missing a value:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun pluralObjectsCarryTheCategoriesEachLanguagesFamilyNeeds() {
        val strings = raw.getJSONObject("strings")
        val source = catalog.defaultLanguage!!.code
        var plurals = 0
        for (key in strings.keys()) {
            val entry = strings.getJSONObject(key)
            val isPlural = entry.get(source) is JSONObject
            for (language in catalog.languages) {
                val code = language.code
                assertEquals("$key/$code plural-ness matches $source", isPlural, entry.get(code) is JSONObject)
                if (!isPlural) continue
                val have = entry.getJSONObject(code).keySetCompat()
                val need = PluralRules.requiredCategories(language.plural).map { it.key }.toSet()
                assertTrue("$key/$code needs $need, has $have", have.containsAll(need))
            }
            if (isPlural) plurals++
        }
        assertTrue("at least one plural key is seeded", plurals >= 1)
        assertEquals(setOf("one", "two", "other"), PluralFamily.ONE_TWO_OTHER.required.map { it.key }.toSet())
        assertEquals(6, PluralFamily.ARABIC_6.required.size)
        assertEquals(setOf("one", "few", "many", "other"), PluralFamily.SLAVIC_ONE_FEW_MANY.required.map { it.key }.toSet())
        assertEquals(setOf("one", "other"), PluralFamily.ONE_IS_1.required.map { it.key }.toSet())
        assertEquals(setOf("one", "other"), PluralFamily.ONE_IS_0_1.required.map { it.key }.toSet())
        assertEquals(PluralFamily.ONE_IS_1.required, PluralRules.requiredCategories(null))
    }

    @Test
    fun placeholderSetsAreIdenticalAcrossLanguages() {
        val strings = raw.getJSONObject("strings")
        val source = catalog.defaultLanguage!!.code
        var withPlaceholders = 0
        for (key in strings.keys()) {
            val entry = strings.getJSONObject(key)
            val sets = codes.associateWith { code ->
                when (val v = entry.get(code)) {
                    is JSONObject -> v.keySetCompat().flatMap { cat -> placeholders(v.getString(cat)) }.toSet()
                    else -> placeholders(v as String)
                }
            }
            assertEquals("$key placeholder sets differ: $sets", 1, sets.values.toSet().size)
            if (sets.getValue(source).isNotEmpty()) withPlaceholders++
        }
        assertTrue("a placeholder key is seeded", withPlaceholders >= 2)
    }

    @Test
    fun realCatalogRendersSeedKeys() {
        val en = I18nTestFiles.codeNamed("English")
        assertEquals("אישור", catalog.t(I18nTestFiles.defaultCode(), "common.ok"))
        assertEquals("OK", catalog.t(en, "common.ok"))
        assertEquals("Plate number: 12-345-67", catalog.t(en, "common.plate_label", mapOf("plate" to "12-345-67")))
        assertNull(catalog.rawEntry("no.such.key"))
    }
}
