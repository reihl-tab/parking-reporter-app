package il.sidewalks.reporter.municipal

/**
 * The one house-number grammar every stage uses - address lookup, address
 * split and payload build (#164): leading ASCII digits, then letters,
 * digits, underscore, space, slash or dash. The underscore keeps parity with
 * the helper's Python `\w`; the leading `[0-9]+` refuses Hebrew-first values.
 */
internal val HOUSE_NUMBER = Regex("[0-9]+[\\p{L}\\p{N}_ /\\-]*")

internal const val HOUSE_NUMBER_MAX_LENGTH = 16
