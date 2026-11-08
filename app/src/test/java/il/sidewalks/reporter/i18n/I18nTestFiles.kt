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

    const val LRI = "\u2066"
    const val FSI = "\u2068"
    const val PDI = "\u2069"
}
