package il.sidewalks.reporter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * M4 taxonomy parity: the captured municipal catalog asset parses, its
 * canonical fingerprint matches the embedded value (the same value the
 * parking-report-helper computes over its own copy), and every local
 * ReportCategory resolves to a municipal subject code present in the catalog.
 */
class MunicipalSubjectsTest {

    private fun loadCatalog(): MunicipalSubjects {
        val stream = javaClass.classLoader?.getResourceAsStream(MunicipalSubjects.ASSET_PATH)
        checkNotNull(stream) { "Catalog asset not found on test classpath: ${MunicipalSubjects.ASSET_PATH}" }
        return stream.use { MunicipalSubjects.load(it) }
    }

    @Test
    fun assetParsesWithExpectedShape() {
        val catalog = loadCatalog()
        assertEquals("2026-10-05", catalog.capturedAt)
        assertEquals("2026-11-08", catalog.verifiedLiveAt)
        assertEquals(86, catalog.subjects.size)
        assertTrue(catalog.subjects.all { it.code.isNotEmpty() && it.name.isNotBlank() && it.path.isNotEmpty() })
    }

    @Test
    fun fingerprintMatchesEmbeddedValue() {
        val catalog = loadCatalog()
        assertEquals(
            "77613b820e657c43c7df6558a5f1d24c3a4bcd1ea7d691bb4bb55db2607c66ab",
            catalog.fingerprintSha256,
        )
        assertEquals(catalog.fingerprintSha256, catalog.computedFingerprint())
    }

    @Test
    fun fingerprintIsOrderIndependent() {
        val catalog = loadCatalog()
        assertEquals(
            catalog.computedFingerprint(),
            MunicipalSubjects.canonicalFingerprint(catalog.subjects.reversed()),
        )
    }

    @Test
    fun everyLocalCategoryResolvesToMunicipalSubject() {
        val catalog = loadCatalog()
        for (category in ReportCategory.entries) {
            assertTrue(
                "Local category ${category.code} maps to missing municipal subject ${category.municipalSubjectCode}",
                catalog.hasCode(category.municipalSubjectCode),
            )
        }
    }

    @Test
    fun municipalMappingsAreTheExpectedSubjects() {
        val catalog = loadCatalog()
        assertEquals("חנייה אסורה - חנייה על מדרכה", catalog.byCode("1145").single().name)
        assertEquals("תמרור תנועה נפל / דהוי / שבור / נוטה ליפול", catalog.byCode("17841").single().name)
        assertEquals("חידוש סימון מעבר חצייה", catalog.byCode("1137").single().name)
    }

    @Test
    fun bollardUsesEstablishedSpecificSubjectWithExplicitCatalogContext() {
        val catalog = loadCatalog()
        val category = ReportCategory.BOLLARD_REQUEST
        assertEquals("1280", category.municipalSubjectCode)
        val subject = catalog.byCode(category.municipalSubjectCode).single()
        assertEquals("עמוד מחסום ברזל נוטה ליפול / נפל", subject.name)
        assertEquals(listOf("גדרות, מעקות, עמודים", subject.name), subject.path)
        // Historical installation requests used this specific bollard channel.
        // The description states installation, not a claim that a post fell.
        assertNotEquals("5164", category.municipalSubjectCode)
    }

    @Test
    fun repeatedSubjectCodesKeepEveryPath() {
        val catalog = loadCatalog()
        // 5164 אחר appears under many roots in the captured catalog.
        assertTrue(catalog.byCode("5164").size > 1)
    }

    @Test
    fun tamperingChangesFingerprint() {
        val catalog = loadCatalog()
        val tampered = catalog.subjects.map {
            if (it.code == "1145") it.copy(name = it.name + "x") else it
        }
        assertNotEquals(
            catalog.computedFingerprint(),
            MunicipalSubjects.canonicalFingerprint(tampered),
        )
    }

    @Test
    fun canonicalEncoderMatchesPythonJsonDumps() {
        // Golden vectors computed with Python json.dumps([code, name, path], ensure_ascii=False).
        val hebrewRow = MunicipalSubjects.canonicalRow(
            "1145",
            "חנייה אסורה - חנייה על מדרכה",
            listOf("חניה אסורה", "בקשה לאכיפת חניה", "חנייה אסורה - חנייה על מדרכה"),
        )
        assertEquals(
            """["1145", "חנייה אסורה - חנייה על מדרכה", ["חניה אסורה", "בקשה לאכיפת חניה", "חנייה אסורה - חנייה על מדרכה"]]""",
            hebrewRow,
        )
        // Backslash escaping (catalog row 1217 contains literal backslashes).
        val backslashRow = MunicipalSubjects.canonicalRow(
            "1217",
            "מכשולים שונים- גושי בטון\\ מלט בכביש\\מדרכה",
            listOf("כבישים מדרכות", "כביש משובש", "מכשולים שונים- גושי בטון\\ מלט בכביש\\מדרכה"),
        )
        assertTrue(backslashRow.contains("\\\\"))
        assertEquals(
            "5020d684d7816405f4361e6e21bdbd87dbe015ce9ee28a5b2ef784f2bfff1b18",
            sha256Hex(backslashRow),
        )
    }

    @Test
    fun loadRejectsMalformedCatalog() {
        val bad = JSONObjectBytes("""{"version":2,"subjects":[]}""")
        try {
            MunicipalSubjects.load(bad)
            fail("Expected rejection of version 2")
        } catch (_: IllegalArgumentException) {
        }
        try {
            MunicipalSubjects.load(JSONObjectBytes("""{"version":1}"""))
            fail("Expected rejection of missing subjects")
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun JSONObjectBytes(text: String) = text.byteInputStream(Charsets.UTF_8)

    private fun sha256Hex(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
