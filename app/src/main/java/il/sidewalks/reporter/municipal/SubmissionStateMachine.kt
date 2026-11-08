package il.sidewalks.reporter.municipal

import java.util.UUID

/**
 * One-attempt submission semantics, ported from parking-report-helper api106.py
 * `submit()` (helper 0.6.0, pinned 51dd5c02916b1ebbe62fdd2390132f9f58a39eb8).
 *
 * States (stored lowercase, like the helper's state files):
 * prepared -> creating -> created -> attaching -> complete.
 *
 * The rules that protect against duplicate municipal cases:
 * - a saved "creating" state means the previous CreateCase result is uncertain:
 *   refuse with MunicipalReviewNeededException, never resubmit;
 * - a saved "attaching" state means the case exists but its photo was not
 *   confirmed: refuse unless retryPhoto=true, and retryPhoto only re-attempts
 *   the photo, never the case;
 * - "complete" short-circuits to AlreadySubmitted;
 * - an unknown status refuses with MunicipalReviewNeededException;
 * - every state change is persisted before the next network call.
 *
 * The store interface keeps this class pure: M-B wires a durable on-device
 * store; tests use an in-memory one. State JSON uses the helper's shape
 * ({"version": 1, "status": ..., "file_name": ..., "case": {...}}) so
 * cross-tool reconciliation stays possible.
 */
interface SubmissionStateStore {
    /** The persisted state map, or null when nothing was persisted yet. */
    fun read(): MutableMap<String, Any?>?

    /** Durably persists the state. Throwing aborts the submission (fail-closed). */
    fun write(state: Map<String, Any?>)

    /**
     * Runs [block] while holding this store's exclusive submission claim,
     * refusing with MunicipalReviewNeededException when another in-flight
     * submission already holds it. The claim spans the whole submit() call:
     * two concurrent submissions must never both reach CreateCase (a
     * duplicate municipal case is the worst outcome this app can produce).
     */
    fun <T> withSubmissionLock(block: () -> T): T
}

sealed class SubmissionResult {
    abstract val case: Map<String, Any?>

    data class Submitted(override val case: Map<String, Any?>) : SubmissionResult()
    data class AlreadySubmitted(override val case: Map<String, Any?>) : SubmissionResult()
}

class SubmissionStateMachine(
    private val client: MunicipalClient,
    private val fileNameGenerator: () -> String = { UUID.randomUUID().toString() + ".jpg" },
) {

    @Suppress("UNCHECKED_CAST")
    fun submit(
        store: SubmissionStateStore,
        contact: LinkedHashMap<String, Any?>,
        report: LinkedHashMap<String, Any?>,
        jpeg: ByteArray,
        retryPhoto: Boolean = false,
    ): SubmissionResult = store.withSubmissionLock {
        // The helper mutates the caller's contact dict (Token, ContactIdInCRM);
        // the port works on a copy so a caller-side map never changes mid-flight.
        val contact: LinkedHashMap<String, Any?> = LinkedHashMap(contact)
        val state: MutableMap<String, Any?> = store.read() ?: mutableMapOf<String, Any?>(
            "version" to 1,
            "status" to "prepared",
        )
        if (!state.containsKey("file_name")) {
            state["file_name"] = fileNameGenerator()
        }
        when (state["status"] as? String) {
            "prepared", "creating", "created", "attaching", "complete" -> Unit
            else -> throw MunicipalReviewNeededException(
                "Unknown saved state; inspect the stored state before continuing."
            )
        }
        if (state["status"] == "creating") {
            throw MunicipalReviewNeededException(
                "A previous case creation has an uncertain result. Check the municipal app; do not delete the state and resubmit."
            )
        }
        if (state["status"] == "complete") {
            return@withSubmissionLock SubmissionResult.AlreadySubmitted(
                (state["case"] as? Map<String, Any?>) ?: emptyMap()
            )
        }
        if (retryPhoto && state["status"] == "prepared") {
            throw MunicipalInputException(
                "No existing municipal case in the saved state. retryPhoto will not create one."
            )
        }
        if (state["status"] == "attaching" && !retryPhoto) {
            throw MunicipalReviewNeededException(
                "The report exists but its photo was not confirmed. Use retryPhoto after checking the municipal app."
            )
        }
        if (state["status"] == "prepared") {
            store.write(state)
        }
        val token = client.login()
        if (state["status"] == "prepared") {
            // The helper's contact dict carries the token into CreateCase.
            contact["Token"] = token
            val contactResponse = client.upsertContact(token, contact)
            val contactId = contactResponse["ContactIdInCRM"] as? String
            if (contactId.isNullOrEmpty()) {
                throw MunicipalApiException("Contact registration returned no ID.")
            }
            contact["ContactIdInCRM"] = contactId
            state["status"] = "creating"
            store.write(state)
            try {
                val result = client.createCase(token, contact, report)
                val caseId = result["CaseIdInCRM"] as? String
                val webCaseId = result["WebCaseIdInCRM"] as? String
                if (caseId.isNullOrEmpty() || webCaseId.isNullOrEmpty()) {
                    throw MunicipalApiException("Missing case reference.")
                }
                val case = LinkedHashMap<String, Any?>()
                case["CaseIdInCRM"] = caseId
                case["WebCaseIdInCRM"] = webCaseId
                case["CaseNumberInCRM"] = result["CaseNumberInCRM"] as? String
                state["case"] = case
                state["status"] = "created"
                store.write(state)
            } catch (e: MunicipalReviewNeededException) {
                throw e
            } catch (e: Exception) {
                throw MunicipalReviewNeededException(
                    "Case creation result is uncertain. No automatic retry. Check the municipal app."
                )
            }
        }
        state["status"] = "attaching"
        store.write(state)
        val case = state["case"] as? Map<String, Any?>
            ?: throw MunicipalReviewNeededException("Saved state lost the case reference.")
        try {
            client.attachPhoto(
                token,
                case["CaseIdInCRM"] as String,
                case["WebCaseIdInCRM"] as String,
                state["file_name"] as String,
                jpeg,
            )
        } catch (e: MunicipalReviewNeededException) {
            throw e
        } catch (e: Exception) {
            throw MunicipalReviewNeededException(
                "The report exists; photo attachment was not confirmed. Use retryPhoto after checking the municipal app."
            )
        }
        state["status"] = "complete"
        store.write(state)
        return@withSubmissionLock SubmissionResult.Submitted(case)
    }
}
