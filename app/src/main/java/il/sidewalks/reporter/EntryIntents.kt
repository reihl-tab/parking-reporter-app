package il.sidewalks.reporter

import android.content.Intent

/**
 * Entry points into the report flow (issue #149 phase 1): a deep link
 * (il.sidewalks.reporter://report/new), a share-sheet target (ACTION_SEND with
 * an image) and the Quick Settings tile. Parsing is pure Kotlin over primitives
 * so it runs in JVM unit tests; MainActivity unwraps the Intent and AppShell
 * executes the request.
 */
object EntryIntents {

    /** Action the Quick Settings tile sends to MainActivity. */
    const val ACTION_TILE_NEW_REPORT = "il.sidewalks.reporter.action.NEW_REPORT"

    /**
     * The deep link that starts a new report; shared with the future iOS port.
     * Reverse-DNS scheme per the owner decision on #151, not a generic scheme.
     */
    const val DEEP_LINK_NEW_REPORT = "il.sidewalks.reporter://report/new"

    sealed interface Request {
        /** Open the capture flow to start a new report. */
        data object NewReport : Request

        /** Start a new report pre-filled with a shared photo (URI string). */
        data class SharedPhoto(val uriString: String) : Request
    }

    private val NEW_REPORT_LINK = Regex("^il\\.sidewalks\\.reporter://report(/new)?/?$")

    /**
     * Only content:// shares are accepted: a file:// share would let another
     * app name a path inside our own private storage (confused deputy). The
     * link and scheme matches are exact and case-sensitive.
     */
    fun parse(action: String?, dataString: String?, mimeType: String?, streamUriString: String?): Request? = when {
        action == ACTION_TILE_NEW_REPORT -> Request.NewReport
        action == Intent.ACTION_VIEW && dataString != null && NEW_REPORT_LINK.matches(dataString) ->
            Request.NewReport
        action == Intent.ACTION_SEND && streamUriString != null &&
            mimeType?.startsWith("image/") == true && streamUriString.startsWith("content://") ->
            Request.SharedPhoto(streamUriString)
        else -> null
    }
}

/** A parsed entry request plus a nonce, so repeating the same entry still re-triggers. */
data class EntrySignal(val nonce: Long, val request: EntryIntents.Request)
