package il.sidewalks.reporter.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*

/** Read-only grouping while unfocused. On focus the raw canonical string is editable. */
object PhoneDisplayTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = ReporterInput.formattedPhone(text.text)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                var seen = 0
                formatted.forEachIndexed { i, c -> if (c.isDigit() || c == '+') {
                    seen++; if (seen == offset) return i + 1
                } }
                return formatted.length
            }
            override fun transformedToOriginal(offset: Int) = formatted.take(offset).count { it.isDigit() || it == '+' }.coerceIn(0, text.length)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
