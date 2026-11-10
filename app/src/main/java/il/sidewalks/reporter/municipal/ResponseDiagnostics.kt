package il.sidewalks.reporter.municipal

import org.json.JSONArray
import org.json.JSONObject

/** Bounded diagnostic data only; never a request, credential, token or cookie. */
data class ResponseDiagnostic(val endpoint: String, val status: Int?, val headers: Map<String, String>, val body: String, val failure: String?)

object ResponseDiagnostics {
    const val MAX_BODY_BYTES = 65_536
    private val sensitive = Regex("(?i)(token|password|passwd|secret|authorization|cookie|subscription|api.?key|username|credential|auth|sid|jwt|otp|session|pwd|private.?key|bearer)")
    fun redactBody(raw: String, secrets: List<String>): String {
        val foundSecrets = secrets.toMutableList()
        fun rememberLeaves(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { rememberLeaves(value.opt(it)) }
                is JSONArray -> for (i in 0 until value.length()) rememberLeaves(value.opt(i))
                is String -> if (value.isNotEmpty()) foundSecrets += value
                is Number, is Boolean -> foundSecrets += value.toString()
            }
        }
        fun collect(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { key ->
                    val child = value.opt(key)
                    if (sensitive.containsMatchIn(key)) rememberLeaves(child)
                    else collect(child)
                }
                is JSONArray -> for (i in 0 until value.length()) collect(value.opt(i))
            }
        }
        fun scrub(value: Any?): Any = when (value) {
            is JSONObject -> JSONObject().also { out -> value.keys().forEach { key ->
                out.put(key, if (sensitive.containsMatchIn(key)) "[REDACTED]" else scrub(value.opt(key)))
            } }
            is JSONArray -> JSONArray().also { out -> for (i in 0 until value.length()) out.put(scrub(value.opt(i))) }
            is String -> foundSecrets.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }.fold(value) { text, secret -> text.replace(secret, "[REDACTED]") }
            null -> JSONObject.NULL
            else -> if (value.toString() in foundSecrets) "[REDACTED]" else value
        }
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) return "[body omitted: size limit]"
        return try { val parsed = JSONObject(raw); collect(parsed); scrub(parsed).toString() } catch (_: Exception) { "[body omitted: non-JSON]" }
    }
}
