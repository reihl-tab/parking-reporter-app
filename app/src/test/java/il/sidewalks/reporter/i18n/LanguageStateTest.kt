package il.sidewalks.reporter.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #115 PR-2a: language resolution, the StateFlow controller and its store (JVM only).
 * Every language here is read from the real catalog (td423/td424); "zz" and "xx" are codes
 * no catalog will carry.
 */
class LanguageStateTest {

    private val catalog = I18nTestFiles.realCatalog()
    private val codes = catalog.languages.map { it.code }
    private val default = catalog.defaultLanguage!!.code
    private val others = codes - default
    private val unknown = listOf("zz", "xx", "")

    private class FakeStore(var value: String? = null) : LanguageStore {
        val writes = mutableListOf<String?>()
        var reads = 0
        override fun get(): String? { reads++; return value }
        override fun set(code: String?) { writes += code; value = code }
    }

    private fun active(selected: String?, system: String?) = LanguageResolver.activeLanguage(catalog, selected, system).code

    @Test
    fun theCatalogHasADefaultAndOtherLanguages() {
        assertTrue(others.isNotEmpty())
    }

    @Test
    fun nullFollowsTheSystemLocaleWhenTheCatalogHasIt() {
        for (code in codes) assertEquals(code, active(null, code))
    }

    @Test
    fun nullWithASystemLanguageTheCatalogLacksGivesTheDefault() {
        for (code in unknown) assertEquals(default, active(null, code))
        assertEquals(default, active(null, null))
    }

    @Test
    fun systemLocaleFormsAreNormalized() {
        for (code in codes) {
            assertEquals(code, active(null, "$code-IL"))
            assertEquals(code, active(null, "${code}_US"))
            assertEquals(code, active(null, code.uppercase()))
            assertEquals(code, LanguageResolver.normalize(" ${code.uppercase()}_ET "))
        }
        assertEquals(default, active(null, "pt-BR"))
        assertNull(LanguageResolver.normalize(" "))
        assertNull(LanguageResolver.normalize(null))
    }

    @Test
    fun aLegacySystemCodeIsMatchedThroughTheCatalogsAliases() {
        val withAliases = catalog.languages.filter { it.aliases.isNotEmpty() }
        // Java may still report the legacy Hebrew code; the catalog lists it as an alias.
        assertTrue("some block declares an alias", withAliases.isNotEmpty())
        for (language in withAliases) {
            for (alias in language.aliases) {
                assertEquals(language.code, active(null, alias))
                assertEquals(language.code, active(null, "$alias-IL"))
                assertEquals(alias, LanguageResolver.normalize("$alias-IL"))
                // An alias is a system-locale form only: a stored choice is a catalog code.
                assertEquals(default, active(alias, null))
            }
        }
    }

    @Test
    fun anExplicitCodeWinsOverTheSystemLocale() {
        for (selected in codes) {
            for (system in codes + unknown) assertEquals(selected, active(selected, system))
            assertEquals(selected, active(selected, null))
        }
    }

    @Test
    fun anUnknownStoredCodeGivesTheDefaultNotTheSystemLocale() {
        for (code in unknown) assertEquals(default, active(code, others.first()))
    }

    @Test
    fun activeLanguageCarriesItsDirection() {
        for (language in catalog.languages) {
            assertEquals(language.dir, LanguageResolver.activeLanguage(catalog, language.code, null).dir)
            assertEquals(language.dir, LanguageResolver.activeLanguage(catalog, null, language.code).dir)
        }
    }

    @Test
    fun anEmptyCatalogGivesTheUndeterminedRtlPlaceholder() {
        val placeholder = LanguageResolver.EMPTY_CATALOG_LANGUAGE
        assertEquals("und", placeholder.code)
        assertEquals(TextDir.RTL, placeholder.dir)
        for (code in codes + unknown) {
            assertEquals(placeholder, LanguageResolver.activeLanguage(StringCatalog.EMPTY, code, code))
            assertEquals(placeholder, LanguageResolver.activeLanguage(StringCatalog.EMPTY, null, code))
        }
        assertEquals(placeholder, LanguageResolver.defaultLanguage(StringCatalog.EMPTY))
        assertEquals(catalog.defaultLanguage, LanguageResolver.defaultLanguage(catalog))
    }

    @Test
    fun absentStoredKeyResolvesToTheSystemDefault() {
        val controller = LanguageController(FakeStore(null))
        assertNull(controller.selected.value)
        for (code in codes) assertEquals(code, LanguageResolver.activeLanguage(catalog, controller.selected.value, code).code)
    }

