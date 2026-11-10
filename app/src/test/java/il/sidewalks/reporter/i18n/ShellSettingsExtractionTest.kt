package il.sidewalks.reporter.i18n

import il.sidewalks.reporter.municipal.SubmissionCoordinator.Outcome
import il.sidewalks.reporter.municipal.SubmissionCoordinator.Reason
import il.sidewalks.reporter.ui.municipalOutcomeText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #115 PR-2a: AppShell.kt and SettingsScreen.kt read every user-visible text from
 * the catalog. The Hebrew values are pinned to the literals those files had before the
 * extraction (main at v0.12.1), byte for byte, so the Hebrew UI and the instrumented
 * tests that look texts up by their Hebrew wording are unchanged.
 */
class ShellSettingsExtractionTest {

    private val catalog = I18nTestFiles.realCatalog()
    private val raw = JSONObject(I18nTestFiles.realCatalogText()).getJSONObject("strings")
    /** The catalog default (the source language, Hebrew) and English, named by its own name (td423). */
    private val source = I18nTestFiles.defaultCode()
    private val english = I18nTestFiles.codeNamed("English")

    /** Key -> the Hebrew literal it replaced. */
    private val extracted = linkedMapOf(
        "settings.passport.mode" to "ללא תעודת זהות ישראלית",
        "settings.passport.number" to "מספר דרכון",
        "shell.passport.review_required" to "נדרש גילוי על פרטי הקשר ומספרי מצייני המקום בנוסח הדיווח. בדוק את הנוסח המלא, שמור ואשר מחדש לפני שליחה. אם פרטי הקשר חסרים או אינם תקינים, תקן אותם בהגדרות.",
        "settings.api.advanced" to "חיבור 106 מתקדם",
        "settings.phone.international" to "מספר שאינו נייד ישראלי: המספר שלך יופיע בנוסח הדיווח; בשדה העירייה יישלח מספר מציין מקום לאחר אישורך.",
        "settings.email.invalid" to "כתובת האימייל אינה תקינה. בדוק את הכתובת.",

        "settings.id.invalid" to "מספר תעודת הזהות אינו תקין. בדוק את הספרות וספרת הביקורת.",
        "settings.phone.invalid" to "הזן מספר בינלאומי עם + וקוד מדינה, או מספר נייד ישראלי שמתחיל ב-05.",

        "shell.nav.home" to "בית",
        "shell.nav.reports" to "הדיווחים שלי",
        "shell.nav.settings" to "הגדרות",
        "shell.home.title" to "מדרכות פנויות",
        "shell.home.subtitle" to "צלם תמונה כדי להתחיל דיווח",
        "shell.home.on_device_note" to "הניתוח נעשה במכשיר. תוכל לבדוק ולתקן לפני שליחה.",
        "shell.home.take_photo" to "צלם תמונה",
        "shell.home.upload_photo" to "העלה תמונה",
        "shell.home.upload_note" to "העלאה מהגלריה אינה דורשת הרשאות; זמן ומיקום נקראים מהתמונה אם קיימים.",
        "shell.home.test_build_note" to "גרסת בדיקה מקומית. אין שליחה.",
        "shell.home.drafts_title" to "טיוטות",
        "shell.home.no_drafts" to "אין טיוטות שמורות",
        "shell.home.open_draft" to "פתח טיוטה {id}",
        "shell.home.continue_review" to "המשך בדיקת טיוטה",
        "shell.home.login_unavailable" to "התחברות לשירות הדיווח עדיין אינה זמינה. פרטי גישה עירוניים אינם חלק מהאפליקציה.",
        // The four review questions AppShell adds to a new draft moved to review.warning.*
        // in PR-2b (stored as codes, translated when shown): ReviewCaptureReportsExtractionTest.
        "shell.home.permission_note" to "ביטול הרשאת מצלמה או אימות משאיר את המסך ללא פעולה. אין שמירת פרטים גלויים כחלופה.",
        "shell.outgoing.title" to "תמונה נפרדת לבדיקה",
        "shell.outgoing.image_description" to "התמונה המדויקת המיועדת להעברה בעתיד",
        "shell.outgoing.explanation" to "המקור לא השתנה. נתוני EXIF הוסרו מהעותק. בדוק שהלוחית והעבירה קריאות; אל תאשר תמונה שאיבדה ראיה.",
        "shell.outgoing.no_sending" to "שליחה והתחברות אינן זמינות בגרסת הבדיקה. אין אישור שליחה ממסך זה.",
        "shell.outgoing.back_to_review" to "חזור לבדיקה",
        "shell.import_location.title" to "נמצא מיקום בתמונה",
        "shell.import_location.message" to "להשתמש במיקום מהתמונה לדיווח? ניתן גם בלי, ולסמן את המיקום ידנית.",
        "shell.import_location.use" to "השתמש במיקום",
        "shell.import_location.skip" to "ללא מיקום",
        "shell.status.draft_saved" to "נשמר מקומית, לא נשלח",
        "shell.status.draft_save_failed" to "שמירה נכשלה, לא בוצעה שליחה",
        "shell.status.settings_read_failed" to "לא ניתן לקרוא מידע מוצפן",
        "shell.status.evidence_duplicate_or_unsaved" to "הראיה כפולה או שלא נשמרה, לא בוצעה שליחה",
        "shell.status.evidence_restore_failed" to "לא ניתן לשחזר את הראיה; אין שליחה",
        "shell.status.image_read_failed" to "התמונה לא נקראה, לא בוצעה שליחה",
        "shell.status.outgoing_prepare_failed" to "לא ניתן להכין תמונה לבדיקה; אין שליחה",
        "shell.status.settings_saved" to "נשמר מוצפן",
        "shell.status.settings_save_failed" to "הצפנה או אימות נכשלו, לא נשמר מידע גלוי",
        "shell.status.import_cancelled" to "הייבוא בוטל, לא בוצעה שליחה",
        "shell.debug.title" to "כלי בדיקה (מצב פיתוח בלבד)",
        "shell.debug.description" to "בדיקת פיתוח מקומית של חבילת ההצפנה: יוצרת דיווח מלאכותי, מצפינה ופותחת במכשיר. ללא רשת, ללא שליחה. מוסתרת לחלוטין מחוץ למצב פיתוח.",
        "shell.debug.run_button" to "בדיקת פיתוח: חבילה מוצפנת ללא רשת",
        "shell.debug.running" to "מריץ בדיקה סינתטית מקומית",
        "shell.debug.failed" to "הבדיקה המקומית נכשלה, אין שליחה",
        "settings.privacy.mlkit_note" to "Google ML Kit: התמונות והלוחיות נשארות במכשיר; נתוני שימוש וביצועים נשלחים ל-Google. ה-SDK עשוי לפנות לשרתיה לעדכונים.",
        "settings.privacy.title" to "הגדרות פרטיות",
        "settings.privacy.description" to "כל מדווח מזין את פרטיו שלו. הפרטים מוצפנים במכשיר, ללא גיבוי. אין שליחה או בדיקת התחברות ממסך זה. פרטי הגישה העירוניים מוזנים ידנית ונשמרים מוצפנים במכשיר בלבד.",
        "settings.field.first_name" to "שם פרטי",
        "settings.field.last_name" to "שם משפחה",
        "settings.field.id_number" to "מספר זהות",
        "settings.field.phone" to "טלפון",
        "settings.field.email" to "אימייל",
        "settings.api.title" to "גישת API עירונית (הזנה ידנית)",
        "settings.api.description" to "המפתחות מוזנים ידנית ונשמרים מוצפנים במכשיר בלבד, ללא גיבוי. הם אינם חלק מהאפליקציה ואינם נשלחים למקום אחר. שליחה חיה אינה זמינה ותופעל רק באישור נפרד.",
        "settings.field.subscription_key" to "מפתח מנוי",
        "settings.field.api_username" to "שם משתמש API",
        "settings.field.api_password" to "סיסמת API",
        "settings.save.button" to "אמת זהות ושמור מוצפן",
        "settings.save.done" to "נשמר מוצפן, הפרטים הוסרו מהמסך",
        "settings.back" to "חזור",
        "settings.version" to "גרסה {version}",
    )

