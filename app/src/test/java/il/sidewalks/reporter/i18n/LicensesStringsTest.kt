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
 * "derived" for the ODbL derivative database instead of the stray "የተራመደው".
 */
class LicensesStringsTest {

    private val raw = JSONObject(I18nTestFiles.realCatalogText()).getJSONObject("strings")
    private val languages = listOf("he", "en", "ar", "ru", "am")

    private fun value(key: String, lang: String): String = raw.getJSONObject(key).getString(lang)

    private val mlkitBody = linkedMapOf(
        "he" to "זיהוי טקסט בתמונות באמצעות Google ML Kit, בכפוף לתנאי השירות של ML Kit (הכוללים את תנאי השימוש של Google APIs): developers.google.com/ml-kit/terms",
        "en" to "On-image text recognition by Google ML Kit, subject to the ML Kit Terms of Service (which incorporate the Google APIs Terms of Service): developers.google.com/ml-kit/terms",
        "ar" to "التعرف على النص في الصور بواسطة Google ML Kit، وفقًا لشروط خدمة ML Kit (التي تتضمن شروط استخدام Google APIs): developers.google.com/ml-kit/terms",
        "ru" to "Распознавание текста на изображениях с помощью Google ML Kit в соответствии с условиями использования ML Kit (включающими условия использования Google APIs): developers.google.com/ml-kit/terms",
        "am" to "በምስሎች ላይ ያለውን ጽሑፍ መለየት በGoogle ML Kit፣ በML Kit የአገልግሎት ውሎች ሥር (የGoogle APIs የአገልግሎት ውሎችን ያካትታሉ): developers.google.com/ml-kit/terms",
    )

    @Test
    fun mlkitBodyIsPinnedInAllFiveLanguages() {
        for (lang in languages) {
            assertEquals("licenses.mlkit.body[$lang]", mlkitBody[lang], value("licenses.mlkit.body", lang))
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
        val am = value("licenses.osm.body", "am")
        assertTrue(am.contains("የተገኘው የአድራሻ ዳታቤዝ"))
        assertFalse(am.contains("የተራመደው"))
    }

    @Test
    fun theOldAmharicWordAppearsNowhereInTheCatalog() {
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = raw.optJSONObject(key) ?: continue
            assertFalse("$key[am]", entry.optString("am").contains("የተራመደው"))
        }
    }
}
