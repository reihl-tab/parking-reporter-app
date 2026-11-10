package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.core.ReportCategory
import il.sidewalks.reporter.ui.HebrewPresentation
import il.sidewalks.reporter.ui.reviewTimeText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #115 PR-2c: ReportCategory.kt and HebrewPresentation.kt read their display texts
 * from the catalog when shown, and MainActivity.kt holds no UI text (its device-unlock
 * prompt texts are passed in by AppShell, which reads them from the catalog since PR-2a).
 * The Hebrew values are pinned to the literals those files had before the extraction
 * (main at 2e91581, after PR-2b), byte for byte.
 *
 * None of these texts reaches the city: the CreateCase payload carries the category's
 * subject code and the approved text the user typed. MunicipalReportTextStaysHebrewTest
 * pins that payload.
 */
class MainCategoryPresentationExtractionTest {

    private val catalog = I18nTestFiles.realCatalog()
    private val raw = JSONObject(I18nTestFiles.realCatalogText()).getJSONObject("strings")
    private val he = Strings(catalog, "he")
    private val en = Strings(catalog, "en")
    private val lri = I18nTestFiles.LRI
    private val pdi = I18nTestFiles.PDI
    private val fsi = I18nTestFiles.FSI

    /** Key -> the Hebrew literal it replaced. */
    private val categories = linkedMapOf(
        "category.sidewalk_parking" to "חניה על המדרכה",
        "category.broken_sign" to "תמרור שבור",
        "category.faded_crosswalk" to "מעבר חציה דהוי",
        "category.bollard_request" to "בקשה להתקנת עמוד חסימה",
        "category.needs_correction" to "נדרש תיקון קטגוריה",
    )

    private val presentation = linkedMapOf(
        "presentation.time.unknown_zone" to "אזור זמן לא ידוע",
        "presentation.time.missing" to "חסר",
    )

    private val all get() = categories + presentation

    private val languages = listOf("he", "en", "ar", "ru", "am")

    private val sources = mapOf(
        "ReportCategory.kt" to "src/main/java/il/sidewalks/reporter/core/ReportCategory.kt",
        "HebrewPresentation.kt" to "src/main/java/il/sidewalks/reporter/ui/HebrewPresentation.kt",
        "MainActivity.kt" to "src/main/java/il/sidewalks/reporter/MainActivity.kt",
        "ReviewScreen.kt" to "src/main/java/il/sidewalks/reporter/ui/ReviewScreen.kt",
        "ReviewTexts.kt" to "src/main/java/il/sidewalks/reporter/ui/ReviewTexts.kt",
    )

    private fun source(name: String): String = I18nTestFiles.moduleFile(sources.getValue(name)).readText(Charsets.UTF_8)

