package il.sidewalks.reporter.municipal

/**
 * Error taxonomy of the municipal 106 client, mirroring parking-report-helper
 * api106.py (helper 0.6.0, pinned 51dd5c02916b1ebbe62fdd2390132f9f58a39eb8).
 *
 * Messages never contain credentials, tokens, request URLs, response bodies,
 * plates or addresses.
 */

/** Invalid caller input; fixable before any network call. */
class MunicipalInputException(message: String) : Exception(message)

/** Transport failure, HTTP error, or a response the municipality did not confirm. */
class MunicipalApiException(message: String) : Exception(message)

/**
 * The result of a municipal-side operation is uncertain (for example a CreateCase
 * call whose answer never arrived). Never retry automatically: a human must check
 * the municipal app first, exactly like the helper's ReviewNeeded.
 */
class MunicipalReviewNeededException(message: String) : Exception(message)
