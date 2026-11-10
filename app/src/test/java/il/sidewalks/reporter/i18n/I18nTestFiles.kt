package il.sidewalks.reporter.i18n

import java.io.File

/** Locates module files from the unit-test working directory (the app module, or the repo root). */
internal object I18nTestFiles {
    fun moduleFile(relative: String): File {
        val candidates = listOf(File(relative), File("app", relative))
        return candidates.firstOrNull { it.exists() }
            ?: error("Not found from ${File(".").absolutePath}: $relative")
    }

    /** The real asset, read from the source tree (not a copy). */
    fun realCatalogText(): String = moduleFile("src/main/assets/" + StringCatalog.ASSET_PATH).readText(Charsets.UTF_8)

    fun realCatalog(): StringCatalog = StringCatalog.parse(realCatalogText())

    /** The real catalog's languages, in catalog order: what tests iterate instead of a list. */
    fun languageCodes(): List<String> = realCatalog().languages.map { it.code }

    /** The real catalog's default (source) language, whose values the extraction tests pin. */
    fun defaultCode(): String = realCatalog().defaultLanguage!!.code

    /**
     * The code of the catalog language whose own name is [nativeName]. Tests that pin the
     * wording of one particular language name it the way the picker does, by its own name,
     * so no language code is written outside the catalog (td423).
     */
    fun codeNamed(nativeName: String): String =
        realCatalog().languages.singleOrNull { it.nativeName == nativeName }?.code
            ?: error("no catalog language is called $nativeName")

    const val LRI = "\u2066"
    const val FSI = "\u2068"
    const val PDI = "\u2069"
}