    private val keyLiteral = Regex(""""((?:category|presentation)\.[a-z0-9_]+(?:\.[a-z0-9_]+)*)"""")

    private fun keysNamedIn(name: String): Set<String> = keyLiteral.findAll(source(name)).map { it.groupValues[1] }.toSet()

    private val script = Regex("[\u0590-\u05FF\u0600-\u06FF\u0400-\u04FF\u1200-\u139F]")

    @Test
    fun extractedHebrewValuesEqualTheOldLiteralsByteForByte() {
        for ((key, literal) in all) {
            assertEquals("he value of $key", literal, raw.getJSONObject(key).getString("he"))
            assertEquals(key, literal, catalog.t("he", key))
        }
    }

    @Test
    fun everyUsedKeyExistsInAllFiveLanguagesAndEveryKeyIsUsed() {
        val used = keysNamedIn("ReportCategory.kt") + keysNamedIn("HebrewPresentation.kt")
        for (key in used) {
            val entry = catalog.rawEntry(key)
            assertTrue("$key is used but missing from strings.json", entry != null)
            for (code in languages) assertTrue("$key/$code", entry!!.optString(code).isNotBlank())
        }
        val ownKeys = catalog.keys.filter { it.startsWith("category.") || it.startsWith("presentation.") }.toSet()
        assertEquals("category./presentation. keys in the catalog vs the keys the sources use", ownKeys, used)
        assertEquals(all.keys, ownKeys)
        assertTrue(keysNamedIn("ReportCategory.kt").all { it.startsWith("category.") })
        assertEquals(setOf("category.needs_correction") + presentation.keys, keysNamedIn("HebrewPresentation.kt"))
    }

    /** Stable codes: the ledger stores [ReportCategory.code], the city gets the subject code. */
    @Test
    fun categoryCodesAreStableAndEachHasItsOwnLabelKey() {
        assertEquals(listOf("sidewalk_parking", "broken_sign", "faded_crosswalk", "bollard_request"), ReportCategory.entries.map { it.code })
        assertEquals(listOf("1145", "17841", "1137", "1280"), ReportCategory.entries.map { it.municipalSubjectCode })
        assertEquals(listOf(true, false, false, false), ReportCategory.entries.map { it.needsPlate })
        for (category in ReportCategory.entries) {
            assertEquals("category.${category.code}", category.labelKey)
            assertFalse(script.containsMatchIn(category.code + category.municipalSubjectCode + category.labelKey))
        }
    }

    @Test
    fun categoryLabelsAreTranslatedWhenShownAndFollowALanguageSwitch() {
        val hebrew = categories.filterKeys { it != "category.needs_correction" }.values.toList()
        assertEquals(hebrew, ReportCategory.entries.map { HebrewPresentation.category(he, it.code) })
        assertEquals(
            listOf("Parking on the sidewalk", "Broken traffic sign", "Faded crosswalk", "Request a bollard"),
            ReportCategory.entries.map { HebrewPresentation.category(en, it.code) },
        )
        assertEquals("נדרש תיקון קטגוריה", HebrewPresentation.category(he, ""))
        assertEquals("Category needs correcting", HebrewPresentation.category(en, "unknown"))
        for (category in ReportCategory.entries) {
            for (code in languages) {
                assertEquals("${category.code}/$code", raw.getJSONObject(category.labelKey).getString(code),
                    HebrewPresentation.category(Strings(catalog, code), category.code))
            }
            assertNotEquals(HebrewPresentation.category(he, category.code), HebrewPresentation.category(en, category.code))
        }
    }

    @Test
    fun evidenceTimeKeepsTheOldHebrewBytesAndIsTranslatedWhenShown() {
        assertEquals("01/01/2026 14:00", HebrewPresentation.evidenceTime(he, "2026-01-01T12:00:00Z"))
        assertEquals("01/01/2026 12:00 (אזור זמן לא ידוע)", HebrewPresentation.evidenceTime(he, "2026-01-01T12:00:00"))
        assertEquals("חסר", HebrewPresentation.evidenceTime(he, null))
        assertEquals("חסר", HebrewPresentation.evidenceTime(he, ""))
        assertEquals("not-a-time", HebrewPresentation.evidenceTime(he, "not-a-time"))
        assertEquals("01/01/2026 12:00 (time zone unknown)", HebrewPresentation.evidenceTime(en, "2026-01-01T12:00:00"))
        assertEquals("missing", HebrewPresentation.evidenceTime(en, null))
        for (code in languages) {
            val s = Strings(catalog, code)
            assertEquals(code, "01/01/2026 12:00 (" + raw.getJSONObject("presentation.time.unknown_zone").getString(code) + ")",
                HebrewPresentation.evidenceTime(s, "2026-01-01T12:00:00"))
            assertEquals(code, raw.getJSONObject("presentation.time.missing").getString(code), HebrewPresentation.evidenceTime(s, null))
            // An evidence time with a known offset is digits and Latin in every language.
            assertEquals(code, "01/01/2026 14:30", HebrewPresentation.evidenceTime(s, "2026-01-01T14:30:00+02:00"))
        }
        // The review line built from it (PR-2b) keeps its Hebrew bytes, and follows the language.
        assertEquals("זמן: $fsi${lri}01/01/2026 12:00 (אזור זמן לא ידוע)$pdi$pdi", reviewTimeText(he, "2026-01-01T12:00:00"))
        assertEquals("זמן: $fsi${lri}חסר$pdi$pdi", reviewTimeText(he, null))
        assertEquals("Time: 01/01/2026 12:00 (time zone unknown)", reviewTimeText(en, "2026-01-01T12:00:00"))
        assertEquals("Time: missing", reviewTimeText(en, null))
    }

    /** The three files hold no text in any of the catalog's scripts. */
    @Test
    fun sourcesHoldNoScriptLiterals() {
        for (name in listOf("ReportCategory.kt", "HebrewPresentation.kt", "MainActivity.kt")) {
            val text = source(name)
            assertFalse("$name still holds script text", script.containsMatchIn(text))
            val findings = HardcodedUiStringScanner.scan(name, text)
            assertEquals("$name: $findings", 0, findings.size)
        }
        assertFalse("ReportCategory keeps no display text", Regex("""\bval hebrew\b""").containsMatchIn(source("ReportCategory.kt")))
        // MainActivity shows only the texts AppShell passes in for the device-unlock prompt.
        assertTrue(source("MainActivity.kt").contains("private fun authenticate(title: String, subtitle: String, action: () -> Unit)"))
        assertTrue(source("MainActivity.kt").contains("createConfirmDeviceCredentialIntent(title, subtitle)"))
    }

    /** Source tripwires for the Compose code the JVM cannot run (covered by M1ShellTest). */
    @Test
    fun reviewScreenShowsCategoryAndTimeThroughTheActiveLanguage() {
        val screen = source("ReviewScreen.kt")
        assertEquals(1, screen.split("HebrewPresentation.category(s, draft.subject)").size - 1)
        assertEquals(1, screen.split("Text(s.t(category.labelKey))").size - 1)
        assertEquals(1, screen.split("HebrewPresentation.evidenceTime(s, draft.evidence.capturedAtIso)").size - 1)
        assertFalse(screen.contains(".hebrew"))
        assertEquals(1, source("ReviewTexts.kt").split("HebrewPresentation.evidenceTime(strings, confirmedTime)").size - 1)
    }
}
