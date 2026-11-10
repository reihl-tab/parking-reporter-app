package il.sidewalks.reporter.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** The catalog from assets/i18n/strings.json, parsed once per process. */
object AppCatalog {
    @Volatile private var cached: StringCatalog? = null

    fun get(context: Context): StringCatalog = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    /** The catalog's default (source) language code: what tests pin to get the default UI. */
    fun defaultCode(context: Context): String = LanguageResolver.defaultLanguage(get(context)).code

    private fun load(context: Context): StringCatalog = try {
        context.assets.open(StringCatalog.ASSET_PATH).bufferedReader(Charsets.UTF_8).use { StringCatalog.parse(it.readText()) }
    } catch (_: Exception) {
        StringCatalog.EMPTY
    }
}

@Composable
fun rememberAppCatalog(): StringCatalog {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppCatalog.get(context) }
}

/** The app's language controller over the "ui-prefs" store. No authentication involved. */
@Composable
fun rememberLanguageController(): LanguageController {
    val context = LocalContext.current.applicationContext
    return remember(context) { LanguageController(UiPrefsLanguageStore.of(context)) }
}

/** The system locale's language as Android reports it (possibly the legacy "iw"). */
@Composable
fun systemLanguageCode(): String? {
    val locales = LocalConfiguration.current.locales
    return if (locales.isEmpty) null else locales.get(0)?.language
}

/**
 * Screens read [LocalStrings]; inside the app AppShell provides it. A screen hosted on
 * its own (instrumented tests, previews) has no provider: this gives it the real catalog
 * in its default language (the block marked `default`) instead of rendering raw keys.
 */
@Composable
fun ProvideStringsIfMissing(content: @Composable () -> Unit) {
    if (LocalStrings.current.catalog !== StringCatalog.EMPTY) {
        content()
        return
    }
    val catalog = rememberAppCatalog()
    ProvideStrings(catalog, LanguageResolver.defaultLanguage(catalog), content)
}
