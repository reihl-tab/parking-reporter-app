package il.sidewalks.reporter.ui

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** UI only. Keep the recorded local clock and offset, never the device's current zone.
 * An unchanged minute display must not rewrite seconds/offset or evidence bytes. */
object ReviewTime {
    private val format = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    fun display(value: String?): String = try {
        OffsetDateTime.parse(value).format(format)
    } catch (_: Exception) {
        try { LocalDateTime.parse(value).format(format) } catch (_: Exception) { "" }
    }
    fun offset(value: String?): String = try { OffsetDateTime.parse(value).offset.id } catch (_: Exception) { "" }
    fun toIso(input: String, previous: String?, offsetText: String = offset(previous)): String? {
        if (input == display(previous) && offsetText == offset(previous)) return previous?.takeIf { validOffset(it) }
        return try {
            val local = LocalDateTime.parse(input, format)
            val offset = ZoneOffset.of(offsetText)
            local.atOffset(offset).toString()
        } catch (_: Exception) { null }
    }
    private fun validOffset(value: String) = try { OffsetDateTime.parse(value); true } catch (_: Exception) { false }
}
