package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.ledger.DraftCodec
import il.sidewalks.reporter.ui.ReviewWarning
import il.sidewalks.reporter.ui.initialReviewWarnings
import il.sidewalks.reporter.ui.reportsOpenApprovedText
import il.sidewalks.reporter.ui.reportsOpenDraftText
import il.sidewalks.reporter.ui.reviewAddressSuggestionText
import il.sidewalks.reporter.ui.reviewAddressText
import il.sidewalks.reporter.ui.reviewGpsText
import il.sidewalks.reporter.ui.reviewPlateSuggestionText
import il.sidewalks.reporter.ui.reviewPlateText
import il.sidewalks.reporter.ui.reviewTimeText
import il.sidewalks.reporter.ui.reviewWarningLabels
import il.sidewalks.reporter.ui.unresolvedText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #115 PR-2b: ReviewScreen.kt, CaptureScreen.kt and ReportsScreen.kt read every
 * user-visible text from the catalog, and a draft's review questions (`unresolved`) are
 * stored as stable codes that are translated when shown. The Hebrew values are pinned
 * to the literals those files had before the extraction (main at v0.14.3), byte for byte.
 */
class ReviewCaptureReportsExtractionTest {

    private val catalog = I18nTestFiles.realCatalog()
    private val raw = JSONObject(I18nTestFiles.realCatalogText()).getJSONObject("strings")
    private val he = Strings(catalog, "he")
    private val en = Strings(catalog, "en")
    private val lri = I18nTestFiles.LRI
    private val fsi = I18nTestFiles.FSI
    private val pdi = I18nTestFiles.PDI
    private val rli = "\u2067"

