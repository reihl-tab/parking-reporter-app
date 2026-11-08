package il.sidewalks.reporter.core

/**
 * The M-B send gate (issue #153): the draft SHOWN on the review screen is
 * what goes to the municipality - never a silently different saved copy.
 * Both must be the same draft, and it must be approved. A failed or dropped
 * save must never send stale text.
 */
object SendGate {
    fun readyToSend(shown: ReviewDraft?, saved: ReviewDraft?): Boolean =
        shown != null && saved != null && shown == saved && shown.isApproved()
}