    /**
     * Keys for the M-B submit texts (#160, main at v0.13.2) -> the Hebrew literal they
     * replaced; a `${...}` value in the old literal is the `{placeholder}` here.
     */
    private val submitExtracted = linkedMapOf(
        "shell.submit.sent" to "נשלח לעירייה. מספר קריאה: {case_number}",
        "shell.submit.case_number_received" to "התקבל בעירייה",
        "shell.submit.already_sent" to "הדיווח כבר נשלח. מספר קריאה: {case_number}",
        "shell.submit.blocked.not_approved" to "אשר את התוכן לפני שליחה",
        "shell.submit.blocked.missing_credentials" to "חסרים פרטי גישה לעירייה בהגדרות",
        "shell.submit.blocked.unsupported_category" to "הקטגוריה אינה נתמכת לשליחה",
        "shell.submit.blocked.address_unresolved" to "הכתובת לא נקבעה: {detail}",
        "shell.submit.blocked.input_invalid" to "קלט לא תקין: {detail}",
        "shell.submit.blocked.municipal_review_needed" to "המצב מול העירייה אינו ודאי. בדוק באפליקציית העירייה; לא בוצעה שליחה חוזרת. {detail}",
        "shell.submit.blocked.municipal_api_failure" to "העירייה ענתה: {detail}",
        "shell.submit.blocked.local_failure" to "שגיאה מקומית, לא בוצעה שליחה ({detail})",
        "shell.submit.blocked.lookup_not_installed" to "קובץ הכתובות הפרטי לא מותקן במכשיר",
        "shell.submit.blocked.lookup_mismatch" to "קובץ הכתובות במכשיר מיושן או שונה. התקן את העותק העדכני",
        "shell.submit.draft_not_saved" to "הטיוטה לא נשמרה אחרי האישור. שמור ואשר מחדש לפני שליחה.",
        "shell.submit.sending" to "שולח לעירייה...",
        "shell.unlock.submit_title" to "שליחת דיווח לעירייה",
        "shell.unlock.submit_subtitle" to "אימות נעילת המכשיר מאשר שליחת הדיווח לעיריית ירושלים",
        "shell.unlock.settings_title" to "הגדרות פרטיות",
        "shell.unlock.settings_subtitle" to "אמת את נעילת המכשיר כדי לגשת לפרטים",
    )

