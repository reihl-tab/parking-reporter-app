package il.sidewalks.reporter.municipal

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validator and builder tests for the api106 port. Byte-parity of request
 * bodies against the helper's golden fixtures is proven here (payload maps)
 * and in MunicipalClientContractTest (full requests through mockwebserver).
 * All values are synthetic.
 */
class MunicipalPayloadsTest {

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader.getResource("municipal/$name")).readBytes()

    private val reporter = MunicipalPayloads.Reporter(
        firstName = "ישראל",
        lastName = "ישראלי",
        phone = "+972501234567",
        nationalId = "000000018",
        email = "synthetic@example.com",
    )

    private val reportFields = MunicipalPayloads.ReportFields(
        description = "דיווח סינתטי לבדיקת התאמת מטען בלבד.",
        subjectCode = "1145",
        streetCode = "1630",
        streetName = "הפלמח",
        houseNumber = "900",
        coordinateX = 220000,
        coordinateY = 630500,
    )

    @Test
    fun contactBodyMatchesGoldenBytes() {
        val body = MunicipalPayloads.buildContact(reporter)
        body["Token"] = "synthetic-token"
        body["SignUpByApp"] = true
        assertArrayEquals(resource("golden-contact.json"), PythonJson.writeBytes(body))
    }

    @Test
    fun createCaseBodyMatchesGoldenBytes() {
        val contact = MunicipalPayloads.buildContact(reporter)
        contact["Token"] = "synthetic-token"
        contact["ContactIdInCRM"] = "synthetic-contact-id"
        val body = MunicipalPayloads.buildReport(reportFields)
        body["Token"] = "synthetic-token"
        body["contact"] = contact
        assertArrayEquals(resource("golden-createcase.json"), PythonJson.writeBytes(body))
    }

    @Test
    fun loginBodyMatchesGoldenBytes() {
        val body = LinkedHashMap<String, Any?>()
        body["UserName"] = "synthetic-user"
        body["Password"] = "synthetic-password"
        assertArrayEquals(resource("golden-login.json"), PythonJson.writeBytes(body))
    }

    @Test
    fun attachBodyMatchesGoldenBytes() {
        val body = LinkedHashMap<String, Any?>()
        body["token"] = "synthetic-token"
        body["caseIdInCRM"] = "synthetic-case-id"
        body["webCaseIdInCRM"] = "synthetic-webcase-id"
        body["fileName"] = "synthetic.jpg"
        body["fileContent"] =
            java.util.Base64.getEncoder().encodeToString("synthetic-jpeg-bytes-not-a-photo".toByteArray())
        assertArrayEquals(resource("golden-attach.json"), PythonJson.writeBytes(body))
    }

    @Test
    fun contactFieldOrderMatchesHelper() {
        assertEquals(
            listOf(
                "ContactIdInCRM", "ContactIdType", "ContactIdNumber", "FirstName", "LastName",
                "MobilePhoneNumber", "Email", "CityCode", "CityName", "StreetCode", "StreetName",
                "HouseNumber", "ApartmentNumber", "SecondPhoneNumber", "Postbox", "ZIPCODE",
                "AllowSendMarketingMaterial",
            ),
            MunicipalPayloads.buildContact(reporter).keys.toList(),
        )
    }

    @Test
    fun nationalIdValidation() {
        assertEquals("000000018", MunicipalPayloads.normalizeNationalId("18"))
        assertEquals("000000018", MunicipalPayloads.normalizeNationalId(" 18 "))
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.normalizeNationalId("19")
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.normalizeNationalId("0")
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.normalizeNationalId("1234567890")
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.normalizeNationalId("18a")
        }
    }

    @Test
    fun phoneValidation() {
        assertEquals("0501234567", MunicipalPayloads.normalizePhone("+972501234567"))
        assertEquals("0501234567", MunicipalPayloads.normalizePhone("050-123-4567"))
        assertEquals("0501234567", MunicipalPayloads.normalizePhone(" 050 123 4567 "))
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.normalizePhone("021234567")
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.normalizePhone("050123456")
        }
    }

    @Test
    fun emailValidation() {
        assertEquals("a@b.co", MunicipalPayloads.checkEmail(" a@b.co "))
        assertThrows(MunicipalInputException::class.java) { MunicipalPayloads.checkEmail("a@b") }
        assertThrows(MunicipalInputException::class.java) { MunicipalPayloads.checkEmail("a b@c.d") }
    }

    @Test
    fun checkTextRejectsBlankAndMojibake() {
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.checkText("   ", "Report text")
        }
        // UTF-8 bytes of Hebrew decoded as Latin-1: classic mojibake the helper refuses.
        val mojibake = String("שלום".toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.checkText(mojibake, "Report text")
        }
        assertEquals("שלום", MunicipalPayloads.checkText(" שלום ", "Report text"))
    }

    @Test
    fun normalizeFoldsFinalsAndStripsNonAlnum() {
        assertEquals("אבכ", MunicipalPayloads.normalize("אב ך!"))
        assertEquals("abc123", MunicipalPayloads.normalize("a-b c:123"))
    }

    @Test
    fun reportValidation() {
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.buildReport(reportFields.copy(description = " "))
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.buildReport(reportFields.copy(subjectCode = "abc"))
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.buildReport(reportFields.copy(houseNumber = "a".repeat(17)))
        }
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.buildReport(reportFields.copy(houseNumber = "/3"))
        }
        // Hebrew-letter house numbers pass (parity with the helper's Unicode \w),
        // but the number must still lead with ASCII digits: Hebrew-first is refused.
        val hebrew = MunicipalPayloads.buildReport(reportFields.copy(houseNumber = "12א"))
        assertEquals("הפלמח 12א", hebrew["CaseAddressText"])
        assertEquals("12א", hebrew["CaseHouseNumber"])
        assertThrows(MunicipalInputException::class.java) {
            MunicipalPayloads.buildReport(reportFields.copy(houseNumber = "א12"))
        }
        val report = MunicipalPayloads.buildReport(reportFields)
        assertEquals("הפלמח 900", report["CaseAddressText"])
        assertEquals(1, report["Language"])
        assertEquals("ApplicationCaseNumber_VAL", report["ApplicationCaseNumber"])
        assertTrue(report.keys.first() == "CaseDescription")
    }

    @Test
    fun pythonJsonEscapesLikePython() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\"", PythonJson.dumps("a\"b\\c\n\t"))
        assertEquals("\"\\u0001\"", PythonJson.dumps("\u0001"))
        // DEL and non-ASCII pass through unescaped, like ensure_ascii=False.
        assertEquals("\"א\u007f\"", PythonJson.dumps("א\u007f"))
        assertEquals("[1, true, null, \"x\"]", PythonJson.dumps(listOf(1, true, null, "x")))
        assertEquals("{\"a\": {\"b\": 2}}", PythonJson.dumps(linkedMapOf("a" to linkedMapOf("b" to 2))))
    }

    @Test
    fun approvedDescriptionIsValidatedTrimmedButSentVerbatim() {
        // Helper parity (api_cli.py "Preserve exact reviewed whitespace too"):
        // surrounding whitespace passes validation yet stays in the payload.
        val padded = reportFields.copy(description = "  דיווח מאושר.  ")
        val report = MunicipalPayloads.buildReport(padded)
        assertEquals("  דיווח מאושר.  ", report["CaseDescription"])
    }
}