    /** Key -> the Hebrew literal it replaced; a `${...}` value is the `{placeholder}` here. */
    private val review = linkedMapOf(
        "review.title" to "בדיקת הדיווח",
        "review.edit.start" to "ערוך",
        "review.edit.done" to "סיום עריכה",
        "review.image_description" to "צילום ראיה",
        "review.category_note" to "זיהוי קטגוריה אוטומטי עדיין בפיתוח. בחר קטגוריה לבדיקה.",
        "review.plate_not_confirmed" to "לא אושרה",
        "review.ocr.analyzing" to "מנתח את הצילום במכשיר...",
        "review.ocr.no_plate" to "לא זוהתה לוחית; ניתן לערוך",
        "review.ocr.suggestions_found" to "נמצאו הצעות. בדוק כל ספרה בצילום",
        "review.address" to "כתובת: {address}",
        "review.address_missing" to "נדרשת השלמה בעריכה",
        "review.text_missing" to "תיאור הדיווח נדרש בעריכה",
        "review.unresolved" to "דרוש בירור: {items}",
        "review.edit.original_time_title" to "זמן הראיה המקורי",
        "review.edit.gps" to "GPS: {status}",
        "review.edit.gps_missing" to "חסר",
        "review.edit.gps_read" to "נקרא מהראיה",
        "review.edit.privacy_note" to "התמונות והלוחיות מעובדות במכשיר ואינן נשלחות ל-Google. ML Kit שולח ל-Google נתוני שימוש וביצועים ועשוי לפנות לשרתיה לעדכונים. תוצאה היא הצעה בלבד.",
        "review.edit.use_mlkit" to "זיהוי עם Google ML Kit במקום Tesseract",
        "review.edit.recognizing" to "מזהה במכשיר...",
        "review.edit.suggest_plate" to "הצע לוחית מתוך הצילום",
        "review.edit.plate_field" to "לוחית ללא מקפים",
        "review.edit.address_field" to "רחוב ומספר בית שאישרת",
        "review.edit.geocode_note" to "חיפוש כתובת שולח קואורדינטות בלבד לספק המיקום של המכשיר. מספרי בית הם הצעות בלבד.",
        "review.edit.geocode_button" to "חפש הצעות כתובת לפי המיקום",
        "review.geocode.no_suggestions" to "אין הצעות, הזן כתובת שבדקת",
        "review.geocode.check_house_number" to "בדוק ואשר את מספר הבית בעצמך",
        "review.address_suggestion" to "הצעה בלבד: {address}",
        "review.edit.text_field" to "הטקסט המדויק לבדיקה",
        "review.edit.confirm_note" to "בדוק את הרחוב ומספר הבית ואשר את זמן הצילום. אין לנחש מידע חסר. מיקום התמונה המקורי נשמר בנפרד.",
        "review.edit.timezone_toggle" to "תיקון אזור זמן",
        "review.edit.timezone_field" to "אזור זמן (למשל +02:00)",
        "review.edit.timezone_note" to "רק אם אזור הזמן חסר או שגוי: הזן היסט שבדקת לזמן הצילום. אין לנחש.",
        "review.location.missing_plain" to "לא נמצא מיקום בתמונה. בחר תמונה עם מיקום כדי לשלוח דיווח.",
        "review.edit.jerusalem_time_note" to "הזמן מוצג לפי השעון המקומי בירושלים. אשר רק זמן שבדקת.",
        "review.edit.time_field" to "זמן הצילום (24 שעות)",
        "review.edit.latitude_field" to "קו רוחב",
        "review.edit.longitude_field" to "קו אורך",
        "review.edit.confirm_button" to "אשר זמן, מיקום וכתובת שבדקת",
        "review.approve_button" to "אשר את התוכן לבדיקה",
        "review.approved" to "התוכן אושר מקומית, לא נשלח",
        "review.not_approved" to "התוכן לא אושר",
        "review.save_draft" to "שמור טיוטה",
        "review.outgoing_preview" to "בדוק תמונה נפרדת ללא נתוני צילום",
        "review.submit.button" to "שלח לעירייה",
        "review.submit.sending" to "שולח לעירייה...",
        "review.submit.missing_credentials" to "לא ניתן לשלוח - יש להגדיר בהגדרות את הערכים:",
        "review.submit.disabled_button" to "שלח",
        "review.submit.unavailable" to "שליחה אינה זמינה בגרסת הבדיקה",
        "review.back" to "חזור",
        "review.geocode.loading" to "מחפש רחוב ומספר בית לפי מיקום התמונה…",
        "review.location.show_advanced" to "תיקון מיקום מתקדם",
        "review.location.hide_advanced" to "הסתר תיקון מיקום מתקדם",
        "review.location.advanced_note" to "רק אם המיקום חסר או שגוי: הזן קואורדינטות שבדקת. מיקום התמונה המקורי נשמר בנפרד.",
        "review.location.lookup_title" to "למצוא רחוב ומספר בית?",
        "review.location.lookup_yes" to "כן, חפש כתובת",
        "review.location.lookup_later" to "לא עכשיו",
        "review.location.missing" to "חסר מיקום מאושר; יש להשלים מיקום לפני אישור הדיווח.",
        "review.location.enter_missing" to "השלם מיקום חסר",
    )

    /**
     * The old literals had an explicit LRI..PDI around these values
     * (`"לוחית: \u2066${...}\u2069"`); the catalog now supplies the isolate, so the value is
     * the literal without them. The rendered Hebrew stays byte-identical (pinned below).
     */
    private val isolatedByTheCatalog = linkedMapOf(
        "review.plate" to "לוחית: {plate}",
        "review.plate_suggestion" to "הצעה בלבד: {plate}",
        "review.time" to "זמן: {time}",
    )

    /** The review questions: key -> the Hebrew text that drafts stored before PR-2b. */
    private val warnings = linkedMapOf(
        "review.warning.missing_gps" to "GPS חסר",
        "review.warning.missing_time" to "זמן חסר",
        "review.warning.timezone_needs_confirmation" to "אזור זמן דורש אישור",
        "review.warning.address_needs_confirmation" to "כתובת ומספר בית דורשים אישור",
        "review.warning.suggested_address_needs_confirmation" to "כתובת דורשת אישור",
        "review.warning.time_needs_confirmation" to "זמן דורש אישור",
        "review.warning.location_needs_confirmation" to "מיקום דורש אישור",
    )