    /** Keys added by PR-2a for the language picker (new texts, no old literal). */
    private val pickerKeys = setOf("settings.language.title", "settings.language.system_default")

    private val sizeSummaryHe = "{width} × {height}, {count} bytes"

    private fun source(name: String): String =
        I18nTestFiles.moduleFile("src/main/java/il/sidewalks/reporter/ui/$name").readText(Charsets.UTF_8)

    private val usedKey = Regex("""\.tp?\(\s*"([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)"""")

    private fun keysUsedIn(name: String): Set<String> = usedKey.findAll(source(name)).map { it.groupValues[1] }.toSet()

    @Test
    fun extractedHebrewValuesEqualTheOldLiteralsByteForByte() {
        for ((key, literal) in extracted + submitExtracted) {
            assertEquals("he value of $key", literal, raw.getJSONObject(key).getString(source))
        }
        val plural = raw.getJSONObject("shell.outgoing.size_summary").getJSONObject(source)
        for (category in listOf("one", "two", "other")) {
            assertEquals("size_summary he/$category", sizeSummaryHe, plural.getString(category))
        }
    }

    @Test
    fun plainHebrewValuesRenderExactlyAsBefore() {
        for ((key, literal) in extracted + submitExtracted) {
            if (literal.contains('{')) continue
            assertEquals(key, literal, catalog.t(source, key))
        }
    }

    @Test
    fun interpolatedValuesKeepTheOldWordingAroundIsolatedValues() {
        val lri = Bidi.LRI
        val pdi = Bidi.PDI
        assertEquals("פתח טיוטה ${lri}aaaabbbb$pdi", catalog.t(source, "shell.home.open_draft", mapOf("id" to "aaaabbbb")))
        assertEquals("גרסה ${lri}0.12.1$pdi", catalog.t(source, "settings.version", mapOf("version" to "0.12.1")))
        assertEquals(
            "${lri}1280$pdi × ${lri}960$pdi, ${lri}2048$pdi bytes",
            catalog.tp(source, "shell.outgoing.size_summary", 2048, mapOf("width" to 1280, "height" to 960)),
        )
        assertEquals("1280 × 960, 1 byte", catalog.tp(english, "shell.outgoing.size_summary", 1, mapOf("width" to 1280, "height" to 960)))
        assertEquals("Version 0.12.1", catalog.t(english, "settings.version", mapOf("version" to "0.12.1")))
    }

    @Test
    fun everyKeyTheTwoScreensUseExistsAndEveryScreenKeyIsUsed() {
        val used = keysUsedIn("AppShell.kt") + keysUsedIn("SettingsScreen.kt")
        val screenKeys = catalog.keys.filter { it.startsWith("shell.") || it.startsWith("settings.") }.toSet()
        // The exact set equality between the catalog's shell./settings. keys and the keys this
        // test lists, split into its two directions and checked first, so a mismatch names the
        // offending keys, sorted, one per line (the one long set diff hid #175's six keys).
        val listedKeys = extracted.keys + submitExtracted.keys + pickerKeys + "shell.outgoing.size_summary"
        val notListed = (screenKeys - listedKeys).sorted()
        assertTrue(
            "catalog keys not used by any screen, or used but not listed in this test " +
                "(extracted, submitExtracted, pickerKeys):\n" + notListed.joinToString("\n"),
            notListed.isEmpty(),
        )
        val missingFromCatalog = (listedKeys - screenKeys).sorted()
        assertTrue(
            "keys used by a screen (as listed in this test) but missing from the catalog:\n" +
                missingFromCatalog.joinToString("\n"),
            missingFromCatalog.isEmpty(),
        )
        for (key in used) assertTrue("$key is used but missing from strings.json", catalog.rawEntry(key) != null)
        // SettingsScreen also links to the Licenses screen using that screen's own
        // title key (#163); it is not one of the two screens' shell./settings. keys.
        val ownUsed = used - "licenses.screen.title" - setOf("setup.title", "setup.description", "setup.save_continue", "setup.later")
        assertEquals("shell./settings. keys in the catalog vs the keys the two screens use", screenKeys, ownUsed)
    }

    /**
     * Every submission outcome maps to the same Hebrew text as #160's inline mapping. The
     * only difference is the catalog's usual bidi isolation of interpolated values: a
     * case number keeps #160's LRI..PDI, a Hebrew fallback gets FSI..PDI, and a detail is
     * isolated where #160 left it bare.
     */
    @Test
    fun submissionOutcomesMapToTheSameHebrewTextsAsBefore() {
        val he = Strings(catalog, source)
        val lri = Bidi.LRI
        val fsi = Bidi.FSI
        val pdi = Bidi.PDI
        val d = "settings unreadable"
        fun blocked(reason: Reason) = municipalOutcomeText(he, Outcome.Blocked(reason, d))
        assertEquals("נשלח לעירייה. מספר קריאה: \u20661234567\u2069", municipalOutcomeText(he, Outcome.Submitted("1234567")))
        assertEquals("נשלח לעירייה. מספר קריאה: ${fsi}התקבל בעירייה$pdi", municipalOutcomeText(he, Outcome.Submitted(null)))
        assertEquals("הדיווח כבר נשלח. מספר קריאה: \u20667654321\u2069", municipalOutcomeText(he, Outcome.AlreadySubmitted("7654321")))
        assertEquals("הדיווח כבר נשלח. מספר קריאה: \u2066\u2069", municipalOutcomeText(he, Outcome.AlreadySubmitted(null)))
        val expected = mapOf(
            Reason.NOT_APPROVED to "אשר את התוכן לפני שליחה",
            Reason.MISSING_CREDENTIALS to "חסרים פרטי גישה לעירייה בהגדרות",
            Reason.UNSUPPORTED_CATEGORY to "הקטגוריה אינה נתמכת לשליחה",
            Reason.ADDRESS_UNRESOLVED to "הכתובת לא נקבעה: $lri$d$pdi",
            Reason.PASSPORT_DISCLOSURE_REQUIRED to "נדרש גילוי על פרטי הקשר ומספרי מצייני המקום בנוסח הדיווח. בדוק את הנוסח המלא, שמור ואשר מחדש לפני שליחה. אם פרטי הקשר חסרים או אינם תקינים, תקן אותם בהגדרות.",
            Reason.INPUT_INVALID to "קלט לא תקין: $lri$d$pdi",
            Reason.MUNICIPAL_REVIEW_NEEDED to "המצב מול העירייה אינו ודאי. בדוק באפליקציית העירייה; לא בוצעה שליחה חוזרת. $lri$d$pdi",
            Reason.MUNICIPAL_API_FAILURE to "העירייה ענתה: $lri$d$pdi",
            Reason.LOCAL_FAILURE to "שגיאה מקומית, לא בוצעה שליחה ($lri$d$pdi)",
            Reason.LOOKUP_NOT_INSTALLED to "קובץ הכתובות הפרטי לא מותקן במכשיר",
            Reason.LOOKUP_MISMATCH to "קובץ הכתובות במכשיר מיושן או שונה. התקן את העותק העדכני",
        )
        assertEquals("every reason is pinned", Reason.entries.toSet(), expected.keys)
        for ((reason, text) in expected) assertEquals(reason.name, text, blocked(reason))
        assertEquals("every outcome has its own text", Reason.entries.size, Reason.entries.map { blocked(it) }.toSet().size)
        assertEquals("שגיאה מקומית, לא בוצעה שליחה (${fsi}שגיאה$pdi)",
            municipalOutcomeText(he, Outcome.Blocked(Reason.LOCAL_FAILURE, "שגיאה")))
        // Owner 2026-11-09: the typed missing-field IDs name the missing values
        // (constant catalog labels); unknown IDs are dropped, not mislabeled.
        assertEquals("חסרים פרטי גישה לעירייה בהגדרות: מפתח מנוי, סיסמת API",
            municipalOutcomeText(he, Outcome.Blocked(Reason.MISSING_CREDENTIALS,
                "municipal credentials absent: subscription_key, api_password",
                missingFields = listOf("subscription_key", "api_password"))))
        assertEquals("חסרים פרטי גישה לעירייה בהגדרות: מפתח מנוי",
            municipalOutcomeText(he, Outcome.Blocked(Reason.MISSING_CREDENTIALS,
                "municipal credentials absent: subscription_key, future_field",
                missingFields = listOf("subscription_key", "future_field"))))
    }

    @Test
    fun submissionOutcomesFollowTheActiveLanguage() {
        val en = Strings(catalog, english)
        assertEquals("Sent to the municipality. Case number: 42", municipalOutcomeText(en, Outcome.Submitted("42")))
        assertEquals("Invalid input: x", municipalOutcomeText(en, Outcome.Blocked(Reason.INPUT_INVALID, "x")))
        for (code in I18nTestFiles.languageCodes()) {
            for (key in submitExtracted.keys) {
                assertTrue("$key/$code", raw.getJSONObject(key).optString(code).isNotBlank())
                val heTokens = SubmissionTokens.of(raw.getJSONObject(key).getString(source))
                assertEquals("$key/$code placeholders", heTokens, SubmissionTokens.of(raw.getJSONObject(key).getString(code)))
            }
        }
    }

    /** The unlock prompts and the submit notes are read from the catalog, never inline. */
    @Test
    fun unlockPromptsAreResolvedFromTheCatalog() {
        val shell = source("AppShell.kt")
        assertEquals(1, Regex("""authenticate\(strings\.t\("shell\.unlock\.submit_title"\),\s*strings\.t\("shell\.unlock\.submit_subtitle"\)\)""").findAll(shell).count())
        assertEquals(2, Regex("""authenticate\(strings\.t\("shell\.unlock\.settings_title"\), strings\.t\("shell\.unlock\.settings_subtitle"\)\)""").findAll(shell).count())
        assertEquals("every authenticate call uses catalog texts", 3, Regex("""authenticate\(""").findAll(shell).count())
    }

    private object SubmissionTokens {
        fun of(s: String): Set<String> = StringCatalog.PLACEHOLDER.findAll(s).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun appShellKeysUseTheShellPrefixAndSettingsKeysTheSettingsPrefix() {
        assertTrue(keysUsedIn("AppShell.kt").all { it.startsWith("shell.") })
        // The one allowed exception is the Licenses-screen link button (#163), which
        // shows that screen's own licenses.screen.title rather than duplicating it.
        assertTrue(keysUsedIn("SettingsScreen.kt").all { it.startsWith("settings.") || it.startsWith("setup.") || it == "licenses.screen.title" })
    }

    @Test
    fun setupKeysExistInAllLanguagesAndNeverReadProfiles() {
        for (key in listOf("setup.title", "setup.description", "setup.save_continue", "setup.later")) {
            for (code in I18nTestFiles.languageCodes())
                assertTrue("$key/$code", raw.getJSONObject(key).optString(code).isNotBlank())
        }
        val shell = source("AppShell.kt")
        val setup = shell.substring(shell.indexOf("if (showFirstRun) {"), shell.indexOf("    Scaffold("))
        assertTrue(!setup.contains("readSettings("))
        assertTrue(!Regex("""by\s+rememberSaveable""").containsMatchIn(setup))
        assertTrue(setup.contains("SettingsScreen(null, basicSetup = true"))
    }

    @Test
    fun pickerTextsExistInAllLanguages() {
        for (key in pickerKeys) {
            for (code in I18nTestFiles.languageCodes()) {
                assertTrue("$key/$code", raw.getJSONObject(key).optString(code).isNotBlank())
            }
        }
        assertEquals("System default", catalog.t(english, "settings.language.system_default"))
    }

    /**
     * Source tripwire for the wiring the JVM cannot run (the Compose path is covered by
     * androidTest LanguagePickerTest): AppShell hands the picker the language controller
     * directly, and SettingsScreen's picker never reaches save() or authentication.
     */
    @Test
    fun languagePickerIsWiredOnlyToTheLanguageController() {
        val shell = source("AppShell.kt")
        assertEquals(2, Regex("""onLanguageSelected\s*=\s*languageController::select\b""").findAll(shell).count())
        assertEquals(2, Regex("""onLanguageSelected\s*=""").findAll(shell).count())
        val settings = source("SettingsScreen.kt")
        assertEquals(
            "the screen hands the picker its callback unchanged",
            listOf("LanguagePicker(s.catalog.languages, selectedLanguage, select)"),
            Regex("""LanguagePicker\([^\n]*""").findAll(settings).map { it.value.trim() }.filterNot { it.startsWith("LanguagePicker(languages") }.toList(),
        )
        val picker = settings.substring(settings.indexOf("private fun LanguagePicker"))
        assertTrue("the picker must not call save", !picker.contains("save("))
        assertTrue("SettingsScreen never authenticates", !settings.contains("authenticate"))
        assertTrue("the picker never touches ReporterSettings", !picker.contains("ReporterSettings"))
    }
}
