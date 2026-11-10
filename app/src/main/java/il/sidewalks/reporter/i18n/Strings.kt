package il.sidewalks.reporter.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONArray
import org.json.JSONObject

/**
 * Localization core for issue #115 (PR-1: infrastructure only).
 *
 * The single source of truth is `assets/i18n/strings.json`, and it is also the only place
 * that names languages (td423/td424): `{"languages":[{...}...], "strings":{key:{<code>:value}}}`.
 * Each language is one block: `code`, `name` (its own name, shown by the picker), `dir`,
 * `default` (exactly one, the source language), `machine_draft`, `plural` (a [PluralFamily]
 * id), and optionally `aliases` (legacy system codes that mean this language) and
 * `fallback` (consulted after the default when a value is missing).
 * A value is either a string with `{placeholder}` tokens or a plural object keyed
 * by CLDR category (zero, one, two, few, many, other).
 *
 * The core is plain Kotlin over org.json so it runs in JVM unit tests; the caller
 * reads the asset and passes its text to [StringCatalog.parse]. Nothing here throws
 * on bad data: a malformed entry falls through the chain
 * active language -> the default language -> each `fallback` language -> the key itself (visible).
 *
 * PR-1 added the core. PR-2a wires it in at the AppShell root (see LanguageState.kt) and
 * extracts AppShell and SettingsScreen; PR-2b/2c extracted the remaining sources.
 */
/** Text direction of a language, as declared by its `dir` field. */
enum class TextDir { RTL, LTR }

/**
 * One entry of the `languages` block. [status] is the translation status text, e.g. machine
 * draft; [machineDraft] is the same fact as a flag. [plural] is null when the block names
 * no known family (StringsAssetTest fails on that); [PluralRules] then counts 1 as one.
 */
data class Language(
    val code: String,
    val nativeName: String,
    val dir: TextDir,
    val isDefault: Boolean,
    val status: String?,
    val machineDraft: Boolean = false,
    val plural: PluralFamily? = null,
    val aliases: List<String> = emptyList(),
    val isFallback: Boolean = false,
) {
    /** Compose layout direction for this language; never derived from the device locale. */
    val layoutDirection: LayoutDirection
        get() = when (dir) {
            TextDir.RTL -> LayoutDirection.Rtl
            TextDir.LTR -> LayoutDirection.Ltr
        }
}

/** CLDR plural categories. */
enum class PluralCategory(val key: String) {
    ZERO("zero"), ONE("one"), TWO("two"), FEW("few"), MANY("many"), OTHER("other")
}

/**
 * The integer plural rule a language block names in its `plural` field. Languages share a
 * family when CLDR gives them the same integer rule, so a new language usually only names
 * an existing family. [acceptsManyAsOther]: before CLDR 42 Hebrew had a "many" form; a
 * value that still carries one is read as "other" when "other" is missing.
 */
