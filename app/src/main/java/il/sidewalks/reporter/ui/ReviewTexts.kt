package il.sidewalks.reporter.ui

import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.i18n.Bidi
import il.sidewalks.reporter.i18n.StringCatalog
import il.sidewalks.reporter.i18n.Strings
import il.sidewalks.reporter.i18n.TextDir

/**
 * The review questions a draft carries in `ReviewDraft.unresolved` (issue #115 PR-2b).
 *
 * A draft stores the stable [code], and the text is looked up in the active language
 * only when it is shown, so an open draft follows a language switch. Drafts saved before
 * PR-2b hold the text itself: the Hebrew literal, or since PR-2a one of AppShell's four
 * texts in the language that was active when the draft was made. [fromStored] maps such
 * a text back to its warning through the catalog's values for [key] in every language
 * (the Hebrew values are the old literals byte for byte); any other stored text is shown
 * as it is.
 */
enum class ReviewWarning(val code: String, val key: String) {
    MISSING_GPS("missing_gps", "review.warning.missing_gps"),
    MISSING_TIME("missing_time", "review.warning.missing_time"),
    TIMEZONE_NEEDS_CONFIRMATION("timezone_needs_confirmation", "review.warning.timezone_needs_confirmation"),
    ADDRESS_NEEDS_CONFIRMATION("address_needs_confirmation", "review.warning.address_needs_confirmation"),
    SUGGESTED_ADDRESS_NEEDS_CONFIRMATION("suggested_address_needs_confirmation", "review.warning.suggested_address_needs_confirmation"),
    TIME_NEEDS_CONFIRMATION("time_needs_confirmation", "review.warning.time_needs_confirmation"),
    LOCATION_NEEDS_CONFIRMATION("location_needs_confirmation", "review.warning.location_needs_confirmation"),
    ;

    companion object {
        fun fromCode(code: String): ReviewWarning? = entries.firstOrNull { it.code == code }

        /** A stored code, or a text an older draft stored in any catalog language; else null. */
        fun fromStored(stored: String, catalog: StringCatalog): ReviewWarning? =
            fromCode(stored) ?: entries.firstOrNull { warning ->
                val entry = catalog.rawEntry(warning.key)
                entry != null && catalog.languages.any { language -> entry.opt(language.code) == stored }
            }
    }
}

/** The questions AppShell attaches to a new draft, as codes (same rules and order as before PR-2b). */
internal fun initialReviewWarnings(evidence: Evidence): Set<String> = buildSet {
    if (evidence.latitude == null) add(ReviewWarning.MISSING_GPS.code)
    if (evidence.capturedAtIso == null) add(ReviewWarning.MISSING_TIME.code) else add(ReviewWarning.TIMEZONE_NEEDS_CONFIRMATION.code)
    add(ReviewWarning.ADDRESS_NEEDS_CONFIRMATION.code)
}

/**
 * The texts for [unresolved] in the active language: known warnings in their constant
 * order (the order a new draft adds them), then any other stored text as it is.
 */
internal fun reviewWarningLabels(strings: Strings, unresolved: Set<String>): List<String> {
    val known = mutableSetOf<ReviewWarning>()
    val other = mutableListOf<String>()
    for (stored in unresolved) {
        val warning = ReviewWarning.fromStored(stored, strings.catalog)
        if (warning != null) known += warning else other += stored
    }
    return ReviewWarning.entries.filter { it in known }.map { strings.t(it.key) } + other
}

/**
 * The "needs checking" line. In an RTL language the list is isolated right to left
 * inside the catalog's isolate: the catalog's first-strong FSI would read "GPS חסר, ..."
 * as left to right and reverse the list.
 */
internal fun unresolvedText(strings: Strings, unresolved: Set<String>): String {
    val items = reviewWarningLabels(strings, unresolved).joinToString()
    val rtl = strings.catalog.language(strings.languageCode)?.dir == TextDir.RTL
    return strings.t("review.unresolved", mapOf("items" to if (rtl) "${Bidi.RLI}$items${Bidi.PDI}" else items))
}

internal fun reviewPlateText(strings: Strings, plate: String): String =
    strings.t("review.plate", mapOf("plate" to plate.ifBlank { strings.t("review.plate_not_confirmed") }))

internal fun reviewPlateSuggestionText(strings: Strings, plate: String): String =
    strings.t("review.plate_suggestion", mapOf("plate" to plate))

internal fun reviewAddressText(strings: Strings, address: String): String =
    strings.t("review.address", mapOf("address" to address.ifBlank { strings.t("review.address_missing") }))

internal fun reviewAddressSuggestionText(strings: Strings, display: String): String =
    strings.t("review.address_suggestion", mapOf("address" to display))

/**
 * The confirmed-time line. The old line wrapped the time in LRI..PDI by hand. The catalog
 * does the same for a time of digits and Latin; a time with Hebrew in it (an unknown time
 * zone, or "missing") keeps an explicit LRI so it still reads left to right.
 */
internal fun reviewTimeText(strings: Strings, confirmedTime: String?): String {
    val shown = HebrewPresentation.evidenceTime(confirmedTime)
    val value = if (Bidi.hasStrongRtl(shown)) "${Bidi.LRI}$shown${Bidi.PDI}" else shown
    return strings.t("review.time", mapOf("time" to value))
}

internal fun reviewGpsText(strings: Strings, hasGps: Boolean): String =
    strings.t("review.edit.gps", mapOf("status" to strings.t(if (hasGps) "review.edit.gps_read" else "review.edit.gps_missing")))
