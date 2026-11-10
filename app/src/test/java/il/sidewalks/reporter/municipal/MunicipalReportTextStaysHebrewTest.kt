package il.sidewalks.reporter.municipal

import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReportCategory
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.i18n.I18nTestFiles
import il.sidewalks.reporter.security.ReporterSettings
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #115 PR-2c: the UI language changes what the screens show, never what is sent to
 * the city. The CreateCase payload carries the approved text exactly as the user typed and
 * reviewed it (Hebrew), the category's numeric subject code and the confirmed address; no
 * category label, in any language, is part of it. The approval digest binds the same
 * bytes. Both are pinned here and must come out identical whatever language is active.
 */
class MunicipalReportTextStaysHebrewTest {

    private val settings = ReporterSettings(
        firstName = "Test", lastName = "User", phone = "0501234567",
        idNumber = "000000018", email = "t@example.com",
        municipalSubscriptionKey = "k", municipalUsername = "u", municipalPassword = "p",
    )

    private val reportText = "רכב חנה על המדרכה ליד מעבר החציה"

    /** sha256 of [reportText] in UTF-8: the exact bytes the city receives as CaseDescription. */
    private val reportTextSha256 = "1eea38c776f3d3719bcd6a8c50a7e13e5316de84cb4762e82d06ec5453b69e4f"

    private fun draft(category: ReportCategory): ReviewDraft {
        val evidence = Evidence("stable-115", "a".repeat(64), "2026-11-08T10:00:00+02:00", 31.7683, 35.2137)
        return ReviewDraft(evidence, if (category.needsPlate) "12345678" else "", "רחוב הנביאים 12", category.code,
            reportText, emptySet()).approve()
    }

    private fun fixture(): StreetDirectory = StreetDirectory.load("""
        {"version": 1,
         "streets": [{"StreetCode": "1001", "StreetName": "רחוב הנביאים"}],
         "addresses": [[31.7683, 35.2137, "12", "רחוב הנביאים"]]}
    """.trimIndent())

    private class RecordingClient : MunicipalClient {
        var seenReport: Map<String, Any?>? = null
        override fun login(): String = "token"
        override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> =
            mapOf("ContactIdInCRM" to "contact-1")
        override fun createCase(token: String, contact: LinkedHashMap<String, Any?>, report: LinkedHashMap<String, Any?>): Map<String, Any?> {
            seenReport = LinkedHashMap(report)
            return mapOf("CaseIdInCRM" to "c1", "WebCaseIdInCRM" to "w1", "CaseNumberInCRM" to "2611-0000001")
        }
        override fun attachPhoto(token: String, caseIdInCRM: String, webCaseIdInCRM: String, fileName: String, jpeg: ByteArray) {}
    }

