package il.sidewalks.reporter.ui

import il.sidewalks.reporter.municipal.MunicipalPayloads

object ReporterInput {
    fun digits(value: String): String = buildString { value.forEach { c ->
        if (Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER.toInt()) {
            val digit = Character.digit(c, 10)
            if (digit in 0..9) append(('0'.code + digit).toChar())
        }
    } }
    fun phoneInput(value: String) = (if (value.startsWith("+")) "+" else "") + digits(value)
    fun canonicalPhone(value: String): String {
        val clean = phoneInput(value)
        return if (clean.matches(Regex("05[0-9]{8}"))) "+972" + clean.drop(1) else clean
    }
    fun nameInput(value: String) = value.filterNot { Character.isDigit(it) }
    fun validEmail(value: String): Boolean {
        val email = value.trim()
        if (email.length > 254 || email.count { it == '@' } != 1) return false
        val local = email.substringBefore('@')
        val domain = email.substringAfter('@')
        if (local.isEmpty() || local.length > 64 || local.startsWith('.') || local.endsWith('.') || local.contains("..")) return false
        if (!local.all { it.isLetterOrDigit() || it in ".!#$%&'*+/=?^_`{|}~-" }) return false
        val labels = domain.split('.')
        return labels.size >= 2 && labels.all { label ->
            label.isNotEmpty() && label.length <= 63 && !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        } && !labels.last().all { it.isDigit() }
    }
    fun isIsraeliMobile(value: String) = canonicalPhone(value).matches(Regex("\\+9725[0-9]{8}"))
    fun validId(value: String): Boolean = try { MunicipalPayloads.normalizeNationalId(value); true } catch (_: Exception) { false }
    fun validPhone(value: String): Boolean = canonicalPhone(value).matches(Regex("\\+[1-9][0-9]{6,14}"))
    fun formattedPhone(value: String): String {
        val canonical = canonicalPhone(value)
        if (canonical.matches(Regex("\\+9725[0-9]{8}")))
            return "+972-" + canonical.substring(4, 6) + "-" + canonical.substring(6, 9) + "-" + canonical.substring(9)
        return android.telephony.PhoneNumberUtils.formatNumber(canonical, "IL")?.replace(' ', '-') ?: canonical
    }
}
