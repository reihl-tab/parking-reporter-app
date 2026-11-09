package il.sidewalks.reporter.municipal

import il.sidewalks.reporter.core.ReportCategory
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.security.ReporterSettings
import java.io.File

/**
 * M-B glue between an approved review draft and the one-attempt
 * SubmissionStateMachine. Android-free on purpose (JVM-tested); the UI maps
 * outcomes to Hebrew copy - this class never produces user-facing text.
 *
 * Hard rules preserved here:
 * - the draft must be locally approved (digest matches) before anything runs;
 * - the report photo attached is the immutable original bytes, like the
 *   helper pipeline, never a re-encoded derivative;
 * - every municipal failure maps to a typed outcome; nothing is retried
 *   silently and no state is deleted here.
 */
class SubmissionCoordinator(
    private val clientFactory: (ReporterSettings) -> MunicipalClient,
    private val streets: StreetDirectory,
    private val storeFor: (stableId: String) -> SubmissionStateStore,
) {
    sealed interface Outcome {
        /** The city accepted the case; [caseNumber] is the municipal number. */
        data class Submitted(val caseNumber: String?) : Outcome

        /** This exact report was already submitted earlier (state short-circuit). */
        data class AlreadySubmitted(val caseNumber: String?) : Outcome

        /**
         * Refused before or during submission: [reason] is machine-readable
         * for the UI's Hebrew mapping; [detail] is the municipality's or
         * validator's exact answer, shown verbatim where the UI allows.
         */
        data class Blocked(val reason: Reason, val detail: String) : Outcome
    }

    enum class Reason {
        NOT_APPROVED,
        MISSING_CREDENTIALS,
        UNSUPPORTED_CATEGORY,
        ADDRESS_UNRESOLVED,
        INPUT_INVALID,
        /** The app itself failed before/while talking to the city - NOT a municipal answer. */
        LOCAL_FAILURE,
        /** The private address lookup is not installed on this device. */
        LOOKUP_NOT_INSTALLED,
        LOOKUP_MISMATCH,
        MUNICIPAL_REVIEW_NEEDED,
        MUNICIPAL_API_FAILURE,
    }

    @Suppress("UNCHECKED_CAST")
    fun submitApproved(
        draft: ReviewDraft,
        settings: ReporterSettings,
        originalJpeg: File,
    ): Outcome {
        if (!draft.isApproved()) return Outcome.Blocked(Reason.NOT_APPROVED, "draft not approved")
        if (!settings.hasMunicipalCredentials()) {
            return Outcome.Blocked(Reason.MISSING_CREDENTIALS, "municipal credentials absent")
        }
        val category = ReportCategory.from(draft.subject)
            ?: return Outcome.Blocked(Reason.UNSUPPORTED_CATEGORY, draft.subject)
        val gps = if (draft.confirmedLatitude != null && draft.confirmedLongitude != null) {
            draft.confirmedLatitude to draft.confirmedLongitude
        } else {
            null
        }
        // A confirmed address line always carries street + house. Without a
        // house number we must NOT fall back to the GPS-nearest house: the
        // app would file a street and number the user never confirmed.
        val address = splitAddress(draft.address)
            ?: return Outcome.Blocked(
                Reason.ADDRESS_UNRESOLVED, "the confirmed address has no house number")
        val resolved = try {
            streets.resolve(address.first, address.second, gps)
        } catch (e: MunicipalInputException) {
            return Outcome.Blocked(Reason.ADDRESS_UNRESOLVED, e.message ?: "address unresolved")
        }
        val contact = try {
            MunicipalPayloads.buildContact(MunicipalPayloads.Reporter(
                settings.firstName, settings.lastName, settings.phone, settings.idNumber, settings.email))
        } catch (e: MunicipalInputException) {
            return Outcome.Blocked(Reason.INPUT_INVALID, e.message ?: "reporter invalid")
        }
        val report = try {
            MunicipalPayloads.buildReport(MunicipalPayloads.ReportFields(
                description = draft.exactHebrewText,
                subjectCode = category.municipalSubjectCode,
                streetCode = resolved.streetCode,
                streetName = resolved.streetName,
                houseNumber = resolved.house,
                coordinateX = resolved.x,
                coordinateY = resolved.y,
            ))
        } catch (e: MunicipalInputException) {
            return Outcome.Blocked(Reason.INPUT_INVALID, e.message ?: "report invalid")
        }
        val jpeg = try {
            originalJpeg.readBytes()
        } catch (e: Exception) {
            return Outcome.Blocked(Reason.INPUT_INVALID, "original unreadable")
        }
        val client = try {
            clientFactory(settings)
        } catch (e: MunicipalInputException) {
            return Outcome.Blocked(Reason.MISSING_CREDENTIALS, e.message ?: "credentials invalid")
        }
        val store = storeFor(draft.evidence.stableId)
        val result = try {
            SubmissionStateMachine(client).submit(store, contact, report, jpeg)
        } catch (e: MunicipalReviewNeededException) {
            return Outcome.Blocked(Reason.MUNICIPAL_REVIEW_NEEDED, e.message ?: "review needed")
        } catch (e: IllegalStateException) {
            return Outcome.Blocked(
                Reason.MUNICIPAL_REVIEW_NEEDED, e.message ?: "saved state unreadable")
        } catch (e: MunicipalApiException) {
            return Outcome.Blocked(Reason.MUNICIPAL_API_FAILURE, e.message ?: "municipal failure")
        } catch (e: MunicipalInputException) {
            return Outcome.Blocked(Reason.INPUT_INVALID, e.message ?: "input invalid")
        }
        val caseNumber = result.case["CaseNumberInCRM"] as? String
        return when (result) {
            is SubmissionResult.Submitted -> Outcome.Submitted(caseNumber)
            is SubmissionResult.AlreadySubmitted -> Outcome.AlreadySubmitted(caseNumber)
        }
    }

    companion object {
        private val HOUSE_SUFFIX = Regex("^(.*\\S)\\s+(" + HOUSE_NUMBER.pattern + ")$")

        /**
         * Splits a confirmed "street house" line into (street, house). A line
         * with no trailing house number returns null and the coordinator
         * refuses - the GPS-only resolver path exists for parity but is not
         * reachable from a confirmed draft.
         */
        fun splitAddress(address: String): Pair<String, String>? {
            val match = HOUSE_SUFFIX.matchEntire(address.trim()) ?: return null
            return match.groupValues[1] to match.groupValues[2]
        }
    }
}
