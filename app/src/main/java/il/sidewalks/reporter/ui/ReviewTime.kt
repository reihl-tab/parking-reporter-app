package il.sidewalks.reporter.ui

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** UI only. Show municipality-local Jerusalem time consistently, never the device's current zone.
 * An unchanged minute display must not rewrite seconds/offset or evidence bytes. */
object ReviewTime {
    private val format = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    fun display(value: String?): String = try {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Jerusalem")).format(format)
    } catch (_: Exception) {
        try { LocalDateTime.parse(value).format(format) } catch (_: Exception) { "" }
    }
    /** Municipality-local wall clock, explicitly named beside the confirmation button.
     * Missing/ambiguous DST instants refuse instead of silently selecting an offset. */
    fun toJerusalemIso(input: String, previous: String?): String? {
        val normalized = buildString { input.forEach { char ->
            val digit = Character.digit(char, 10)
            append(if (digit >= 0) ('0'.code + digit).toChar() else char)
        } }
        if (normalized == display(previous)) {
            if (previous?.let { validOffset(it) } == true) return previous
            try {
                val originalLocal = LocalDateTime.parse(previous)
                val offsets = ZoneId.of("Asia/Jerusalem").rules.getValidOffsets(originalLocal)
                return if (offsets.size == 1) originalLocal.atOffset(offsets.single()).toString() else null
            } catch (_: Exception) { /* Missing/invalid original: validate the edited display below. */ }
        }
        return try {
            val local = LocalDateTime.parse(normalized, format)
            val offsets = ZoneId.of("Asia/Jerusalem").rules.getValidOffsets(local)
            if (offsets.size == 1) local.atOffset(offsets.single()).toString() else null
        } catch (_: Exception) { null }
    }
    private fun validOffset(value: String) = try { OffsetDateTime.parse(value); true } catch (_: Exception) { false }
}
