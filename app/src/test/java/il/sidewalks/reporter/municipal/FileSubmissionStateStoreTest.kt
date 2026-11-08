package il.sidewalks.reporter.municipal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSubmissionStateStoreTest {

    private fun tempStore(): Pair<File, FileSubmissionStateStore> {
        val dir = java.nio.file.Files.createTempDirectory("submission-store").toFile()
        val store = FileSubmissionStateStore.forReport(dir, "6f2b6a4e-1234-4abc-9def-0123456789ab")
        return dir to store
    }

    @Test fun emptyReadReturnsNull() {
        val (_, store) = tempStore()
        assertNull(store.read())
    }

    @Test fun writeThenReadRoundTripsTheHelperStateShape() {
        val (_, store) = tempStore()
        val state = linkedMapOf<String, Any?>(
            "version" to 1L,
            "status" to "created",
            "file_name" to "abc.jpg",
            "case" to linkedMapOf<String, Any?>(
                "CaseIdInCRM" to "case-1",
                "WebCaseIdInCRM" to "web-1",
                "CaseNumberInCRM" to "2611-1234567",
            ),
        )
        store.write(state)
        val back = store.read()!!
        assertEquals(1L, back["version"])
        assertEquals("created", back["status"])
        assertEquals("abc.jpg", back["file_name"])
        @Suppress("UNCHECKED_CAST")
        val case = back["case"] as Map<String, Any?>
        assertEquals("2611-1234567", case["CaseNumberInCRM"])
    }

    @Test fun unicodeSurvivesRoundTrip() {
        val (_, store) = tempStore()
        store.write(linkedMapOf<String, Any?>("version" to 1L, "status" to "prepared",
            "file_name" to "x.jpg", "note" to "חניה על המדרכה"))
        assertEquals("חניה על המדרכה", store.read()!!["note"])
    }

    @Test fun corruptStateFailsClosed() {
        val (dir, store) = tempStore()
        File(dir, "6f2b6a4e-1234-4abc-9def-0123456789ab.json").writeText("{not json")
        try {
            store.read()
            org.junit.Assert.fail("corrupt state must throw")
        } catch (e: Exception) {
            assertTrue(e is MunicipalInputException || e is IllegalStateException)
        }
    }

    @Test fun invalidReportIdRefused() {
        try {
            FileSubmissionStateStore.forReport(java.nio.file.Files.createTempDirectory("submission-store").toFile(), "../escape")
            org.junit.Assert.fail("path escape must throw")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun singleFlightClaimSharedAcrossInstancesOfOneFile() {
        val (dir, storeA) = tempStore()
        // A second instance resolving to the SAME state file shares the claim.
        val storeB = FileSubmissionStateStore.forReport(dir, "6f2b6a4e-1234-4abc-9def-0123456789ab")
        val outcome = storeA.withSubmissionLock {
            try {
                storeB.withSubmissionLock { }
                org.junit.Assert.fail("concurrent claim must be refused")
            } catch (_: MunicipalReviewNeededException) { }
            "done"
        }
        assertEquals("done", outcome)
        // Free again after release.
        assertEquals("ok", storeB.withSubmissionLock { "ok" })
    }

    @Test fun machineDrivesTheStoreThroughAllStates() {
        val (_, store) = tempStore()
        val client = FakeClient()
        val machine = SubmissionStateMachine(client, fileNameGenerator = { "fixed.jpg" })
        val contact = MunicipalPayloads.buildContact(MunicipalPayloads.Reporter(
            "Test", "User", "0501234567", "000000018", "t@example.com"))
        val report = MunicipalPayloads.buildReport(MunicipalPayloads.ReportFields(
            "desc", "1145", "1234", "רחוב", "5", 220000, 630000))
        val result = machine.submit(store, contact, report, byteArrayOf(1, 2, 3))
        assertTrue(result is SubmissionResult.Submitted)
        assertEquals("complete", store.read()!!["status"])
        // A second submit short-circuits without any further client calls.
        val again = machine.submit(store, MunicipalPayloads.buildContact(MunicipalPayloads.Reporter(
            "Test", "User", "0501234567", "000000018", "t@example.com")), report, byteArrayOf(1, 2, 3))
        assertTrue(again is SubmissionResult.AlreadySubmitted)
        assertEquals(1, client.logins)
    }

    private class FakeClient : MunicipalClient {
        var logins = 0
        override fun login(): String { logins++; return "token" }
        override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> =
            mapOf("ContactIdInCRM" to "contact-1")
        override fun createCase(token: String, contact: LinkedHashMap<String, Any?>, report: LinkedHashMap<String, Any?>): Map<String, Any?> =
            mapOf("CaseIdInCRM" to "c1", "WebCaseIdInCRM" to "w1", "CaseNumberInCRM" to "2611-0000001")
        override fun attachPhoto(token: String, caseIdInCRM: String, webCaseIdInCRM: String, fileName: String, jpeg: ByteArray) { }
    }
}
