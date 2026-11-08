package il.sidewalks.reporter.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the chosen UI language lives (issue #115 PR-2a). A language code, or null for
 * "follow the system". The Android implementation is [UiPrefsLanguageStore]; JVM tests
 * use a fake. It is deliberately separate from the encrypted ReporterSettings: that store
 * needs device authentication within the last 30 s to read, so it cannot be read at
 * app start (Instinct's ruling td386, option C).
 */
interface LanguageStore {
    fun get(): String?

    /** null removes the choice (system default). */
    fun set(code: String?)
}

/**
 * The selected UI language as a [StateFlow]. [select] persists and applies in one step,
 * so the UI switches live (no Activity restart). It touches only its [LanguageStore]:
 * never the encrypted settings and never device authentication.
 */
class LanguageController(private val store: LanguageStore) {
    private val selectedCode = MutableStateFlow(readSafely())

    /** The stored code, or null for the system default. */
    val selected: StateFlow<String?> = selectedCode.asStateFlow()

    fun select(code: String?) {
        store.set(code)
        selectedCode.value = code
    }

    private fun readSafely(): String? = try {
        store.get()
    } catch (_: Exception) {
        null
    }
}

/** Picks the active catalog language from the stored choice and the system locale. */
object LanguageResolver {
    /** Used only when the catalog itself has no Hebrew entry (an empty or broken catalog). */
    private val HEBREW_FALLBACK = Language(StringCatalog.HEBREW, StringCatalog.HEBREW, TextDir.RTL, true, null)

    /**
     * The selected code if the catalog has it; an unknown selected code gives he. With no
     * selection (null), the system locale's language if the catalog has it, else he.
     */
    fun activeLanguage(catalog: StringCatalog, selected: String?, systemLanguage: String?): Language {
        if (selected != null) return catalog.language(selected) ?: hebrew(catalog)
        val system = normalize(systemLanguage)
        if (system != null) catalog.language(system)?.let { return it }
        return hebrew(catalog)
    }

    /**
     * A system locale language as a catalog code: lower case, region dropped, and the
     * legacy ISO code "iw" (which java.util.Locale may still report for Hebrew) mapped to he.
     */
    fun normalize(systemLanguage: String?): String? {
        val base = systemLanguage?.trim()?.lowercase()?.split('-', '_')?.firstOrNull()
        if (base.isNullOrEmpty()) return null
        return if (base == "iw") StringCatalog.HEBREW else base
    }

    fun hebrew(catalog: StringCatalog): Language = catalog.language(StringCatalog.HEBREW) ?: HEBREW_FALLBACK

    /** A native language name for the picker, isolated in its own direction. */
    fun pickerLabel(language: Language): String {
        val open = if (language.dir == TextDir.RTL) Bidi.RLI else Bidi.LRI
        return "$open${language.nativeName}${Bidi.PDI}"
    }
}
