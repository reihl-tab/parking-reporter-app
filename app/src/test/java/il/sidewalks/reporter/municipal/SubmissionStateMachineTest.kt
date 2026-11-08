package il.sidewalks.reporter.municipal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One-attempt semantics of the submission state machine, mirroring the
 * helper's submit() transitions. Uses a fake transport; no network.
 */
class SubmissionStateMachineTest {

    private open class FakeClient(
        var contactId: String? = "synthetic-contact-id",
        var createError: Exception? = null,
        var attachError: Exception? = null,
        var createResult: Map<String, Any?> = mapOf(
            "CaseIdInCRM" to "synthetic-case-id",
            "WebCaseIdInCRM" to "synthetic-webcase-id",
            "CaseNumberInCRM" to "2000-0000000",
        ),
    ) : MunicipalClient {
        val calls = mutableListOf<String>()

        override fun login(): String {
            calls.add("login")
            return "synthetic-token"
        }

        override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> {
            calls.add("contact")
            return if (contactId == null) mapOf() else mapOf("ContactIdInCRM" to contactId)
        }

        override fun createCase(
            token: String,
            contact: LinkedHashMap<String, Any?>,
            report: LinkedHashMap<String, Any?>,
        ): Map<String, Any?> {
            calls.add("create")
            createError?.let { throw it }
            return createResult
        }

        override fun attachPhoto(
            token: String,
            caseIdInCRM: String,
            webCaseIdInCRM: String,
            fileName: String,
            jpeg: ByteArray,
        ) {
            calls.add("attach")
            attachError?.let { throw it }
        }
    }

    private val contact = MunicipalPayloads.buildContact(
        MunicipalPayloads.Reporter("ישראל", "ישראלי", "+972501234567", "000000018", "synthetic@example.com")
    )

    private val report = MunicipalPayloads.buildReport(
        MunicipalPayloads.ReportFields("דיווח סינתטי.", "1145", "1630", "הפלמח", "900", 220000, 630500)
    )

    private val jpeg = "synthetic-jpeg-bytes-not-a-photo".toByteArray()

    private fun machine(client: FakeClient) =
        SubmissionStateMachine(client) { "synthetic-file-name.jpg" }

    @Test
    fun freshRunCompletesWithHelperTransitions() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        val result = machine(client).submit(store, contact, report, jpeg)
        assertTrue(result is SubmissionResult.Submitted)
        assertEquals("synthetic-case-id", result.case["CaseIdInCRM"])
        assertEquals(listOf("prepared", "creating", "created", "attaching", "complete"), store.writes)
        assertEquals(listOf("login", "contact", "create", "attach"), client.calls)
        assertEquals("synthetic-file-name.jpg", store.stored!!["file_name"])
    }

    @Test
    fun completedStateShortCircuitsWithoutNetwork() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        store.write(mutableMapOf("status" to "complete", "case" to mapOf("CaseIdInCRM" to "synthetic-case-id")))
        val result = machine(client).submit(store, contact, report, jpeg)
        assertTrue(result is SubmissionResult.AlreadySubmitted)
        assertEquals("synthetic-case-id", result.case["CaseIdInCRM"])
        assertEquals(emptyList<String>(), client.calls)
    }

    @Test
    fun creatingStateIsRefusedAsUncertain() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        store.write(mutableMapOf("status" to "creating"))
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        assertEquals(emptyList<String>(), client.calls)
    }

    @Test
    fun unknownStateIsRefused() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        store.write(mutableMapOf("status" to "half-done"))
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        assertEquals(emptyList<String>(), client.calls)
    }

    @Test
    fun attachingStateRequiresExplicitPhotoRetry() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        val case = mapOf("CaseIdInCRM" to "synthetic-case-id", "WebCaseIdInCRM" to "synthetic-webcase-id")
        store.write(mutableMapOf("status" to "attaching", "case" to case, "file_name" to "synthetic-file-name.jpg"))
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        // With retryPhoto only the photo is re-attempted; no contact/create calls.
        val result = machine(client).submit(store, contact, report, jpeg, retryPhoto = true)
        assertTrue(result is SubmissionResult.Submitted)
        assertEquals(listOf("login", "attach"), client.calls)
        assertEquals("complete", store.stored!!["status"])
    }

    @Test
    fun photoRetryOnFreshStateIsRefused() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        assertThrows(MunicipalInputException::class.java) {
            machine(client).submit(store, contact, report, jpeg, retryPhoto = true)
        }
        assertEquals(emptyList<String>(), client.calls)
    }

    @Test
    fun createFailureStopsAtCreatingWithReviewNeeded() {
        val client = FakeClient(createError = MunicipalApiException("CreateCase: network failure or invalid response."))
        val store = InMemorySubmissionStateStore()
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        assertEquals("creating", store.stored!!["status"])
        assertEquals(listOf("prepared", "creating"), store.writes)
        assertEquals(listOf("login", "contact", "create"), client.calls)
    }

    @Test
    fun missingCaseReferenceIsUncertain() {
        val client = FakeClient(createResult = mapOf("CaseIdInCRM" to "synthetic-case-id"))
        val store = InMemorySubmissionStateStore()
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        assertEquals("creating", store.stored!!["status"])
    }

    @Test
    fun attachFailureStopsAtAttachingAndRetrySucceeds() {
        val client = FakeClient(attachError = MunicipalApiException("AddFileToCase: HTTP 500."))
        val store = InMemorySubmissionStateStore()
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        assertEquals("attaching", store.stored!!["status"])
        assertEquals(listOf("login", "contact", "create", "attach"), client.calls)

        client.attachError = null
        client.calls.clear()
        val result = machine(client).submit(store, contact, report, jpeg, retryPhoto = true)
        assertTrue(result is SubmissionResult.Submitted)
        assertEquals(listOf("login", "attach"), client.calls)
        assertEquals("complete", store.stored!!["status"])
    }

    @Test
    fun missingContactIdFailsBeforeCreate() {
        val client = FakeClient(contactId = null)
        val store = InMemorySubmissionStateStore()
        assertThrows(MunicipalApiException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        assertEquals(listOf("login", "contact"), client.calls)
    }

    @Test
    fun concurrentSubmitSecondIsRefusedAndExactlyOneCaseIsCreated() {
        // Thread A blocks inside login; a second submit through the SAME store
        // must be refused by the single-flight claim instead of racing to a
        // duplicate CreateCase.
        val gate = java.util.concurrent.CountDownLatch(1)
        val inLogin = java.util.concurrent.CountDownLatch(1)
        val client = object : FakeClient() {
            override fun login(): String {
                inLogin.countDown()
                gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
                return super.login()
            }
        }
        val store = InMemorySubmissionStateStore()
        var resultA: SubmissionResult? = null
        val threadA = kotlin.concurrent.thread {
            resultA = machine(client).submit(store, contact, report, jpeg)
        }
        assertTrue(inLogin.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertThrows(MunicipalReviewNeededException::class.java) {
            machine(client).submit(store, contact, report, jpeg)
        }
        gate.countDown()
        threadA.join(5000)
        assertTrue(resultA is SubmissionResult.Submitted)
        assertEquals(1, client.calls.count { it == "create" })
    }

    @Test
    fun submitDoesNotMutateTheCallersContactMap() {
        val client = FakeClient()
        val store = InMemorySubmissionStateStore()
        val caller = LinkedHashMap(contact)
        val snapshot = LinkedHashMap(caller)
        machine(client).submit(store, caller, report, jpeg)
        assertEquals(snapshot, caller)
    }
}
