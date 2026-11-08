package il.sidewalks.reporter.municipal

/**
 * Deterministic JSON writer that is byte-identical to CPython
 * `json.dumps(value, ensure_ascii=False)` with default separators
 * (", " between items, ": " after keys).
 *
 * The municipal contract tests prove byte-level payload parity with the
 * helper by comparing this writer's output against golden fixtures the
 * helper produced; org.json and kotlinx.serialization do not match
 * Python's spacing, so they are not used for request bodies.
 *
 * Supported values: null, Boolean, Int, Long, String, Map<String, any of
 * these>, List<any of these>. Map iteration order is insertion order; use
 * LinkedHashMap for payload maps, exactly like Python dicts.
 */
object PythonJson {

    fun writeBytes(value: Any?): ByteArray = dumps(value).toByteArray(Charsets.UTF_8)

    fun dumps(value: Any?): String {
        val out = StringBuilder()
        writeValue(out, value)
        return out.toString()
    }

    private fun writeValue(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int -> out.append(value.toString())
            is Long -> out.append(value.toString())
            is String -> writeString(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, entryValue) in value) {
                    if (!first) out.append(", ")
                    first = false
                    writeString(out, key as String)
                    out.append(": ")
                    writeValue(out, entryValue)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(", ")
                    first = false
                    writeValue(out, item)
                }
                out.append(']')
            }
            else -> throw MunicipalInputException(
                "Unsupported JSON value type: " + value.javaClass.simpleName
            )
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\t' -> out.append("\\t")
                '\n' -> out.append("\\n")
                '\u000C' -> out.append("\\f")
                '\r' -> out.append("\\r")
                else ->
                    if (c < ' ') {
                        // Locale-independent, lowercase, exactly like Python's \uXXXX.
                        out.append("\\u")
                        out.append(Integer.toHexString(c.code).padStart(4, '0'))
                    } else {
                        out.append(c)
                    }
            }
        }
        out.append('"')
    }
}
