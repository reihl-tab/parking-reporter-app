package il.sidewalks.reporter.municipal

import java.security.MessageDigest

/**
 * Pins the on-device private address lookup (#165): the app refuses to
 * resolve against a copy whose sha256 differs from the pipeline's current
 * version-1 file, so a tampered or stale file cannot silently change
 * address resolution. The file itself stays private (not committed); only
 * its digest is public, matching the taxonomy fingerprint approach (#98).
 * When the private data is regenerated, the pin changes in the same change
 * that adopts the new file.
 */
class AddressLookupMismatchException : Exception("address lookup on device does not match the pinned copy")

object AddressLookupPin {
    const val EXPECTED_SHA256 =
        "5eaa83a9eb9253798109555bf2f6ea6ab6ec941a86e86c15c6761b2e5ee046d4"

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Returns the text unchanged when pinned; refuses anything else. */
    fun verifyPinned(text: String): String {
        if (sha256Hex(text) != EXPECTED_SHA256) {
            throw AddressLookupMismatchException()
        }
        return text
    }
}
