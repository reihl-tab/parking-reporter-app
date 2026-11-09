package il.sidewalks.reporter.municipal

import org.junit.Assert.*
import org.junit.Test

class TrialStageClientTest {
    private class Fake(private val fail: Throwable? = null) : MunicipalClient {
        var calls = 0
        override fun login(): String { calls++; fail?.let { throw it }; return "private-token" }
        override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> { calls++; return contact }
        override fun createCase(token: String, contact: LinkedHashMap<String, Any?>, report: LinkedHashMap<String, Any?>): Map<String, Any?> { calls++; return report }
        override fun attachPhoto(token: String, caseIdInCRM: String, webCaseIdInCRM: String, fileName: String, jpeg: ByteArray) { calls++ }
    }
    @Test fun successDelegatesAllCallsOnceWithoutPrintingAnyValues() {
        val fake = Fake(); val markers = mutableListOf<String>(); val client = TrialStageClient(fake, markers::add)
        val contact = linkedMapOf<String, Any?>("private-name" to "private-id")
        val report = linkedMapOf<String, Any?>("private-street" to "private-text")
        assertEquals("private-token", client.login())
        assertSame(contact, client.upsertContact("private-token", contact))
        assertSame(report, client.createCase("private-token", contact, report))
        client.attachPhoto("private-token", "private-case", "private-web", "private-name", byteArrayOf(1))
        assertEquals(4, fake.calls)
        assertEquals(listOf("STAGE_LOGIN_START", "STAGE_LOGIN_RETURNED", "STAGE_CONTACT_START", "STAGE_CONTACT_RETURNED", "STAGE_CREATE_CASE_START", "STAGE_CREATE_CASE_RETURNED", "STAGE_ATTACH_PHOTO_START", "STAGE_ATTACH_PHOTO_RETURNED"), markers)
    }
    @Test fun errorsPreserveIdentityAndNeverRetryOrLeakMessage() {
        val cases = listOf(
            MunicipalApiException("Login: HTTP 403.") to "HTTP_403",
            MunicipalApiException("Login: HTTP 500.") to "HTTP_OTHER",
            MunicipalApiException("Login: network failure or invalid response.") to "NETWORK_OR_RESPONSE",
            MunicipalApiException("Login: municipality did not confirm success.") to "API_REJECTED",
            MunicipalApiException("private-id private-token private-body") to "OTHER",
            IllegalStateException("private-id") to "OTHER",
        )
        for ((error, kind) in cases) {
            val fake = Fake(error); val markers = mutableListOf<String>(); val client = TrialStageClient(fake, markers::add)
            try { client.login(); fail("expected failure") } catch (caught: Throwable) { assertSame(error, caught) }
            assertEquals(1, fake.calls)
            assertEquals(listOf("STAGE_LOGIN_START", "STAGE_LOGIN_ERROR_$kind"), markers)
            assertFalse(markers.joinToString().contains("private"))
        }
    }
}
