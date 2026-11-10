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

    @Test fun passportLocalProfileCannotReachAnyNetworkEvenWithValidIdAndCredentials() {
        val client = RecordingClient()
        val outcome = coordinator(client).submitApproved(approvedDraft(), settings.copy(passportMode = true, passportNumber = "SYNTHETIC-PASSPORT"), jpegFile()) as SubmissionCoordinator.Outcome.Blocked
        assertEquals(SubmissionCoordinator.Reason.PASSPORT_DISCLOSURE_REQUIRED, outcome.reason)
        assertEquals(0, client.logins)
        assertEquals(null, client.seenReport)
    }

    @Test fun passportReviewedDisclosureSubmitsOnlyMaskedPassportAndPlaceholder() {
        val client = RecordingClient()
        val profile = settings.copy(passportMode = true, passportNumber = "SYNTHETIC-PASSPORT")
        val text = approvedDraft().exactHebrewText + "\n\n" + PassportSubmission.disclosure(profile)
        val shown = approvedDraft().edit(text = text).approve()
        val outcome = coordinator(client).submitApproved(shown, profile, jpegFile())
        assertTrue(outcome is SubmissionCoordinator.Outcome.Submitted)
        assertEquals(PassportSubmission.COMPATIBILITY_ID, client.seenContact?.get("ContactIdNumber"))
        assertEquals(text, client.seenReport?.get("CaseDescription"))
        assertTrue(text.contains("xxxxxORT"))
        assertTrue(!text.contains(profile.passportNumber))
    }

    @Test fun foreignPhoneRequiresReviewedRealNumberAndUsesOnlyPlaceholderInContact() {
        val client = RecordingClient()
        val profile = settings.copy(phone = "+12025550123")
        val blocked = coordinator(client).submitApproved(approvedDraft(), profile, jpegFile()) as SubmissionCoordinator.Outcome.Blocked
        assertEquals(SubmissionCoordinator.Reason.PASSPORT_DISCLOSURE_REQUIRED, blocked.reason)
        assertEquals(0, client.logins)
        val text = approvedDraft().exactHebrewText + "\n\n" + PassportSubmission.disclosure(profile)
        assertTrue(coordinator(client).submitApproved(approvedDraft().edit(text = text).approve(), profile, jpegFile()) is SubmissionCoordinator.Outcome.Submitted)
        assertEquals(PassportSubmission.INTERNATIONAL_PHONE_PLACEHOLDER, client.seenContact?.get("MobilePhoneNumber"))
        assertTrue(client.seenReport!!["CaseDescription"].toString().contains("+12025550123"))
        assertEquals("0501234567", PassportSubmission.phoneForPayload(settings.copy(phone = "+972501234567")))
        assertEquals("0501234567", PassportSubmission.phoneForPayload(settings.copy(phone = "972501234567")))
    }

    @Test fun staleDisclosureIsBlockedAndReplacedForPhonePassportAndModeChanges() {
        val old = settings.copy(passportMode = true, passportNumber = "SYNTHETIC-OLD", phone = "+12025550123")
        val oldText = PassportSubmission.reconcileDisclosure(approvedDraft().exactHebrewText, old)
        for (current in listOf(old.copy(phone = "+12025550124"), old.copy(passportNumber = "SYNTHETIC-NEW"), settings)) {
            val client = RecordingClient()
            assertTrue(!PassportSubmission.hasReviewedDisclosure(oldText, current))
            val outcome = coordinator(client).submitApproved(approvedDraft().edit(text = oldText).approve(), current, jpegFile())
            assertEquals(SubmissionCoordinator.Reason.PASSPORT_DISCLOSURE_REQUIRED, (outcome as SubmissionCoordinator.Outcome.Blocked).reason)
            assertEquals(0, client.logins)
            val fixed = PassportSubmission.reconcileDisclosure(oldText, current)
            assertTrue(PassportSubmission.hasReviewedDisclosure(fixed, current))
            assertEquals(fixed, PassportSubmission.reconcileDisclosure(fixed, current))
            if (current.phone != old.phone) assertTrue(!fixed.contains("+12025550123"))
            if (!current.passportMode || current.passportNumber != old.passportNumber) assertTrue(!fixed.contains("xxxxxOLD"))
            assertTrue(coordinator(client).submitApproved(approvedDraft().edit(text = fixed).approve(), current, jpegFile()) is SubmissionCoordinator.Outcome.Submitted)
        }
    }

    @Test fun nationalIdContactsKeepRealIdAndIsraeliMobileWhileForeignPhoneUsesReviewedPlaceholder() {
        for (phone in listOf("0501234567", "+972501234567", "+12025550123", "972501234567")) {
            val profile = settings.copy(phone = phone, passportMode = false)
            val client = RecordingClient()
            val text = PassportSubmission.reconcileDisclosure(approvedDraft().exactHebrewText, profile)
            assertTrue(coordinator(client).submitApproved(approvedDraft().edit(text = text).approve(), profile, jpegFile()) is SubmissionCoordinator.Outcome.Submitted)
            assertEquals(settings.idNumber, client.seenContact?.get("ContactIdNumber"))
            assertEquals(if (PassportSubmission.isIsraeliPhone(phone)) "0501234567" else PassportSubmission.INTERNATIONAL_PHONE_PLACEHOLDER, client.seenContact?.get("MobilePhoneNumber"))
        }
    }
    @Test fun israeliLandlinesRemainBlockedBeforeAnyNetwork() {
        for (passportMode in listOf(false, true)) for (phone in listOf("+97221234567", "97221234567", "021234567", "21234567", "26543210", "541234567", "501234567", "1234567", "14155552671", "+1234567", "+1234567890123456")) {
            val client = RecordingClient()
            val outcome = coordinator(client).submitApproved(approvedDraft(), settings.copy(phone = phone, passportMode = passportMode, passportNumber = "SYNTHETIC-PASSPORT"), jpegFile())
            assertEquals(SubmissionCoordinator.Reason.INPUT_INVALID, (outcome as SubmissionCoordinator.Outcome.Blocked).reason)
            assertEquals(0, client.logins)
        }
    }
    @Test fun shortPassportNeverDisclosesWholeValueAndHiddenStaleLinesAreRejected() {
        val profile = settings.copy(passportMode = true, passportNumber = "ABC")
        assertTrue(PassportSubmission.validPassport("ABC"))
        for (invalid in listOf("", "AB", "A!B", "A".repeat(41))) assertTrue(!PassportSubmission.validPassport(invalid))
        val line = PassportSubmission.disclosure(profile)
        assertTrue(!line.contains("ABC"))
        assertTrue(line.contains("xxxxxBC"))
        for (prefix in listOf(" ", "\u200f", "\u2066 ")) {
            assertTrue(!PassportSubmission.hasReviewedDisclosure(line + "\n" + prefix + line, profile))
            assertEquals(line, PassportSubmission.reconcileDisclosure(prefix + line, profile).trim())
        }
        assertTrue(!PassportSubmission.hasReviewedDisclosure(line + "\n" + line, profile))
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
        var seenContact: Map<String, Any?>? = null
        var logins = 0
        var seenReport: Map<String, Any?>? = null
        override fun login(): String { logins++; return "token" }
        override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> {
            seenContact = contact
            return mapOf("ContactIdInCRM" to "contact-1")
        }
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
