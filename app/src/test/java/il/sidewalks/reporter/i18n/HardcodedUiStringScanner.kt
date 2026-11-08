package il.sidewalks.reporter.i18n

/**
 * Source scanner behind [HardcodedUiStringLintTest] (issue #115).
 *
 * A small Kotlin lexer: it skips comments and char literals, reads string and raw
 * string literals (including `${...}` templates, whose nested literals are scanned
 * too) and reports a literal when either
 *  - it sits in a user-visible position: the first argument of `Text(`, or the value
 *    of a `label =`, `placeholder =`, `contentDescription =`, `title =` or `text =`
 *    argument, and it is not a technical string; or
 *  - it contains Hebrew, Arabic, Cyrillic or Ethiopic script, wherever it is.
 */
object HardcodedUiStringScanner {

    data class Finding(val file: String, val line: Int, val literal: String, val reason: String) {
        override fun toString(): String = "$file:$line: \"$literal\" ($reason)"
    }

    const val REASON_UI = "literal in a user-visible position"
    const val REASON_SCRIPT = "literal in Hebrew/Arabic/Cyrillic/Ethiopic script"

    private const val TEMPLATE = '\u0000'

    private val UI_CONTEXT =
        Regex("""(?:\bText\s*\(|\b(?:label|placeholder|contentDescription|title|text)\s*=)\s*$""")
    private val LOG_CONTEXT = Regex("""(?:\bLog\s*\.\s*(?:v|d|i|w|e|wtf)\s*\(|\bTAG\s*(?::\s*String\s*)?=)\s*$""")

    /** Hebrew, Arabic (incl. supplements and presentation forms), Cyrillic, Ethiopic. */
    private val SCRIPT = Regex(
        "[\u0590-\u05FF\uFB1D-\uFB4F" +
            "\u0600-\u06FF\u0750-\u077F\u0870-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF" +
            "\u0400-\u052F\u1C80-\u1C8F\u2DE0-\u2DFF\uA640-\uA69F" +
            "\u1200-\u139F\u2D80-\u2DDF\uAB00-\uAB2F]",
    )
    private val MIME = Regex("""^[a-z]+/[a-z0-9.+*-]+$""")
    private val IDENTIFIER_LIKE = Regex("""^[a-z0-9_]+(?:[./:#{}-][a-z0-9_{}]*)*$""")

    /** Technical strings: no letters, mime types, lowercase keys/routes without spaces. */
    fun isTechnical(staticText: String): Boolean {
        val text = staticText.replace(TEMPLATE.toString(), "").trim()
        if (text.none { it.isLetter() }) return true
        return MIME.matches(text) || IDENTIFIER_LIKE.matches(text)
    }

    fun scan(file: String, source: String): List<Finding> {
        val lexer = Lexer(file, source)
        lexer.code(untilBrace = false)
        return lexer.findings
    }

    private class Lexer(val file: String, val src: String) {
        val findings = mutableListOf<Finding>()
        var pos = 0
        var line = 1
        private val context = StringBuilder()

        private fun peek(offset: Int = 0): Char = if (pos + offset < src.length) src[pos + offset] else '\u0000'
        private fun startsWith(s: String) = src.startsWith(s, pos)

        private fun advance(): Char {
            val c = src[pos++]
            if (c == '\n') line++
            return c
        }

        private fun pushContext(s: CharSequence) {
            context.append(s)
            if (context.length > 400) context.delete(0, context.length - 200)
        }

        /** Lexes code; with [untilBrace] it returns after the `}` closing a template expression. */
        fun code(untilBrace: Boolean) {
            var depth = 0
            while (pos < src.length) {
                when {
                    startsWith("//") -> {
                        while (pos < src.length && peek() != '\n') advance()
                        pushContext(" ")
                    }
                    startsWith("/*") -> {
                        skipBlockComment()
                        pushContext(" ")
                    }
                    startsWith("\"\"\"") -> literal(raw = true)
                    peek() == '"' -> literal(raw = false)
                    peek() == '\'' -> charLiteral()
                    peek() == '{' -> {
                        depth++
                        pushContext(advance().toString())
                    }
                    peek() == '}' -> {
                        if (untilBrace && depth == 0) {
                            advance()
                            return
                        }
                        depth--
                        pushContext(advance().toString())
                    }
                    else -> pushContext(advance().toString())
                }
            }
        }

        private fun skipBlockComment() {
            // Kotlin block comments nest.
            var nesting = 0
            while (pos < src.length) {
                if (startsWith("/*")) {
                    nesting++
                    advance(); advance()
                } else if (startsWith("*/")) {
                    nesting--
                    advance(); advance()
                    if (nesting == 0) return
                } else {
                    advance()
                }
            }
        }

        private fun charLiteral() {
            advance() // opening '
            while (pos < src.length) {
                val c = advance()
                if (c == '\\' && pos < src.length) {
                    advance()
                } else if (c == '\'' || c == '\n') {
                    break
                }
            }
            pushContext("' '")
        }

        private fun literal(raw: Boolean) {
            val startLine = line
            val before = context.toString()
            val text = StringBuilder()
            if (raw) repeat(3) { advance() } else advance()
            while (pos < src.length) {
                if (raw && startsWith("\"\"\"")) {
                    repeat(3) { advance() }
                    // A raw string may end with extra quotes: """a""""
                    while (peek() == '"') text.append(advance())
                    break
                }
                val c = peek()
                if (!raw && c == '"') {
                    advance()
                    break
                }
                if (!raw && c == '\n') break // unterminated; stop at end of line
                if (!raw && c == '\\') {
                    advance()
                    if (pos < src.length) text.append(advance())
                    continue
                }
                if (c == '$' && peek(1) == '{') {
                    advance(); advance()
                    // The template expression is code with its own context.
                    val saved = context.toString()
                    context.setLength(0)
                    code(untilBrace = true)
                    context.setLength(0)
                    context.append(saved)
                    text.append(TEMPLATE)
                    continue
                }
                if (c == '$' && (peek(1).isLetter() || peek(1) == '_')) {
                    advance()
                    while (pos < src.length && (peek().isLetterOrDigit() || peek() == '_')) advance()
                    text.append(TEMPLATE)
                    continue
                }
                text.append(advance())
            }
            report(startLine, before, text.toString())
            pushContext("\"_\"")
        }

        private fun report(lineNo: Int, before: String, text: String) {
            if (LOG_CONTEXT.containsMatchIn(before)) return
            val shown = text.replace(TEMPLATE.toString(), "\${…}")
            when {
                SCRIPT.containsMatchIn(text) -> findings += Finding(file, lineNo, shown, REASON_SCRIPT)
                UI_CONTEXT.containsMatchIn(before) && !isTechnical(text) ->
                    findings += Finding(file, lineNo, shown, REASON_UI)
            }
        }
    }
}
