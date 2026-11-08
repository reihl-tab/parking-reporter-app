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

    /**
     * Minimal strict JSON reader: the inverse of [dumps] for the value shapes
     * submission states carry (objects, arrays, strings, numbers, booleans,
     * null). Hand-rolled so JVM unit tests need no Android framework; rejects
     * trailing data, duplicate-ish weirdness stays last-wins like json.loads.
     */
    fun loads(text: String): Any? {
        val parser = Reader(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw MunicipalInputException("Trailing data in JSON.")
        return value
    }

    private class Reader(private val text: String) {
        private var pos = 0
        fun atEnd(): Boolean = pos >= text.length
        fun skipWhitespace() { while (pos < text.length && text[pos].isWhitespace()) pos++ }
        private fun peek(): Char = if (atEnd()) throw MunicipalInputException("Unexpected end of JSON.") else text[pos]
        private fun expect(c: Char) {
            if (atEnd() || text[pos] != c) throw MunicipalInputException("Malformed JSON near offset $pos.")
            pos++
        }

        fun readValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): MutableMap<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return out }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace(); expect(':')
                out[key] = readValue()
                skipWhitespace()
                if (peek() == ',') { pos++; continue }
                expect('}')
                return out
            }
        }

        private fun readArray(): MutableList<Any?> {
            expect('[')
            val out = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return out }
            while (true) {
                out.add(readValue())
                skipWhitespace()
                if (peek() == ',') { pos++; continue }
                expect(']')
                return out
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw MunicipalInputException("Unterminated string in JSON.")
                when (val c = text[pos++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw MunicipalInputException("Bad escape in JSON.")
                        when (val e = text[pos++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw MunicipalInputException("Bad unicode escape in JSON.")
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                val code = try { hex.toInt(16) } catch (_: NumberFormatException) {
                                    throw MunicipalInputException("Bad unicode escape in JSON.")
                                }
                                out.append(code.toChar())
                            }
                            else -> throw MunicipalInputException("Bad escape in JSON.")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) throw MunicipalInputException("Malformed JSON near offset $pos.")
            pos += word.length
            return value
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && text[pos].isDigit()) pos++
            val integerEnd = pos
            if (!atEnd() && text[pos] == '.') {
                pos++
                while (!atEnd() && text[pos].isDigit()) pos++
            }
            if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                while (!atEnd() && text[pos].isDigit()) pos++
            }
            val raw = text.substring(start, pos)
            if (raw.isEmpty() || raw == "-") throw MunicipalInputException("Malformed number in JSON.")
            return if (pos == integerEnd) {
                try { raw.toLong() } catch (_: NumberFormatException) {
                    throw MunicipalInputException("Integer out of range in JSON.")
                }
            } else {
                try { raw.toDouble() } catch (_: NumberFormatException) {
                    throw MunicipalInputException("Malformed number in JSON.")
                }
            }
        }
    }
}
