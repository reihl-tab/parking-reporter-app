package il.sidewalks.reporter.i18n

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings -> Licenses wording follow-ups (Refs #163, #167): the ML Kit body names the
 * page its link opens (the ML Kit Terms of Service at developers.google.com/ml-kit/terms,
 * which incorporate the Google APIs Terms of Service), and the Amharic OSM body uses
 * "derived" for the ODbL derivative database instead of the stray "የተራመደው". The OSM body
 * states the boundary condition on its "reproduced exactly" claim (Refs #163).
 */
class LicensesStringsTest {

    private val raw = JSONObject(I18nTestFiles.realCatalogText()).getJSONObject("strings")

    /** Every catalog language (td423): a new language is covered by the structural checks at once. */
    private val languages = I18nTestFiles.languageCodes()

    /** Wording pins are keyed by each language's own name, so no code is written here. */
    private fun code(nativeName: String): String = I18nTestFiles.codeNamed(nativeName)

    private fun value(key: String, lang: String): String = raw.getJSONObject(key).getString(lang)

    /** A pin table may only name catalog languages; a language without a pin is checked structurally. */
    private fun pinsByCode(pins: Map<String, String>): Map<String, String> = pins.mapKeys { (name, _) -> code(name) }

    private val mlkitBody = linkedMapOf(
        "עברית" to "זיהוי טקסט בתמונות באמצעות Google ML Kit, בכפוף לתנאי השירות של ML Kit (הכוללים את תנאי השימוש של Google APIs): developers.google.com/ml-kit/terms",
        "English" to "On-image text recognition by Google ML Kit, subject to the ML Kit Terms of Service (which incorporate the Google APIs Terms of Service): developers.google.com/ml-kit/terms",
        "العربية" to "التعرف على النص في الصور بواسطة Google ML Kit، وفقًا لشروط خدمة ML Kit (التي تتضمن شروط استخدام Google APIs): developers.google.com/ml-kit/terms",
        "Русский" to "Распознавание текста на изображениях с помощью Google ML Kit в соответствии с условиями использования ML Kit (включающими условия использования Google APIs): developers.google.com/ml-kit/terms",
        "አማርኛ" to "በምስሎች ላይ ያለውን ጽሑፍ መለየት በGoogle ML Kit፣ በML Kit የአገልግሎት ውሎች ሥር (የGoogle APIs የአገልግሎት ውሎችን ያካትታሉ): developers.google.com/ml-kit/terms",
    )

    @Test
    fun mlkitBodyIsPinnedInEveryPinnedLanguage() {
        for ((lang, pinned) in pinsByCode(mlkitBody)) {
            assertEquals("licenses.mlkit.body[$lang]", pinned, value("licenses.mlkit.body", lang))
        }
    }

    @Test
    fun mlkitBodyNamesTheMlKitTermsPageItLinksTo() {
        for (lang in languages) {
            val text = value("licenses.mlkit.body", lang)
            assertTrue("$lang ends with the ML Kit terms URL", text.endsWith("developers.google.com/ml-kit/terms"))
            // "Google ML Kit" (the product) plus "ML Kit" in the terms label.
            assertEquals("$lang names ML Kit twice (product and terms)", 2, Regex("ML Kit").findAll(text).count())
            assertTrue("$lang still names the incorporated Google APIs terms", text.contains("Google APIs"))
        }
    }

    @Test
    fun amharicOsmBodyUsesDerivedNotTheOldWord() {
        val am = value("licenses.osm.body", code("አማርኛ"))
        assertTrue(am.contains("የተገኘው የአድራሻ ዳታቤዝ"))
        assertFalse(am.contains("የተራመደው"))
    }

    @Test
    fun theOldAmharicWordAppearsNowhereInTheCatalog() {
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = raw.optJSONObject(key) ?: continue
            assertFalse("$key[am]", entry.optString(code("አማርኛ")).contains("የተራመደው"))
        }
    }

    // licenses.osm.body (Refs #163): the "reproduced exactly" claim carries the same boundary
    // condition as docs/address-lookup.md. The Overpass [date:] pin does not cover the boundary
    // area, so the addresses section is reproducible only while OSM relation 1381350 and the
    // server's area derived from it are unchanged.
    private val osmBody = linkedMapOf(
        "עברית" to "נקודות הכתובות מבוססות על נתוני OpenStreetMap, © תורמי OpenStreetMap, רישיון ODbL 1.0: openstreetmap.org/copyright — מסד הנתונים הנגזר של הכתובות מופץ ברישיון ODbL 1.0 וניתן לשחזור מדויק בעזרת מחולל הקוד הפתוח במאגר האפליקציה והקלטים המקובעים המתועדים בו, כל עוד יחס הגבול 1381350 של OpenStreetMap (והאזור שהשרת גוזר ממנו) לא השתנה.",
        "English" to "Address points are based on OpenStreetMap data, © OpenStreetMap contributors, ODbL 1.0: openstreetmap.org/copyright - the derivative address database is distributed under ODbL 1.0 and can be reproduced exactly with the open-source generator in the app's repository and its documented pinned inputs, as long as the OpenStreetMap boundary relation 1381350 (and the area the server derives from it) is unchanged.",
        "العربية" to "تستند نقاط العناوين إلى بيانات OpenStreetMap، © مساهمو OpenStreetMap، رخصة ODbL 1.0: openstreetmap.org/copyright - قاعدة العناوين المشتقة موزعة بموجب ODbL 1.0 ويمكن إعادة إنتاجها بدقة عبر المولّد المفتوح المصدر في مستودع التطبيق ومدخلاته المثبتة الموثقة، ما دامت علاقة الحدود 1381350 في OpenStreetMap (والمنطقة التي يشتقها الخادم منها) دون تغيير.",
        "Русский" to "Адресные точки основаны на данных OpenStreetMap, © участники OpenStreetMap, лицензия ODbL 1.0: openstreetmap.org/copyright - производная база адресов распространяется по лицензии ODbL 1.0 и может быть точно воспроизведена генератором с открытым кодом из репозитория приложения и его задокументированными зафиксированными входными данными, пока граничное отношение OpenStreetMap 1381350 (и область, которую сервер строит на его основе) остаётся неизменным.",
        "አማርኛ" to "የአድራሻ ነጥቦች በOpenStreetMap ውሂብ ላይ የተመሰረቱ ናቸው፣ © የOpenStreetMap አበርካቾች፣ ODbL 1.0 ፍቃድ: openstreetmap.org/copyright - የተገኘው የአድራሻ ዳታቤዝ በODbL 1.0 ሥር ይሰራጫል እና በመተግበሪያው ማከማቻ ውስጥ ባለው ክፍት ምንጭ አመንጭ እና በተመዘገቡ ተቆልፈው በሚገኙ ግቤቶች በትክክል መድገም ይቻላል፣ የOpenStreetMap የወሰን ግንኙነት 1381350 (እና አገልጋዩ ከእሱ የሚያመነጨው ክልል) እስካልተቀየረ ድረስ።",
    )

    /** The exactness claim in each language; the boundary condition must follow it. */
    private val exactnessClaim = linkedMapOf(
        "עברית" to "לשחזור מדויק",
        "English" to "reproduced exactly",
        "العربية" to "بدقة",
        "Русский" to "точно воспроизведена",
        "አማርኛ" to "በትክክል",
    )

    @Test
    fun osmBodyIsPinnedInEveryPinnedLanguage() {
        for ((lang, pinned) in pinsByCode(osmBody)) {
            assertEquals("licenses.osm.body[$lang]", pinned, value("licenses.osm.body", lang))
        }
    }

    @Test
    fun osmBodyStatesTheBoundaryConditionAfterTheExactnessClaim() {
        for ((lang, claimText) in pinsByCode(exactnessClaim)) {
            val text = value("licenses.osm.body", lang)
            val claim = text.indexOf(claimText)
            val relation = text.indexOf("1381350")
            assertTrue("$lang still states the exactness claim", claim >= 0)
            assertTrue("$lang names boundary relation 1381350", relation >= 0)
            assertTrue("$lang puts the condition after the claim, in the same sentence", relation > claim)
            assertFalse(
                "$lang has no sentence end between the claim and the condition",
                text.substring(claim, relation).contains(Regex("[.።]")),
            )
            assertTrue("$lang ends with the condition", text.indexOf("1381350") > text.length - 120)
        }
    }

    @Test
    fun osmBodyKeepsAttributionLicenceAndGenerator() {
        for (lang in languages) {
            val text = value("licenses.osm.body", lang)
            assertTrue("$lang attribution", text.contains("©") && text.contains("OpenStreetMap"))
            assertEquals("$lang names ODbL 1.0 twice (source and derivative)", 2, Regex("ODbL 1\\.0").findAll(text).count())
            assertTrue("$lang copyright link", text.contains("openstreetmap.org/copyright"))
        }
        assertTrue(value("licenses.osm.body", code("English")).contains("open-source generator in the app's repository"))
        assertTrue(value("licenses.osm.body", code("עברית")).contains("מחולל הקוד הפתוח במאגר האפליקציה"))
        for (lang in languages) {
            assertTrue("$lang names the boundary relation", value("licenses.osm.body", lang).contains("1381350"))
        }
    }

    @Test
    fun everyPinTableNamesOnlyCatalogLanguages() {
        for (pins in listOf(mlkitBody, osmBody, exactnessClaim)) {
            assertTrue(pinsByCode(pins).keys.all { it in languages })
            assertEquals("one pin per language", pins.size, pinsByCode(pins).keys.toSet().size)
        }
    }
}
