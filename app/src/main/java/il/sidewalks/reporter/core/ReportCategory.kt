package il.sidewalks.reporter.core

/**
 * Explicit local categories, not a classifier. Each category carries the
 * captured municipal 106 subject code it maps to (see MunicipalSubjects);
 * the mapping is the M4 parity layer and is not sent anywhere yet (M5).
 */
enum class ReportCategory(
    val code: String,
    val hebrew: String,
    val needsPlate: Boolean,
    val municipalSubjectCode: String,
) {
    SIDEWALK_PARKING("sidewalk_parking", "חניה על המדרכה", true, "1145"),
    BROKEN_SIGN("broken_sign", "תמרור שבור", false, "17841"),
    FADED_CROSSWALK("faded_crosswalk", "מעבר חציה דהוי", false, "1137");

    companion object { fun from(code: String): ReportCategory? = entries.firstOrNull { it.code == code } }
}
