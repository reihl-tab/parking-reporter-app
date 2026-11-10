package il.sidewalks.reporter.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * td424 answer (b): Android shows the quick-settings tile label (`tile_new_report`) in the
 * device locale, from res/values-<code>/strings.xml, so those files stay checked in. This
 * test keeps them equal to the catalog: the default language lives in res/values, every
 * other catalog language has exactly one res/values-<code>, and no other language
 * directory exists. The app name stays Hebrew in every language (answer (c)): it is the
 * manifest's own label and no values-<code> file overrides it.
 */
class LauncherLabelResourcesTest {

    private val catalog = I18nTestFiles.realCatalog()
    private val res: File get() = I18nTestFiles.moduleFile("src/main/res")
    private val languageDir = Regex("""^values-([a-z]{2,3})(-r[A-Z]{2})?$""")
    private val stringEntry = Regex("""<string\s+name="([a-z_]+)"\s*>([^<]*)</string>""")

    private fun stringsIn(dir: File): Map<String, String> {
        val file = File(dir, "strings.xml")
        if (!file.isFile) return emptyMap()
        return stringEntry.findAll(file.readText(Charsets.UTF_8)).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    private fun languageDirs(): Map<String, File> =
        res.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
            languageDir.matchEntire(dir.name)?.let { it.groupValues[1] to dir }
        }.toMap()

    @Test
    fun everyNonDefaultCatalogLanguageHasItsOwnLabelFileAndNoOtherLanguageDoes() {
        val defaultCode = catalog.defaultLanguage!!.code
        val expected = catalog.languages.map { it.code }.filter { it != defaultCode }.toSortedSet()
        assertEquals(
            "res/values-<code> directories must equal the catalog's non-default languages",
            expected,
            languageDirs().keys.toSortedSet(),
        )
    }

    @Test
    fun everyLabelFileCarriesTheTileLabelAndNothingElse() {
        val base = stringsIn(File(res, "values"))
        assertTrue("res/values (the default language) has tile_new_report", base["tile_new_report"].orEmpty().isNotBlank())
        for ((code, dir) in languageDirs()) {
            val strings = stringsIn(dir)
            assertEquals("values-$code holds only the tile label", setOf("tile_new_report"), strings.keys)
            assertTrue("values-$code tile label is not blank", strings.getValue("tile_new_report").isNotBlank())
        }
    }

    @Test
    fun theAppNameStaysTheManifestsHebrewLabel() {
        val manifest = I18nTestFiles.moduleFile("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        assertTrue("application label is the literal app name", manifest.contains("android:label=\"מדרכות פנויות\""))
        for ((code, dir) in languageDirs() + ("" to File(res, "values"))) {
            assertTrue("values-$code must not define app_name", "app_name" !in stringsIn(dir))
        }
    }
}
