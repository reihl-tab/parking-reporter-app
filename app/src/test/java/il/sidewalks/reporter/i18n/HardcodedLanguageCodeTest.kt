package il.sidewalks.reporter.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * td423/td424: assets/i18n/strings.json is the only place that names a language. This scan
 * fails when a catalog language code (or one of its `aliases`) appears as a string literal
 * anywhere else in the app's sources: main, unit tests, instrumented tests, the manifest,
 * the resources and the Gradle scripts. Comments are not code and are skipped.
 *
 * A literal counts when it IS a code ("xx", "xx-YY", "xx_YY") or when its text quotes one
 * ("xx" inside JSON in a raw string). Tests that need a language take it from the catalog
 * (see [I18nTestFiles]); synthetic catalogs in tests use codes that are no real language.
 *
 * Not scanned, by design: the catalog itself, the res/values-<code> directories (Android
 * picks the launcher label by that directory name; [LauncherLabelResourcesTest] keeps them
 * equal to the catalog) and this file, which holds no code.
 */
class HardcodedLanguageCodeTest {

    private val catalog = I18nTestFiles.realCatalog()

    /** Every catalog code and alias: what may only live in the catalog. */
    private val codes: Set<String> = catalog.languages.flatMap { listOf(it.code) + it.aliases }.toSet()

    /**
     * The justified exceptions, by file, with the exact number of hits each may keep (so an
     * exception can neither grow nor go stale silently).
     */
    private val exceptions = mapOf(
        // City data, not a UI language: the geocoder must return the city's Hebrew street
        // names in every UI language (do-not-translate list, td423/td453).
        "src/main/java/il/sidewalks/reporter/evidence/AddressResolver.kt" to 1,
        // The live emulator e2e test is outside this change by agreement (td424): it is
        // owned by the live-e2e lane and is not edited here.
        "src/androidTest/java/il/sidewalks/reporter/live/LiveEmulatorSubmissionTest.kt" to 1,
    )

    private val moduleRoot: File get() = I18nTestFiles.moduleFile("src/main/AndroidManifest.xml").absoluteFile.parentFile.parentFile.parentFile

    private val valuesForALanguage = Regex("""^values-[a-z]{2,3}(-r[A-Z]{2})?$""")

    private fun scannedFiles(): List<File> {
        val src = File(moduleRoot, "src")
        val files = src.walkTopDown()
            .onEnter { dir -> !(dir.parentFile?.name == "res" && valuesForALanguage.matches(dir.name)) && dir.name != "assets" }
            .filter { it.isFile && it.extension in setOf("kt", "kts", "java", "xml") }
            .filter { it.name != "HardcodedLanguageCodeTest.kt" }
            .toMutableList()
        files += listOf(File(moduleRoot, "build.gradle.kts"), File(moduleRoot.parentFile, "build.gradle.kts"), File(moduleRoot.parentFile, "settings.gradle.kts"))
            .filter { it.isFile }
        return files
    }

    private fun relative(file: File): String = file.relativeTo(moduleRoot).invariantSeparatorsPath

    /** A literal that is a code, or text quoting one. */
    private fun hits(text: String): List<String> {
        val alternatives = codes.joinToString("|") { Regex.escape(it) }
        val whole = Regex("""^(?:$alternatives)(?:[-_][A-Za-z0-9]{2,8})*$""")
        val quoted = Regex(""""(?:$alternatives)(?:[-_][A-Za-z0-9]{2,8})*"""")
        val found = mutableListOf<String>()
        if (whole.matches(text.trim())) found += text.trim()
        found += quoted.findAll(text).map { it.value }
        return found
    }

    private fun xmlValues(source: String): List<HardcodedUiStringScanner.Literal> {
        val noComments = source.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)) { m -> m.value.filter { it == '\n' } }
        return Regex(""""([^"\n]*)"|>([^<]*)<""").findAll(noComments).map { m ->
            val line = noComments.substring(0, m.range.first).count { it == '\n' } + 1
            HardcodedUiStringScanner.Literal(line, m.groupValues[1].ifEmpty { m.groupValues[2] })
        }.toList()
    }

    /** "file:line: literal" for every hit, per file. */
    private fun findings(): Map<String, List<String>> = scannedFiles().associate { file ->
        val source = file.readText(Charsets.UTF_8)
        val literals = if (file.extension == "xml") xmlValues(source) else HardcodedUiStringScanner.literals(source)
        relative(file) to literals.flatMap { lit -> hits(lit.text).map { "${relative(file)}:${lit.line}: $it" } }
    }.filterValues { it.isNotEmpty() }

    @Test
    fun theScanKnowsEveryCatalogLanguage() {
        assertTrue("the catalog names languages", catalog.languages.isNotEmpty())
        for (language in catalog.languages) assertTrue(language.code, language.code in codes)
        assertTrue("the scan covers main and both test source sets", scannedFiles().map(::relative).let { paths ->
            listOf("src/main/", "src/test/", "src/androidTest/").all { root -> paths.any { it.startsWith(root) } }
        })
    }

    @Test
    fun noLanguageCodeIsHardcodedOutsideTheCatalog() {
        val all = findings()
        val unexpected = all.filterKeys { it !in exceptions }.values.flatten().sorted()
        assertTrue(
            "language codes hardcoded outside assets/i18n/strings.json (take them from the catalog):\n" +
                unexpected.joinToString("\n"),
            unexpected.isEmpty(),
        )
    }

    @Test
    fun eachExceptionKeepsExactlyItsCount() {
        val all = findings()
        for ((path, count) in exceptions) {
            assertTrue("$path still exists", File(moduleRoot, path).isFile)
            assertEquals("$path: ${all[path].orEmpty()}", count, all[path].orEmpty().size)
        }
    }

    @Test
    fun theScanCatchesWhatItIsFor() {
        val code = catalog.languages.last().code
        val samples = listOf(
            "val x = \"$code\"" to 1,
            "Locale.forLanguageTag(\"$code-IL\")" to 1,
            "val json = \"\"\"{\"code\": \"$code\"}\"\"\"" to 1,
            "// a comment naming \"$code\" is fine" to 0,
            "val word = \"${code}x\"" to 0,
        )
        for ((sample, expected) in samples) {
            val n = HardcodedUiStringScanner.literals(sample).sumOf { hits(it.text).size }
            assertEquals(sample, expected, n)
        }
    }
}