    @Test
    fun aStoredCodeIsTheStartingSelection() {
        val stored = others.last()
        val store = FakeStore(stored)
        val controller = LanguageController(store)
        assertEquals(stored, controller.selected.value)
        assertEquals(stored, LanguageResolver.activeLanguage(catalog, controller.selected.value, default).code)
        assertEquals(1, store.reads)
    }

    @Test
    fun anUnknownStoredCodeResolvesToTheDefault() {
        val controller = LanguageController(FakeStore("xx"))
        assertEquals(default, LanguageResolver.activeLanguage(catalog, controller.selected.value, others.first()).code)
    }

    @Test
    fun selectPersistsAndAppliesLive() {
        val picked = others.first()
        val store = FakeStore(null)
        val controller = LanguageController(store)
        controller.select(picked)
        assertEquals(picked, controller.selected.value)
        assertEquals(listOf<String?>(picked), store.writes)
        assertEquals(picked, LanguageResolver.activeLanguage(catalog, controller.selected.value, default).code)
        controller.select(null)
        assertNull(controller.selected.value)
        assertEquals(listOf(picked, null), store.writes)
        assertEquals(default, LanguageResolver.activeLanguage(catalog, controller.selected.value, default).code)
    }

    @Test
    fun aStoreThatFailsToReadStartsAtTheSystemDefault() {
        val controller = LanguageController(object : LanguageStore {
            override fun get(): String? = throw IllegalStateException("synthetic")
            override fun set(code: String?) {}
        })
        assertNull(controller.selected.value)
    }

    /**
     * The controller's only dependency is its LanguageStore: a run of changes writes
     * exactly those codes there and nothing else. That AppShell's picker wiring calls
     * neither authenticate() nor the encrypted settings is tested end to end, with
     * counting fakes, in androidTest LanguagePickerTest.
     */
    @Test
    fun languageChangesWriteOnlyTheLanguageStore() {
        val languageStore = FakeStore(null)
        val controller = LanguageController(languageStore)
        val onLanguageSelected: (String?) -> Unit = controller::select // as AppShell wires the picker
        for (code in others) onLanguageSelected(code)
        onLanguageSelected(null)
        assertEquals(others + listOf(null), languageStore.writes)
        assertEquals(1, languageStore.reads)
    }

    @Test
    fun pickerLabelsAreTheOwnNameIsolatedInTheLanguagesDirection() {
        for (language in catalog.languages) {
            val open = if (language.dir == TextDir.RTL) Bidi.RLI else Bidi.LRI
            assertEquals("$open${language.nativeName}${Bidi.PDI}", LanguageResolver.pickerLabel(language))
        }
        assertEquals("${Bidi.RLI}עברית${Bidi.PDI}", LanguageResolver.pickerLabel(catalog.language(I18nTestFiles.codeNamed("עברית"))!!))
        assertEquals("${Bidi.LRI}English${Bidi.PDI}", LanguageResolver.pickerLabel(catalog.language(I18nTestFiles.codeNamed("English"))!!))
        assertNotEquals(codes.size, 0)
    }

    /**
     * td423: the picker shows exactly the catalog's languages, in catalog order, by their own
     * names. LanguagePicker is a private composable, so this reads its source: Settings hands
     * it catalog.languages, and it renders the system default plus one option per language.
     */
    @Test
    fun thePickerShowsExactlyTheCatalogLanguages() {
        val settings = I18nTestFiles.moduleFile("src/main/java/il/sidewalks/reporter/ui/SettingsScreen.kt").readText(Charsets.UTF_8)
        assertEquals(1, Regex("""LanguagePicker\(s\.catalog\.languages,""").findAll(settings).count())
        val picker = settings.substring(settings.indexOf("private fun LanguagePicker("))
        val body = picker.substring(0, picker.indexOf("\n}\n") + 2)
        assertEquals("one option for the system default, one per language", 2, Regex("""LanguageOption\(""").findAll(body).count())
        assertEquals(1, Regex("""languages\.forEach \{ language ->""").findAll(body).count())
        assertEquals(1, Regex("""LanguageOption\(LanguageResolver\.pickerLabel\(language\), language, selected == language\.code\)""").findAll(body).count())
        assertEquals("no language is filtered out or added", 0, Regex("""languages\.(filter|take|drop|plus|sorted)|\+ Language\(""").findAll(body).count())
    }
}
