package il.sidewalks.reporter.core

/**
 * Explicit local categories, not a classifier. Each category carries the
 * captured municipal 106 subject code it maps to (see MunicipalSubjects);
 * that numeric code is what a report sends, never a label.
 *
 * Issue #115 PR-2c: the display name is the catalog key [labelKey], looked up in the
 * active UI language when shown (by the UI's presentation helper). The report payload
 * carries only [municipalSubjectCode] and the approved text, so the UI language never
 * changes what reaches the city (pinned by MunicipalReportTextStaysHebrewTest).
 */
enum class ReportCategory(
    val code: String,
    val labelKey: String,
    val needsPlate: Boolean,
    val municipalSubjectCode: String,
) {
    SIDEWALK_PARKING("sidewalk_parking", "category.sidewalk_parking", true, "1145"),
    BROKEN_SIGN("broken_sign", "category.broken_sign", false, "17841"),
    FADED_CROSSWALK("faded_crosswalk", "category.faded_crosswalk", false, "1137");

    companion object { fun from(code: String): ReportCategory? = entries.firstOrNull { it.code == code } }
}
