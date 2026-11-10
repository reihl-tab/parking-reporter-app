package il.sidewalks.reporter.municipal

import il.sidewalks.reporter.security.ReporterSettings

/** Explicit owner-approved compatibility mode, not a verified municipal passport contract. */
object PassportSubmission {
    // TODO: remove once a verified municipal passport contact type is supported.
    const val COMPATIBILITY_ID = "123456782"
    // User-chosen: user states the 057 prefix is unassigned; unverified by us.
    // TODO remove with native international support.
    const val INTERNATIONAL_PHONE_PLACEHOLDER = "0571234567"
    fun isIsraeliPhone(phone: String): Boolean = try {
        MunicipalPayloads.normalizePhone(phone).matches(Regex("05[0-9]{8}"))
    } catch (_: Exception) { false }
    private val disclosurePrefixes = listOf("הדיווח נשלח על ידי בעל/ת דרכון שמספרו ", "מספר הטלפון של המדווח/ת ליצירת קשר הוא ")
    private fun isDisclosureLine(line: String): Boolean = disclosurePrefixes.any { prefix -> line.dropWhile { it.isWhitespace() || Character.getType(it) == Character.FORMAT.toInt() }.startsWith(prefix) }
    fun reconcileDisclosure(text: String, settings: ReporterSettings): String {
        require(validProfile(settings))
        val base = text.lines().filterNot(::isDisclosureLine).joinToString("\n").trimEnd()
        val current = disclosure(settings)
        return if (current.isEmpty()) base else base + "\n\n" + current
    }
    fun isSupportedPhone(phone: String): Boolean = try {
        val normalized = MunicipalPayloads.normalizePhone(phone)
        isIsraeliPhone(normalized) || (normalized.matches(Regex("\\+[1-9][0-9]{7,14}")) && !normalized.startsWith("+972"))
    } catch (_: Exception) { false }
    fun validProfile(settings: ReporterSettings): Boolean =
        (!settings.passportMode || validPassport(settings.passportNumber)) &&
        isSupportedPhone(settings.phone)
    // Owner ruling: phone compatibility applies independently of passport mode.
    // Foreign contact numbers use the shared phone placeholder only after
    // exact report disclosure approval; national-ID reporters keep their actual ID.
    fun phoneForPayload(settings: ReporterSettings): String {
        require(isSupportedPhone(settings.phone))
        return if (isIsraeliPhone(settings.phone)) MunicipalPayloads.normalizePhone(settings.phone) else INTERNATIONAL_PHONE_PLACEHOLDER
    }
    fun validPassport(number: String): Boolean = number.trim().matches(Regex("[\\p{L}\\p{N} -]{3,40}")) && number.trim().last().isLetterOrDigit()
    fun disclosure(settings: ReporterSettings): String {
        require(validProfile(settings))
        val passport = settings.passportNumber.trim()
        val suffix = passport.takeLast(minOf(3, (passport.length - 1).coerceAtLeast(0)))
        val passportLine = if (settings.passportMode) "הדיווח נשלח על ידי בעל/ת דרכון שמספרו xxxxx" + suffix +
            "; המדווח/ת אינו/ה מחזיק/ה בתעודת זהות ישראלית. בשדה תעודת הזהות בממשק נשלח מספר מציין מקום " + COMPATIBILITY_ID + ", ולא מספר תעודת זהות של המדווח/ת." else ""
        val phoneLine = if (!isIsraeliPhone(settings.phone)) "מספר הטלפון של המדווח/ת ליצירת קשר הוא " + MunicipalPayloads.normalizePhone(settings.phone) +
            ". בשדה הטלפון בממשק נשלח מספר מציין מקום " + INTERNATIONAL_PHONE_PLACEHOLDER + ", ולא מספר הטלפון של המדווח/ת." else ""
        return listOf(passportLine, phoneLine).filter { it.isNotEmpty() }.joinToString("\n")
    }
    fun hasReviewedDisclosure(text: String, settings: ReporterSettings): Boolean =
        validProfile(settings) && run {
            val expected = disclosure(settings).lines().filter { it.isNotEmpty() }
            val actual = text.lines().filter(::isDisclosureLine)
            actual.size == expected.size && actual.toSet() == expected.toSet()
        }
}