    private val capture = linkedMapOf(
        "capture.title" to "צילום ראיה מקורית",
        "capture.camera_unavailable" to "המצלמה אינה זמינה",
        "capture.failed" to "הצילום נכשל, לא נשמרה ראיה",
        "capture.saving" to "שומר...",
        "capture.shutter" to "צלם",
        "capture.back" to "חזור",
    )

    private val reports = linkedMapOf(
        "reports.title" to "הדיווחים שלי",
        "reports.pending.title" to "טיוטות שלא אושרו",
        "reports.pending.empty" to "אין טיוטות ממתינות",
        "reports.pending.open" to "פתח טיוטה {id}",
        "reports.approved.title" to "אושרו וממתינים לשליחה",
        "reports.approved.empty" to "אין דיווחים שאושרו וממתינים לשליחה",
        "reports.approved.open" to "פתח דיווח {id}",
        "reports.history.title" to "היסטוריית דיווחים",
        "reports.history.empty" to "אין דיווחים שנשלחו עדיין. שליחה חיה תופעל רק באישור נפרד, וסטטוס יגיע מדואר העירייה בלבד.",
    )

    private val all get() = review + isolatedByTheCatalog + warnings + capture + reports

    private val languages = listOf("he", "en", "ar", "ru", "am")

    private val sources = mapOf(
        "ReviewScreen.kt" to "src/main/java/il/sidewalks/reporter/ui/ReviewScreen.kt",
        "ReviewTexts.kt" to "src/main/java/il/sidewalks/reporter/ui/ReviewTexts.kt",
        "ReportsScreen.kt" to "src/main/java/il/sidewalks/reporter/ui/ReportsScreen.kt",
        "CaptureScreen.kt" to "src/main/java/il/sidewalks/reporter/capture/CaptureScreen.kt",
        "AppShell.kt" to "src/main/java/il/sidewalks/reporter/ui/AppShell.kt",
    )

    private fun source(name: String): String = I18nTestFiles.moduleFile(sources.getValue(name)).readText(Charsets.UTF_8)

