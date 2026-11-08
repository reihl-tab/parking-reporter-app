package il.sidewalks.reporter.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #115 PR-2a: language resolution, the StateFlow controller and its store (JVM only). */
class LanguageStateTest {

    private val catalog = I18nTestFiles.realCatalog()

    private class FakeStore(var value: String? = null) : LanguageStore {
        val writes = mutableListOf<String?>()
        var reads = 0
        override fun get(): String? { reads++; return value }
        override fun set(code: String?) { writes += code; value = code }
    }

    private fun active(selected: String?, system: String?) = LanguageResolver.activeLanguage(catalog, selected, system).code

    @Test
    fun nullFollowsTheSystemLocaleWhenTheCatalogHasIt() {
        assertEquals("he", active(null, "he"))
        assertEquals("en", active(null, "en"))
        assertEquals("ar", active(null, "ar"))
        assertEquals("ru", active(null, "ru"))
        assertEquals("am", active(null, "am"))
    }

    @Test
    fun nullWithASystemLanguageTheCatalogLacksGivesHebrew() {
        assertEquals("he", active(null, "fr"))
        assertEquals("he", active(null, null))
        assertEquals("he", active(null, ""))
    }

    @Test
    fun systemLocaleFormsAreNormalized() {
        assertEquals("he", active(null, "iw"))
        assertEquals("he", active(null, "he-IL"))
        assertEquals("en", active(null, "en_US"))
        assertEquals("ru", active(null, "RU"))
        assertEquals("he", active(null, "pt-BR"))
        assertNull(LanguageResolver.normalize(" "))
        assertEquals("he", LanguageResolver.normalize("iw"))
        assertEquals("he", LanguageResolver.normalize("iw-IL"))
        assertEquals("am", LanguageResolver.normalize("am_ET"))
    }

    @Test
    fun anExplicitCodeWinsOverTheSystemLocale() {
        assertEquals("en", active("en", "he"))
        assertEquals("ar", active("ar", "en"))
        assertEquals("am", active("am", "fr"))
        assertEquals("he", active("he", "ru"))
        assertEquals("ru", active("ru", null))
    }

    @Test
    fun anUnknownStoredCodeGivesHebrewNotTheSystemLocale() {
        assertEquals("he", active("xx", "en"))
        assertEquals("he", active("fr", "ru"))
        assertEquals("he", active("", "en"))
    }

    @Test
    fun activeLanguageCarriesItsDirection() {
        assertEquals(TextDir.RTL, LanguageResolver.activeLanguage(catalog, "ar", null).dir)
        assertEquals(TextDir.LTR, LanguageResolver.activeLanguage(catalog, null, "en").dir)
        assertEquals(TextDir.RTL, LanguageResolver.activeLanguage(StringCatalog.EMPTY, "en", "en").dir)
        assertEquals("he", LanguageResolver.activeLanguage(StringCatalog.EMPTY, null, "en").code)
    }

    @Test
    fun absentStoredKeyResolvesToTheSystemDefault() {
        val controller = LanguageController(FakeStore(null))
        assertNull(controller.selected.value)
        assertEquals("en", LanguageResolver.activeLanguage(catalog, controller.selected.value, "en").code)
        assertEquals("ar", LanguageResolver.activeLanguage(catalog, controller.selected.value, "ar").code)
    }

    @Test
    fun aStoredCodeIsTheStartingSelection() {
        val store = FakeStore("ru")
        val controller = LanguageController(store)
        assertEquals("ru", controller.selected.value)
        assertEquals("ru", LanguageResolver.activeLanguage(catalog, controller.selected.value, "he").code)
        assertEquals(1, store.reads)
    }

    @Test
    fun anUnknownStoredCodeResolvesToHebrew() {
        val controller = LanguageController(FakeStore("xx"))
        assertEquals("he", LanguageResolver.activeLanguage(catalog, controller.selected.value, "en").code)
    }

    @Test
    fun selectPersistsAndAppliesLive() {
        val store = FakeStore(null)
        val controller = LanguageController(store)
        controller.select("en")
        assertEquals("en", controller.selected.value)
        assertEquals(listOf<String?>("en"), store.writes)
        assertEquals("en", LanguageResolver.activeLanguage(catalog, controller.selected.value, "he").code)
        controller.select(null)
        assertNull(controller.selected.value)
        assertEquals(listOf("en", null), store.writes)
        assertEquals("he", LanguageResolver.activeLanguage(catalog, controller.selected.value, "he").code)
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
        onLanguageSelected("ar")
        onLanguageSelected("en")
        onLanguageSelected(null)
        assertEquals(listOf("ar", "en", null), languageStore.writes)
        assertEquals(1, languageStore.reads)
    }

    @Test
    fun pickerLabelsAreIsolatedInTheirOwnDirection() {
        val he = catalog.language("he")!!
        val en = catalog.language("en")!!
        val ar = catalog.language("ar")!!
        assertEquals("${Bidi.RLI}עברית${Bidi.PDI}", LanguageResolver.pickerLabel(he))
        assertEquals("${Bidi.LRI}English${Bidi.PDI}", LanguageResolver.pickerLabel(en))
        assertEquals("${Bidi.RLI}العربية${Bidi.PDI}", LanguageResolver.pickerLabel(ar))
        assertEquals(listOf("he", "en", "ar", "ru", "am"), catalog.languages.map { it.code })
    }
}