    private fun submit(category: ReportCategory): Map<String, Any?> {
        val client = RecordingClient()
        val jpeg = File.createTempFile("original", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)); deleteOnExit() }
        val outcome = SubmissionCoordinator({ client }, fixture()) { InMemorySubmissionStateStore() }
            .submitApproved(draft(category), settings, jpeg)
        assertTrue("$category: $outcome", outcome is SubmissionCoordinator.Outcome.Submitted)
        return client.seenReport!!
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Runs [block] with the JVM default locale set to each catalog language (the app's UI languages). */
    private fun <T> underEachLocale(block: () -> T): Map<String, T> {
        val saved = Locale.getDefault()
        try {
            return I18nTestFiles.languageCodes().associateWith { code ->
                Locale.setDefault(Locale.forLanguageTag(code))
                block()
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    private val expectedSubjectCodes = mapOf(
        ReportCategory.SIDEWALK_PARKING to "1145",
        ReportCategory.BROKEN_SIGN to "17841",
        ReportCategory.FADED_CROSSWALK to "1137",
        ReportCategory.BOLLARD_REQUEST to "1280",
    )

    @Test
    fun pinnedReportTextIsTheHebrewTheUserApproved() {
        assertEquals(reportTextSha256, sha256(reportText))
    }

    @Test
    fun createCasePayloadIsTheSameHebrewInEveryUiLanguage() {
        for (category in ReportCategory.entries) {
            val byLocale = underEachLocale { submit(category) }
            val hebrew = byLocale.getValue(I18nTestFiles.defaultCode())
            for ((code, report) in byLocale) assertEquals("$category under $code", hebrew, report)
            assertEquals(reportText, hebrew["CaseDescription"])
            assertEquals(reportTextSha256, sha256(hebrew["CaseDescription"] as String))
            assertEquals(expectedSubjectCodes.getValue(category), hebrew["CaseSubjectCode"])
            assertEquals("רחוב הנביאים", hebrew["CaseStreetName"])
            assertEquals("רחוב הנביאים 12", hebrew["CaseAddressText"])
            assertEquals(1, hebrew["Language"])
        }
    }

    /** A display label is UI only: no category label in any language is part of a report. */
    @Test
    fun noCategoryLabelInAnyLanguageReachesThePayload() {
        val catalog = I18nTestFiles.realCatalog()
        val labels = catalog.keys.filter { it.startsWith("category.") }
            .flatMap { key -> catalog.languages.map { catalog.t(it.code, key) } }
        assertTrue(labels.size >= 20)
        for (category in ReportCategory.entries) {
            val values = submit(category).values.filterIsInstance<String>()
            for (label in labels) assertFalse("$category payload carries \"$label\"", values.any { it.contains(label) })
        }
    }

    /** The approval binds the same Hebrew bytes in every UI language. */
    @Test
    fun approvalDigestIsLanguageIndependent() {
        for (category in ReportCategory.entries) {
            val digests = underEachLocale { draft(category).approvedDigest!! }
            assertEquals("$category: $digests", 1, digests.values.toSet().size)
        }
        assertEquals(
            "f2b464b922c81525e4ec8ac5a1fe3d83deb6f4fe15cd1ef261f8f3dd576f99e7",
            draft(ReportCategory.SIDEWALK_PARKING).approvedDigest,
        )
    }

    /**
     * Source tripwire: the report path never reaches the UI catalog, a display label or
     * the JVM locale, so no future edit can make the city's text follow the UI language.
     */
    @Test
    fun reportPathNeverReadsTheUiLanguage() {
        val dirs = listOf("src/main/java/il/sidewalks/reporter/municipal")
        val files = listOf(
            "src/main/java/il/sidewalks/reporter/core/ReviewDraft.kt",
            "src/main/java/il/sidewalks/reporter/core/ReportCategory.kt",
            "src/main/java/il/sidewalks/reporter/core/SendGate.kt",
            "src/main/java/il/sidewalks/reporter/ledger/DraftCodec.kt",
            "src/main/java/il/sidewalks/reporter/intake/EncryptedPackage.kt",
        ).map { I18nTestFiles.moduleFile(it) } + dirs.flatMap { dir ->
            I18nTestFiles.moduleFile(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue(files.size >= 12)
        val forbidden = listOf(
            Regex("""\bil\.sidewalks\.reporter\.i18n\b"""),
            Regex("""\bStrings\("""),
            Regex("""\bLocalStrings\b"""),
            Regex("""\bStringCatalog\b"""),
            Regex("""\bAppCatalog\b"""),
            Regex("""\bHebrewPresentation\b"""),
            Regex("""\bLocale\.getDefault\b"""),
            Regex("""\bLocaleList\b"""),
            Regex("""\.labelKey\b"""),
        )
        for (file in files) {
            val text = file.readText(Charsets.UTF_8)
            for (rule in forbidden) assertFalse("${file.name} matches ${rule.pattern}", rule.containsMatchIn(text))
        }
        val coordinator = files.single { it.name == "SubmissionCoordinator.kt" }.readText(Charsets.UTF_8)
        assertEquals(1, coordinator.split("description = draft.exactHebrewText,").size - 1)
        assertEquals(1, coordinator.split("subjectCode = category.municipalSubjectCode,").size - 1)
    }
}
