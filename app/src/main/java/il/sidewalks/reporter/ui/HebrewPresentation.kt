package il.sidewalks.reporter.ui

import il.sidewalks.reporter.i18n.Strings
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display texts for evidence times and categories. Issue #115 PR-2c: the words come from
 * the catalog in the active language of [Strings] when shown; the Hebrew values are the
 * old literals byte for byte. Display only: nothing here feeds a municipal report.
 */
object HebrewPresentation {
    fun evidenceTime(strings: Strings, value: String?): String = try {
        val date = OffsetDateTime.parse(value)
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)) +
            " (UTC" + (if (date.offset.totalSeconds == 0) "+00:00" else date.offset.id) + ")"
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT)) +
                " (" + strings.t("presentation.time.unknown_zone") + ")"
        } catch (_: Exception) { value?.takeIf { it.isNotBlank() } ?: strings.t("presentation.time.missing") }
    }

    fun category(strings: Strings, code: String): String =
        strings.t(il.sidewalks.reporter.core.ReportCategory.from(code)?.labelKey ?: "category.needs_correction")
}
