package il.sidewalks.reporter.municipal

import java.text.Normalizer

/**
 * Validation and payload builders ported from parking-report-helper api106.py
 * (helper 0.6.0, pinned 51dd5c02916b1ebbe62fdd2390132f9f58a39eb8). The helper
 * is the de-facto municipal API spec; payload field order matches its dicts
 * exactly so request bodies are byte-identical (see PythonJson).
 *
 * Payloads are LinkedHashMap<String, Any?> with values String / Int / Boolean /
 * nested maps, in the helper's insertion order.
 */
object MunicipalPayloads {

    /** Hebrew final letters have no NFKC mapping to their regular forms (#137 in the helper). */
    private val HEBREW_FINALS = mapOf(
        'ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ',
    )

    /** NFKC + Hebrew finals fold + letters/digits only, like the helper's normalize(). */
    fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val out = StringBuilder()
        for (c in folded) {
            val mapped = HEBREW_FINALS[c] ?: c
            if (mapped.isLetterOrDigit()) out.append(mapped)
        }
        return out.toString()
    }

    /**
     * Trims and rejects empty text and mojibake (UTF-8 bytes decoded as Latin-1),
     * like the helper's check_text().
     */
    fun checkText(value: String, label: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) throw MunicipalInputException("$label must not be empty.")
        // Python: value.encode("latin-1").decode("utf-8") - both steps strict;
        // any failure keeps the original value.
        val restored: String = try {
            val encoder = Charsets.ISO_8859_1.newEncoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            val bytes = encoder.encode(java.nio.CharBuffer.wrap(trimmed))
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(bytes).toString()
        } catch (e: Exception) {
            trimmed
        }
        if (restored != trimmed && restored.any { it in 'א'..'ת' }) {
            throw MunicipalInputException(
                "$label contains corrupted Hebrew encoding. Supply the original Unicode text."
            )
        }
        return trimmed
    }

    /** Israeli national ID: 1-9 digits, zero-padded to 9, with the helper's checksum. */
    fun normalizeNationalId(raw: String): String {
        val digits = raw.trim()
        if (!Regex("[0-9]{1,9}").matches(digits)) {
            throw MunicipalInputException("National ID must contain one to nine digits.")
        }
        val number = digits.padStart(9, '0')
        var sum = 0
        for (i in number.indices) {
            var product = (number[i] - '0') * (1 + i % 2)
            if (product >= 10) product -= 9
            sum += product
        }
        if (number == "000000000" || sum % 10 != 0) {
            throw MunicipalInputException("National ID checksum is invalid.")
        }
        return number
    }

    /** Israeli mobile: strips separators, maps +972 to 0, requires 05XXXXXXXX. */
    fun normalizePhone(raw: String): String {
        var phone = raw.replace(Regex("[\\s()\\-]"), "")
        if (phone.startsWith("+972")) phone = "0" + phone.substring(4)
        if (!Regex("05[0-9]{8}").matches(phone)) {
            throw MunicipalInputException(
                "Supply an Israeli mobile number, such as 05XXXXXXXX or +9725XXXXXXXX."
            )
        }
        return phone
    }

    fun checkEmail(raw: String): String {
        val email = raw.trim()
        if (!Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(email)) {
            throw MunicipalInputException("Email address syntax is invalid.")
        }
        return email
    }

    data class Reporter(
        val firstName: String,
        val lastName: String,
        val phone: String,
        val nationalId: String,
        val email: String,
    )

    /** The municipal contact payload, fields in the helper's exact order. */
    fun buildContact(reporter: Reporter): LinkedHashMap<String, Any?> {
        val contact = LinkedHashMap<String, Any?>()
        contact["ContactIdInCRM"] = ""
        contact["ContactIdType"] = "100000000"
        contact["ContactIdNumber"] = normalizeNationalId(reporter.nationalId)
        contact["FirstName"] = checkText(reporter.firstName, "First name")
        contact["LastName"] = checkText(reporter.lastName, "Last name")
        contact["MobilePhoneNumber"] = normalizePhone(reporter.phone)
        contact["Email"] = checkEmail(reporter.email)
        contact["CityCode"] = "3000"
        contact["CityName"] = "ירושלים"
        contact["StreetCode"] = ""
        contact["StreetName"] = ""
        contact["HouseNumber"] = ""
        contact["ApartmentNumber"] = ""
        contact["SecondPhoneNumber"] = ""
        contact["Postbox"] = ""
        contact["ZIPCODE"] = ""
        contact["AllowSendMarketingMaterial"] = false
        return contact
    }

    data class ReportFields(
        val description: String,
        val subjectCode: String,
        val streetCode: String,
        val streetName: String,
        val houseNumber: String,
        val coordinateX: Int,
        val coordinateY: Int,
    )

    /** The CreateCase report payload, fields in the helper's exact order. */
    fun buildReport(fields: ReportFields): LinkedHashMap<String, Any?> {
        // Parity with the helper's api_cli.py: the approved description is
        // VALIDATED trimmed (check_text strips) but SENT verbatim - the
        // helper preserves the exact reviewed whitespace on purpose.
        checkText(fields.description, "Report text")
        val description = fields.description
        if (!Regex("[0-9]+").matches(fields.subjectCode)) {
            throw MunicipalInputException("Municipal subject code must be numeric.")
        }
        if (!Regex("[0-9]+").matches(fields.streetCode)) {
            throw MunicipalInputException("Municipal street code must be numeric.")
        }
        if (!Regex("[0-9]+[\\w /\\-]*").matches(fields.houseNumber) || fields.houseNumber.length > 16) {
            throw MunicipalInputException("Invalid house number.")
        }
        val report = LinkedHashMap<String, Any?>()
        report["CaseDescription"] = description
        report["CaseSubjectCode"] = fields.subjectCode
        report["CaseStreetCode"] = fields.streetCode
        report["CaseStreetName"] = fields.streetName
        report["CaseHouseNumber"] = fields.houseNumber
        report["CaseAddressText"] = fields.streetName + " " + fields.houseNumber
        report["CoordinateX"] = fields.coordinateX
        report["CoordinateY"] = fields.coordinateY
        report["Language"] = 1
        report["ApplicationCaseNumber"] = "ApplicationCaseNumber_VAL"
        return report
    }
}
