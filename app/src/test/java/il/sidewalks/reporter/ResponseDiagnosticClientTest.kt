package il.sidewalks.reporter

import il.sidewalks.reporter.municipal.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class ResponseDiagnosticClientTest {
    @Test fun throwingDiagnosticNeverChangesSuccessfulCase() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"Success":true,"CaseIdInCRM":"case","WebCaseIdInCRM":"web"}""")); server.start()
            val client = OkHttpMunicipalClient.forTesting(server.url("/"), "key", "user", "pass") { error("diagnostic failure") }
            assertEquals("case", client.createCase("session-token", linkedMapOf(), linkedMapOf())["CaseIdInCRM"])
        }
    }
    @Test fun failingHttpBodyStillReportsHttpStatus() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("x".repeat(2000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)); server.start()
            val client = OkHttpMunicipalClient.forTesting(server.url("/"), "key", "user", "pass") {}
            try { client.createCase("session-token", linkedMapOf(), linkedMapOf()); fail("must fail") }
            catch (e: MunicipalApiException) { assertEquals("CreateCase: HTTP 503.", e.message) }
        }
    }
    @Test fun successfulResponseScrubsKnownTokenAndKey() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"Success":true,"message":"session-token and subscription-secret","Token":"returned-token"}""")); server.start()
            var recorded: ResponseDiagnostic? = null
            val client = OkHttpMunicipalClient.forTesting(server.url("/"), "subscription-secret", "user", "pass") { recorded = it }
            client.createCase("session-token", linkedMapOf(), linkedMapOf())
            assertEquals(200, recorded?.status)
            for (secret in listOf("session-token", "subscription-secret", "returned-token")) assertFalse(recorded!!.body.contains(secret))
        }
    }
}