enum class PluralFamily(
    val id: String,
    val required: Set<PluralCategory>,
    val acceptsManyAsOther: Boolean = false,
) {
    /** 1 is one, everything else other. */
    ONE_IS_1("one_is_1", setOf(PluralCategory.ONE, PluralCategory.OTHER)),

    /** 0 and 1 are one, everything else other. */
    ONE_IS_0_1("one_is_0_1", setOf(PluralCategory.ONE, PluralCategory.OTHER)),

    /** 1 is one, 2 is two, everything else other (CLDR 42 and later). */
    ONE_TWO_OTHER("one_two_other", setOf(PluralCategory.ONE, PluralCategory.TWO, PluralCategory.OTHER), acceptsManyAsOther = true),

    /** zero, one, two, few (3-10 mod 100), many (11-99 mod 100), other. */
    ARABIC_6("arabic_6", PluralCategory.entries.toSet()),

    /** one (1 mod 10 but not 11), few (2-4 mod 10 but not 12-14), many otherwise. */
    SLAVIC_ONE_FEW_MANY(
        "slavic_one_few_many",
        setOf(PluralCategory.ONE, PluralCategory.FEW, PluralCategory.MANY, PluralCategory.OTHER),
    ),
    ;

    companion object {
        fun byId(id: String?): PluralFamily? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Hand-written CLDR cardinal rules for integers (no ICU: android.icu is not available
 * in JVM unit tests), one per [PluralFamily]. Which family a language uses is the
 * catalog's word, never a language code here.
 */
object PluralRules {
    fun categoryOf(family: PluralFamily?, count: Long): PluralCategory {
        val n = if (count < 0) -count else count
        val mod10 = n % 10
        val mod100 = n % 100
        return when (family) {
            PluralFamily.ONE_TWO_OTHER -> when (n) {
                1L -> PluralCategory.ONE
                2L -> PluralCategory.TWO
                else -> PluralCategory.OTHER
            }
            PluralFamily.ARABIC_6 -> when {
                n == 0L -> PluralCategory.ZERO
                n == 1L -> PluralCategory.ONE
                n == 2L -> PluralCategory.TWO
                mod100 in 3L..10L -> PluralCategory.FEW
                mod100 in 11L..99L -> PluralCategory.MANY
                else -> PluralCategory.OTHER
            }
            PluralFamily.SLAVIC_ONE_FEW_MANY -> when {
                mod10 == 1L && mod100 != 11L -> PluralCategory.ONE
                mod10 in 2L..4L && mod100 !in 12L..14L -> PluralCategory.FEW
                else -> PluralCategory.MANY
            }
            PluralFamily.ONE_IS_0_1 -> if (n == 0L || n == 1L) PluralCategory.ONE else PluralCategory.OTHER
            PluralFamily.ONE_IS_1, null -> if (n == 1L) PluralCategory.ONE else PluralCategory.OTHER
        }
    }

    /** Categories a plural object must carry for a language of [family] (other is always required). */
    fun requiredCategories(family: PluralFamily?): Set<PluralCategory> =
        family?.required ?: PluralFamily.ONE_IS_1.required
}

/**
 * Unicode bidi isolates used around interpolated values: every value in an RTL language,
 * and in an LTR language every value that carries a strong-RTL character (a Hebrew street
 * name inside an English sentence, for example).
 */
object Bidi {
    const val LRI = '\u2066'
    const val RLI = '\u2067'
    const val FSI = '\u2068'
    const val PDI = '\u2069'

    fun hasStrongRtl(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            val d = Character.getDirectionality(cp)
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true
            }
            i += Character.charCount(cp)
        }
        return false
    }

    /** LRI..PDI for values with no strong-RTL character (plates, numbers, Latin), FSI..PDI otherwise. */
    fun isolate(value: String): String =
        if (hasStrongRtl(value)) "$FSI$value$PDI" else "$LRI$value$PDI"
}

