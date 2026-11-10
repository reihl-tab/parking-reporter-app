package il.sidewalks.reporter.municipal

import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.security.ReporterSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionCoordinatorTest {

    private val settings = ReporterSettings(
        firstName = "Test", lastName = "User", phone = "0501234567",
        idNumber = "000000018", email = "t@example.com",
        municipalSubscriptionKey = "k", municipalUsername = "u", municipalPassword = "p",
    )

    private fun approvedDraft(): ReviewDraft {
        val evidence = Evidence("stable-1", "a".repeat(64), "2026-11-08T10:00:00+02:00", 31.7683, 35.2137)
        val draft = ReviewDraft(evidence, "12345678", "רחוב הנביאים 12", "sidewalk_parking",
            "רכב חנה על המדרכה", emptySet())
        return draft.approve()
    }

    private fun fixture(): StreetDirectory = StreetDirectory.load("""
        {"version": 1,
         "streets": [{"StreetCode": "1001", "StreetName": "רחוב הנביאים"}],
         "addresses": [[31.7683, 35.2137, "12", "רחוב הנביאים"]]}
    """.trimIndent())

    private fun coordinator(client: MunicipalClient): SubmissionCoordinator =
        SubmissionCoordinator({ client }, fixture()) { InMemorySubmissionStateStore() }

    private fun jpegFile(): File {
        val f = File.createTempFile("original", ".jpg")
        f.writeBytes(byteArrayOf(1, 2, 3, 4))
        return f
    }

    @Test fun happyPathSubmitsWithPipelinePayloadShape() {
        val client = RecordingClient()
        val outcome = coordinator(client).submitApproved(approvedDraft(), settings, jpegFile())
        assertTrue(outcome is SubmissionCoordinator.Outcome.Submitted)
        assertEquals("2611-0000001", (outcome as SubmissionCoordinator.Outcome.Submitted).caseNumber)
        assertEquals("1145", client.seenReport?.get("CaseSubjectCode"))
        assertEquals("1001", client.seenReport?.get("CaseStreetCode"))
        assertEquals("רחוב הנביאים 12", client.seenReport?.get("CaseAddressText"))
        assertEquals("רכב חנה על המדרכה", client.seenReport?.get("CaseDescription"))
        assertTrue(client.seenReport?.get("CoordinateX") is Int)
    }

    @Test fun unapprovedDraftBlocked() {
        val draft = approvedDraft().copy(approvedDigest = null)
        val outcome = coordinator(RecordingClient()).submitApproved(draft, settings, jpegFile())
        assertEquals(SubmissionCoordinator.Reason.NOT_APPROVED,
            (outcome as SubmissionCoordinator.Outcome.Blocked).reason)
    }

    @Test fun missingCredentialsBlocked() {
        val noCreds = settings.copy(municipalPassword = "")
        val outcome = coordinator(RecordingClient()).submitApproved(approvedDraft(), noCreds, jpegFile()) as SubmissionCoordinator.Outcome.Blocked
        assertEquals(SubmissionCoordinator.Reason.MISSING_CREDENTIALS, outcome.reason)
        // The detail names exactly which values are missing (constant IDs, never values).
        assertEquals("municipal credentials absent: api_password", outcome.detail)
        assertEquals(listOf("api_password"), outcome.missingFields)
    }

    @Test fun unresolvableAddressBlockedBeforeAnyNetwork() {
        val client = RecordingClient()
        val draft = approvedDraft().copy(address = "רחוב שאינו קיים 1").approve()
        val outcome = coordinator(client).submitApproved(draft, settings, jpegFile())
        assertEquals(SubmissionCoordinator.Reason.ADDRESS_UNRESOLVED,
            (outcome as SubmissionCoordinator.Outcome.Blocked).reason)
        assertEquals(0, client.logins)
    }

    @Test fun streetOnlyAddressBlockedBeforeAnyNetwork() {
        // A confirmed address with no house number must NOT fall back to the
        // GPS-nearest house: the user never confirmed that house.
        val client = RecordingClient()
        val draft = approvedDraft().copy(address = "רחוב הנביאים").approve()
        val outcome = coordinator(client).submitApproved(draft, settings, jpegFile())
        assertEquals(SubmissionCoordinator.Reason.ADDRESS_UNRESOLVED,
            (outcome as SubmissionCoordinator.Outcome.Blocked).reason)
        assertEquals(0, client.logins)
    }

    @Test fun reviewNeededPassesThroughWithoutRetry() {
        val client = FailingCreateClient()
        val outcome = coordinator(client).submitApproved(approvedDraft(), settings, jpegFile())
        assertEquals(SubmissionCoordinator.Reason.MUNICIPAL_REVIEW_NEEDED,
            (outcome as SubmissionCoordinator.Outcome.Blocked).reason)
        assertEquals(1, client.createCalls)
    }

    @Test fun splitAddressHandlesCommonShapes() {
        assertEquals("רחוב הנביאים" to "12", SubmissionCoordinator.splitAddress("רחוב הנביאים 12"))
        assertEquals("אגריפס" to "12א", SubmissionCoordinator.splitAddress("אגריפס 12א"))
        assertEquals(null, SubmissionCoordinator.splitAddress("רחוב ללא מספר"))
    }

    private open class RecordingClient : MunicipalClient {
        var logins = 0
        var seenReport: Map<String, Any?>? = null
        override fun login(): String { logins++; return "token" }
        override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> =
            mapOf("ContactIdInCRM" to "contact-1")
        override fun createCase(token: String, contact: LinkedHashMap<String, Any?>, report: LinkedHashMap<String, Any?>): Map<String, Any?> {
            seenReport = report
            return mapOf("CaseIdInCRM" to "c1", "WebCaseIdInCRM" to "w1", "CaseNumberInCRM" to "2611-0000001")
        }
        override fun attachPhoto(token: String, caseIdInCRM: String, webCaseIdInCRM: String, fileName: String, jpeg: ByteArray) { }
    }

    private class FailingCreateClient : RecordingClient() {
        var createCalls = 0
        override fun createCase(token: String, contact: LinkedHashMap<String, Any?>, report: LinkedHashMap<String, Any?>): Map<String, Any?> {
            createCalls++
            throw MunicipalApiException("CreateCase: municipality did not confirm success.")
        }
    }
}
