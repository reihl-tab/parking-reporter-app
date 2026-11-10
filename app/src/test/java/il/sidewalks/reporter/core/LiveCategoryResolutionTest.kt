package il.sidewalks.reporter.core

import java.io.File
import org.junit.Test

/**
 * Resolves the category picker label for a live E2E run from the staged
 * TRIAL_SUBJECT code - the SAME code the engine trial files with - so the
 * UI run can never carry a label that disagrees with the staged subject.
 * Runs only when the live workflow sets LIVE_CATEGORY_PATH; the regular
 * suite returns immediately. The resolved Hebrew label is public taxonomy,
 * safe to print.
 */
class LiveCategoryResolutionTest {
    @Test fun resolveTrialSubjectLabel() {
        val out = System.getenv("LIVE_CATEGORY_PATH") ?: return
        // TRIAL_SUBJECT is mandatory for a live run, mirroring the engine trial's
        // env() helper: fail closed when the secret is absent rather than guessing
        // a category for the approved text.
        val code = System.getenv("TRIAL_SUBJECT")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("missing env TRIAL_SUBJECT")
        val category = ReportCategory.from(code)
            ?: throw AssertionError("unsupported trial subject")
        val stringsFile = listOf(
            File("src/main/assets/i18n/strings.json"),
            File("app/src/main/assets/i18n/strings.json"),
        ).first { it.isFile }
        val json = stringsFile.readText(Charsets.UTF_8)
        // The catalog's default language (Hebrew): the label the picker shows by default.
        val source = il.sidewalks.reporter.i18n.StringCatalog.parse(json).defaultLanguage!!.code
        val label = Regex(
            """"${Regex.escape(category.labelKey)}"\s*:\s*\{[^}]*"${Regex.escape(source)}"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(json)?.groupValues?.get(1)
            ?: throw AssertionError("label missing for the staged subject")
        File(out).writeText("LIVE_CATEGORY label=$label\n")
        println("LIVE_CATEGORY label=$label")
    }
}