class StringCatalog private constructor(
    val languages: List<Language>,
    private val strings: Map<String, JSONObject>,
) {
    val keys: Set<String> get() = strings.keys

    /** The block marked `default`, else the first block; null only for an empty catalog. */
    val defaultLanguage: Language?
        get() = languages.firstOrNull { it.isDefault } ?: languages.firstOrNull()

    /** The language marked [code], or the one listing [code] among its `aliases`. */
    fun languageOrAlias(code: String): Language? = language(code) ?: languages.firstOrNull { code in it.aliases }

    fun language(code: String): Language? = languages.firstOrNull { it.code == code }

    /** Raw JSON entry for [key] (per-language values), for coverage tests. */
    fun rawEntry(key: String): JSONObject? = strings[key]

    /** Translate [key] into [languageCode], interpolating [params]. Never throws. */
    fun t(languageCode: String, key: String, params: Map<String, Any?> = emptyMap()): String {
        val entry = strings[key] ?: return key
        for (code in chain(languageCode)) {
            val template = when (val value = entry.opt(code)) {
                is String -> value
                is JSONObject -> pluralForm(value, code, PluralCategory.OTHER)
                else -> null
            }
            if (!template.isNullOrEmpty()) return interpolate(template, params, languageCode)
        }
        return key
    }

    /** Plural-aware translation; `{count}` is filled from [count] unless [params] names it. */
    fun tp(languageCode: String, key: String, count: Long, params: Map<String, Any?> = emptyMap()): String {
        val entry = strings[key] ?: return key
        val all = if (params.containsKey(COUNT)) params else params + (COUNT to count)
        for (code in chain(languageCode)) {
            val template = when (val value = entry.opt(code)) {
                is JSONObject -> pluralForm(value, code, PluralRules.categoryOf(language(code)?.plural, count))
                is String -> value
                else -> null
            }
            if (!template.isNullOrEmpty()) return interpolate(template, all, languageCode)
        }
        return key
    }

    /** active -> the default language -> each `fallback` language, in catalog order (td424). */
    private fun chain(languageCode: String): List<String> =
        (listOf(languageCode) + listOfNotNull(defaultLanguage?.code) + languages.filter { it.isFallback }.map { it.code }).distinct()

    private fun pluralForm(obj: JSONObject, code: String, category: PluralCategory): String? {
        val wanted = obj.opt(category.key) as? String
        if (!wanted.isNullOrEmpty()) return wanted
        val other = obj.opt(PluralCategory.OTHER.key) as? String
        if (!other.isNullOrEmpty()) return other
        // A family whose older CLDR tables had a "many" form (Hebrew) accepts it as "other".
        if (language(code)?.plural?.acceptsManyAsOther == true) {
            val many = obj.opt(PluralCategory.MANY.key) as? String
            if (!many.isNullOrEmpty()) return many
        }
        return null
    }

    private fun interpolate(template: String, params: Map<String, Any?>, activeCode: String): String {
        val rtl = language(activeCode)?.dir == TextDir.RTL
        return PLACEHOLDER.replace(template) { m ->
            val name = m.groupValues[1]
            val value = params[name]
            if (value == null) {
                m.value
            } else {
                val text = value.toString()
                if (rtl || Bidi.hasStrongRtl(text)) Bidi.isolate(text) else text
            }
        }
    }

    companion object {
        const val COUNT = "count"
        const val ASSET_PATH = "i18n/strings.json"
        val PLACEHOLDER = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\}""")

        val EMPTY = StringCatalog(emptyList(), emptyMap())

        /** Parse the catalog JSON once. Bad JSON gives [EMPTY]; bad entries are skipped. */
        fun parse(json: String): StringCatalog {
            val root = try {
                JSONObject(json)
            } catch (_: Exception) {
                return EMPTY
            }
            val languages = mutableListOf<Language>()
            val langArray = root.opt("languages") as? JSONArray
            if (langArray != null) {
                for (i in 0 until langArray.length()) {
                    val obj = langArray.opt(i) as? JSONObject ?: continue
                    val code = obj.opt("code") as? String ?: continue
                    val dir = when (obj.opt("dir") as? String) {
                        "rtl" -> TextDir.RTL
                        "ltr" -> TextDir.LTR
                        else -> null
                    } ?: continue
                    if (code.isEmpty() || languages.any { it.code == code }) continue
                    val aliases = (obj.opt("aliases") as? JSONArray)?.let { a ->
                        (0 until a.length()).mapNotNull { a.opt(it) as? String }.filter { it.isNotEmpty() }
                    } ?: emptyList()
                    languages += Language(
                        code = code,
                        nativeName = obj.opt("name") as? String ?: code,
                        dir = dir,
                        isDefault = obj.opt("default") == true,
                        status = obj.opt("status") as? String,
                        machineDraft = obj.opt("machine_draft") == true,
                        plural = PluralFamily.byId(obj.opt("plural") as? String),
                        aliases = aliases,
                        isFallback = obj.opt("fallback") == true,
                    )
                }
            }
            val strings = mutableMapOf<String, JSONObject>()
            val stringsObj = root.opt("strings") as? JSONObject
            if (stringsObj != null) {
                for (key in stringsObj.keys()) {
                    val entry = stringsObj.opt(key) as? JSONObject ?: continue
                    strings[key] = entry
                }
            }
            return StringCatalog(languages, strings)
        }
    }
}

/** A catalog bound to one active language: what screens read through [LocalStrings]. */
class Strings(val catalog: StringCatalog, val languageCode: String) {
    fun t(key: String, params: Map<String, Any?> = emptyMap()): String = catalog.t(languageCode, key, params)

    fun tp(key: String, count: Long, params: Map<String, Any?> = emptyMap()): String =
        catalog.tp(languageCode, key, count, params)

    fun tp(key: String, count: Int, params: Map<String, Any?> = emptyMap()): String = tp(key, count.toLong(), params)
}

/** Defaults to an empty catalog (keys render visibly) so a missing provider never crashes. */
val LocalStrings = staticCompositionLocalOf { Strings(StringCatalog.EMPTY, LanguageResolver.EMPTY_CATALOG_LANGUAGE.code) }

/**
 * Provides [LocalStrings] and the language's [LocalLayoutDirection] to [content].
 * AppShell provides it at its root with the active language (PR-2a).
 */
@Composable
fun ProvideStrings(catalog: StringCatalog, language: Language, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStrings provides Strings(catalog, language.code),
        LocalLayoutDirection provides language.layoutDirection,
    ) {
        content()
    }
}