    /** Every literal that names a review./capture./reports. key (t() calls and keys held in state). */
    private val keyLiteral = Regex(""""((?:review|capture|reports)\.[a-z0-9_]+(?:\.[a-z0-9_]+)*)"""")

    private fun keysNamedIn(name: String): Set<String> = keyLiteral.findAll(source(name)).map { it.groupValues[1] }.toSet()

    private fun draft(unresolved: Set<String>) =
        ReviewDraft(Evidence("stable-x", "a".repeat(64), "2026-01-01T12:00:00Z", 31.0, 35.0), "", "", "", "", unresolved)

    @Test
    fun extractedHebrewValuesEqualTheOldLiteralsByteForByte() {
        for ((key, literal) in all) {
            assertEquals("he value of $key", literal, raw.getJSONObject(key).getString("he"))
        }
    }

    @Test
    fun plainHebrewValuesRenderExactlyAsBefore() {
        for ((key, literal) in all) {
            if (literal.contains('{')) continue
            assertEquals(key, literal, catalog.t("he", key))
        }
    }

    @Test
    fun everyUsedKeyExistsInAllFiveLanguagesAndEveryScreenKeyIsUsed() {
        val used = keysNamedIn("ReviewScreen.kt") + keysNamedIn("ReviewTexts.kt") +
            keysNamedIn("ReportsScreen.kt") + keysNamedIn("CaptureScreen.kt")
        for (key in used) {
            val entry = catalog.rawEntry(key)
            assertTrue("$key is used but missing from strings.json", entry != null)
            for (code in languages) assertTrue("$key/$code", entry!!.optString(code).isNotBlank())
        }
        val retired = setOf("review.edit.latitude_field", "review.edit.longitude_field", "review.edit.timezone_toggle", "review.edit.timezone_field", "review.edit.timezone_note", "review.location.show_advanced", "review.location.hide_advanced", "review.location.advanced_note", "review.location.missing", "review.location.enter_missing")
        val screenKeys = catalog.keys.filter { it.startsWith("review.") || it.startsWith("capture.") || it.startsWith("reports.") }.toSet() - retired
        assertEquals("review./capture./reports. keys in the catalog vs the keys the screens use", screenKeys, used)
        assertEquals(all.keys - retired, screenKeys)
        assertTrue(keysNamedIn("CaptureScreen.kt").all { it.startsWith("capture.") })
        assertTrue(keysNamedIn("ReportsScreen.kt").all { it.startsWith("reports.") })
        assertTrue((keysNamedIn("ReviewScreen.kt") + keysNamedIn("ReviewTexts.kt")).all { it.startsWith("review.") })
    }

    @Test
    fun interpolatedReviewTextsKeepTheOldHebrewBytes() {
        // Byte-identical to the old literals: digits and Latin get LRI..PDI, as the old code did by hand.
        assertEquals("לוחית: ${lri}1234567$pdi", reviewPlateText(he, "1234567"))
        assertEquals("הצעה בלבד: ${lri}12345678$pdi", reviewPlateSuggestionText(he, "12345678"))
        assertEquals("זמן: ${lri}01/01/2026 14:00$pdi", reviewTimeText(he, "2026-01-01T12:00:00Z"))
        assertEquals("זמן: ${lri}01/01/2026 12:00$pdi", reviewTimeText(he, "2026-01-01T12:00:00+02:00"))
        assertEquals("פתח טיוטה ${lri}aaaabbbb$pdi", reportsOpenDraftText(he, "aaaabbbb"))
        assertEquals("פתח דיווח ${lri}ccccdddd$pdi", reportsOpenApprovedText(he, "ccccdddd"))
        // Hebrew values get the catalog's FSI..PDI where the old line had LRI..PDI or nothing.
        assertEquals("לוחית: ${fsi}לא אושרה$pdi", reviewPlateText(he, ""))
        assertEquals("כתובת: ${fsi}הנביאים 12$pdi", reviewAddressText(he, "הנביאים 12"))
        assertEquals("כתובת: ${fsi}נדרשת השלמה בעריכה$pdi", reviewAddressText(he, " "))
        assertEquals("הצעה בלבד: ${fsi}הנביאים 12, ירושלים$pdi", reviewAddressSuggestionText(he, "הנביאים 12, ירושלים"))
        assertEquals("GPS: ${fsi}חסר$pdi", reviewGpsText(he, hasGps = false))
        assertEquals("GPS: ${fsi}נקרא מהראיה$pdi", reviewGpsText(he, hasGps = true))
        // A time with Hebrew in it stays left to right as before: the line keeps an LRI inside the catalog's FSI.
        assertEquals("זמן: $fsi${lri}01/01/2026 12:00 (אזור זמן לא ידוע)$pdi$pdi", reviewTimeText(he, "2026-01-01T12:00:00"))
        assertEquals("זמן: $fsi${lri}חסר$pdi$pdi", reviewTimeText(he, null))
        // LTR languages are not isolated by the catalog.
        assertEquals("Plate: 1234567", reviewPlateText(en, "1234567"))
        assertEquals("Plate: not confirmed", reviewPlateText(en, ""))
        assertEquals("Time: 01/01/2026 14:00", reviewTimeText(en, "2026-01-01T12:00:00Z"))
        assertEquals("Open draft aaaabbbb", reportsOpenDraftText(en, "aaaabbbb"))
        assertEquals("GPS: missing", reviewGpsText(en, hasGps = false))
    }

    /** Stable storage codes: changing one orphans every saved draft that carries it. */
    @Test
    fun warningCodesAreStableAndEveryWarningHasItsKey() {
        assertEquals(
            listOf(
                "missing_gps", "missing_time", "timezone_needs_confirmation", "address_needs_confirmation",
                "suggested_address_needs_confirmation", "time_needs_confirmation", "location_needs_confirmation",
            ),
            ReviewWarning.entries.map { it.code },
        )
        assertEquals(warnings.keys.toList(), ReviewWarning.entries.map { it.key })
        for (w in ReviewWarning.entries) assertEquals(w, ReviewWarning.fromCode(w.code))
        assertNull(ReviewWarning.fromCode("GPS חסר"))
    }

    @Test
    fun newDraftsStoreCodesNotText() {
        val full = Evidence("s1", "a".repeat(64), "2026-01-01T12:00:00+02:00", 31.0, 35.0)
        assertEquals(setOf("timezone_needs_confirmation", "address_needs_confirmation"), initialReviewWarnings(full))
        val bare = Evidence("s2", "b".repeat(64), null, null, null)
        assertEquals(listOf("missing_gps", "missing_time", "address_needs_confirmation"), initialReviewWarnings(bare).toList())
        val noGps = Evidence("s3", "c".repeat(64), "2026-01-01T12:00:00Z", null, 35.0)
        assertEquals(listOf("missing_gps", "timezone_needs_confirmation", "address_needs_confirmation"), initialReviewWarnings(noGps).toList())
        val script = Regex("[\u0590-\u05FF\u0600-\u06FF\u0400-\u04FF\u1200-\u139F]")
        for (code in initialReviewWarnings(bare) + initialReviewWarnings(full)) {
            assertFalse("stored value must be a code: $code", script.containsMatchIn(code))
            assertTrue("stored value must be a known code: $code", ReviewWarning.fromCode(code) != null)
        }
        // The ledger keeps the codes as they are.
        val stored = DraftCodec.decode(DraftCodec.encode(draft(initialReviewWarnings(bare))))
        assertEquals(initialReviewWarnings(bare), stored.unresolved)
    }

    @Test
    fun codesAreTranslatedWhenShownAndFollowALanguageSwitch() {
        val codes = initialReviewWarnings(Evidence("s2", "b".repeat(64), null, null, null))
        assertEquals(listOf("GPS חסר", "זמן חסר", "כתובת ומספר בית דורשים אישור"), reviewWarningLabels(he, codes))
        assertEquals(listOf("GPS missing", "Time missing", "Address and house number need confirmation"), reviewWarningLabels(en, codes))
        assertEquals(
            "דרוש בירור: $fsi${rli}GPS חסר, זמן חסר, כתובת ומספר בית דורשים אישור$pdi$pdi",
            unresolvedText(he, codes),
        )
        assertEquals("Needs checking: GPS missing, Time missing, Address and house number need confirmation", unresolvedText(en, codes))
        for (w in ReviewWarning.entries) {
            for (code in languages) {
                assertEquals("${w.code}/$code", raw.getJSONObject(w.key).getString(code), reviewWarningLabels(Strings(catalog, code), setOf(w.code)).single())
            }
        }
    }

    /** Drafts saved before PR-2b hold the text: the old Hebrew, or (since PR-2a) another language. */
    @Test
    fun legacyDraftsWithStoredTextStillShowAndAreTranslated() {
        for ((key, oldHebrew) in warnings) {
            val warning = ReviewWarning.entries.single { it.key == key }
            assertEquals(warning, ReviewWarning.fromStored(oldHebrew, catalog))
            assertEquals(oldHebrew, reviewWarningLabels(he, setOf(oldHebrew)).single())
            assertEquals(en.t(key), reviewWarningLabels(en, setOf(oldHebrew)).single())
        }
        // A PR-2a draft made while English or Arabic was active.
        assertEquals(ReviewWarning.MISSING_GPS, ReviewWarning.fromStored("GPS missing", catalog))
        assertEquals(listOf("GPS חסר"), reviewWarningLabels(he, setOf("GPS missing")))
        assertEquals(ReviewWarning.ADDRESS_NEEDS_CONFIRMATION, ReviewWarning.fromStored(raw.getJSONObject("review.warning.address_needs_confirmation").getString("ar"), catalog))
        // A legacy draft as the ledger holds it: the codec sorts the set.
        val legacy = DraftCodec.decode(DraftCodec.encode(draft(setOf("GPS חסר", "זמן חסר", "כתובת ומספר בית דורשים אישור"))))
        assertEquals(listOf("GPS missing", "Time missing", "Address and house number need confirmation"), reviewWarningLabels(en, legacy.unresolved))
        assertEquals(reviewWarningLabels(he, initialReviewWarnings(Evidence("s2", "b".repeat(64), null, null, null))), reviewWarningLabels(he, legacy.unresolved))
    }

    @Test
    fun unknownStoredTextIsShownAsIsAfterTheKnownOnes() {
        assertNull(ReviewWarning.fromStored("plate", catalog))
        assertEquals(listOf("GPS missing", "plate", "שאלה ידנית"), reviewWarningLabels(en, linkedSetOf("plate", "missing_gps", "שאלה ידנית")))
        assertEquals("Needs checking: something else", unresolvedText(en, setOf("something else")))
    }

    /** Source tripwire: each two-way text keeps its branches (a swap shows the wrong state). */
    @Test
    fun conditionalTextsKeepTheirBranches() {
        val screen = source("ReviewScreen.kt")
        for (snippet in listOf(
            """s.t(if (editing) "review.edit.done" else "review.edit.start")""",
            """s.t(if (recognizing) "review.edit.recognizing" else "review.edit.suggest_plate")""",
            """s.t(if (draft.isApproved()) "review.approved" else "review.not_approved")""",
            """s.t(if (submitting) "review.submit.sending" else "review.submit.button")""",
            """if (plates.isEmpty()) "review.ocr.no_plate" else "review.ocr.suggestions_found"""",
            """if (suggestions.isEmpty()) "review.geocode.no_suggestions" else "review.geocode.check_house_number"""",
        )) assertEquals(snippet, 1, screen.split(snippet).size - 1)
        assertEquals(1, source("CaptureScreen.kt").split("""s.t(if (pending) "capture.saving" else "capture.shutter")""").size - 1)
    }

    /**
     * Source tripwires for the Compose code the JVM cannot run (the screens are covered
     * by the androidTests): every review question the screens store is a code, and the
     * screens take their direction from the active language.
     */
    @Test
    fun screensStoreCodesAndFollowTheActiveLanguage() {
        val screen = source("ReviewScreen.kt")
        val stores = Regex("""unresolved = setOf\(([^)]*)\)""").findAll(screen).map { it.groupValues[1] }.toList()
        assertEquals(3, stores.size)
        assertEquals("suggestions add, never erase, existing warnings", 2, Regex("""unresolved = draft\.unresolved \+ ReviewWarning\.SUGGESTED_ADDRESS_NEEDS_CONFIRMATION\.code""").findAll(screen).count())
        assertTrue("ReviewScreen must store codes: $stores", stores.all { Regex("""ReviewWarning\.[A-Z_]+\.code""").matches(it) })
        val shell = source("AppShell.kt")
        assertEquals(1, Regex("""val missing = initialReviewWarnings\(evidence\)\n""").findAll(shell).count())
        assertEquals("AppShell hands the codes to the draft unchanged", 2, Regex("""ReviewDraft\(evidence, "", "", "", "", missing\)""").findAll(shell).count())
        assertFalse("AppShell never stores a translated warning", shell.contains("shell.review.") || shell.contains("review.warning.") || Regex("""\bReviewWarning\b""").containsMatchIn(shell))
        assertEquals("both unresolved lines go through unresolvedText()", 2, Regex("""unresolvedText\(s, draft\.unresolved\)""").findAll(screen).count())
        assertFalse("ReviewScreen shows unresolved through unresolvedText()", screen.contains("unresolved.joinToString"))
        for (name in listOf("ReviewScreen.kt", "ReportsScreen.kt", "CaptureScreen.kt")) {
            val text = source(name)
            assertFalse("$name must not force a layout direction", text.contains("LayoutDirection.Rtl"))
            assertTrue("$name reads the catalog", text.contains("ProvideStringsIfMissing {") && text.contains("LocalStrings.current"))
        }
    }
}
