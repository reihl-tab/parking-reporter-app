package il.sidewalks.reporter.core

import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest

/**
 * Captured municipal 106 subject catalog (GetSubjectCategories), ported from
 * parking-report-helper configs/subjects.json and verified identical to the
 * live catalog on 2026-11-08. Offline only: no network, no live refresh, no
 * submission behavior. Parity with the helper is proven by recomputing the
 * same canonical fingerprint (sha256 over the newline-joined, sorted
 * json.dumps([code, name, path], ensure_ascii=False) rows).
 */
data class MunicipalSubject(val code: String, val name: String, val path: List<String>)

data class MunicipalSubjects(
    val capturedAt: String,
    val verifiedLiveAt: String,
    val fingerprintSha256: String,
    val subjects: List<MunicipalSubject>,
) {
    fun byCode(code: String): List<MunicipalSubject> = subjects.filter { it.code == code }

    fun hasCode(code: String): Boolean = subjects.any { it.code == code }

    /** Recomputed fingerprint of the loaded subjects (canonical rule). */
    fun computedFingerprint(): String = canonicalFingerprint(subjects)

    companion object {
        const val ASSET_PATH = "taxonomy/municipal_subjects.json"

        private const val MAX_SUBJECTS = 10000
        private const val MAX_PATH_DEPTH = 16

        fun load(stream: InputStream): MunicipalSubjects {
            val root = JSONObject(stream.bufferedReader(Charsets.UTF_8).readText())
            if (root.optInt("version", -1) != 1) {
                throw IllegalArgumentException("Invalid subject catalog version")
            }
            val array = root.optJSONArray("subjects")
                ?: throw IllegalArgumentException("Subject catalog missing subjects")
            if (array.length() == 0 || array.length() > MAX_SUBJECTS) {
                throw IllegalArgumentException("Subject catalog size out of bounds")
            }
            val subjects = ArrayList<MunicipalSubject>(array.length())
            for (i in 0 until array.length()) {
                subjects.add(parseSubject(array.getJSONObject(i)))
            }
            return MunicipalSubjects(
                capturedAt = root.getString("captured_at"),
                verifiedLiveAt = root.getString("verified_live_at"),
                fingerprintSha256 = root.getString("fingerprint_sha256"),
                subjects = subjects,
            )
        }

        private fun parseSubject(obj: JSONObject): MunicipalSubject {
            val code = obj.getString("code")
            val name = obj.getString("name")
            val pathArray = obj.getJSONArray("path")
            if (code.isEmpty() || !code.all { it in '0'..'9' }) {
                throw IllegalArgumentException("Invalid subject code")
            }
            if (name.isBlank() || pathArray.length() == 0 || pathArray.length() > MAX_PATH_DEPTH) {
                throw IllegalArgumentException("Invalid subject row")
            }
            val path = ArrayList<String>(pathArray.length())
            for (i in 0 until pathArray.length()) {
                path.add(pathArray.getString(i))
            }
            return MunicipalSubject(code, name, path)
        }

        /**
         * Canonical fingerprint identical to parking-report-helper
         * subjects.catalog_fingerprint: each row encoded as
         * json.dumps([code, name, path], ensure_ascii=False), rows sorted,
         * joined with "\n", sha256 hex.
         */
        fun canonicalFingerprint(subjects: List<MunicipalSubject>): String {
            val items = subjects.map { canonicalRow(it.code, it.name, it.path) }.sorted()
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(items.joinToString("\n").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        /** json.dumps([code, name, path], ensure_ascii=False) equivalent. */
        fun canonicalRow(code: String, name: String, path: List<String>): String =
            buildString {
                append('[')
                append(pythonJsonString(code))
                append(", ")
                append(pythonJsonString(name))
                append(", ")
                append(path.joinToString(", ", prefix = "[", postfix = "]") { pythonJsonString(it) })
                append(']')
            }

        /**
         * Python json.dumps string escaping with ensure_ascii=False: quotes and
         * backslashes escaped, the usual control-character shortcuts, other
         * control characters as \u00xx, all other characters (including Hebrew)
         * emitted raw.
         */
        internal fun pythonJsonString(value: String): String = buildString(value.length + 2) {
            append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch < ' ') append(String.format("\\u%04x", ch.code)) else append(ch)
                }
            }
            append('"')
        }
    }
}
