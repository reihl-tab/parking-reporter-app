package il.sidewalks.reporter

import il.sidewalks.reporter.municipal.ResponseDiagnostics
import org.junit.Assert.*
import org.junit.Test

class ResponseDiagnosticsTest {
    @Test fun nestedSecretsAndActualValuesNeverSurvive() {
        val raw = """{"Success":false,"Token":"abc","nested":{"Password":"def","message":"known-secret"},"items":[{"Authorization":"Bearer xyz"}],"CaseNumberInCRM":"2000-0000000"}"""
        val redacted = ResponseDiagnostics.redactBody(raw, listOf("known-secret"))
        for (secret in listOf("abc", "def", "xyz", "known-secret")) assertFalse(secret, redacted.contains(secret))
        assertTrue(redacted.contains("2000-0000000"))
        assertTrue(redacted.contains("false"))
    }
    @Test fun lineTerminatorsInSensitiveKeysCannotBypassRedaction() {
        val input = org.json.JSONObject().put("\nToken", "NL-SECRET").put("\rPassword", "CR-SECRET").put("\u2028Token", "LS-SECRET")
        val redacted = ResponseDiagnostics.redactBody(input.toString(), emptyList())
        for (secret in listOf("NL-SECRET", "CR-SECRET", "LS-SECRET")) assertFalse(secret, redacted.contains(secret))
    }
    @Test fun sensitiveObjectArrayNumberAndBooleanLeavesAreScrubbedElsewhere() {
        val raw = """{"Token":{"v":"OBJ-1","nested":["ARR-1",987654321,true]},"echo":"OBJ-1 ARR-1 987654321 true","numeric":987654321,"boolean":true}"""
        val redacted = ResponseDiagnostics.redactBody(raw, emptyList())
        for (secret in listOf("OBJ-1", "ARR-1", "987654321", "true")) assertFalse(secret, redacted.contains(secret))
    }
    @Test fun longestSecretIsRemovedBeforeItsPrefix() {
        assertFalse(ResponseDiagnostics.redactBody("""{"message":"abcdef"}""", listOf("abcd", "abcdef")).contains("ef"))
    }
    @Test fun unknownAndOversizeBodiesAreNotStored() {
        assertEquals("[body omitted: non-JSON]", ResponseDiagnostics.redactBody("password = rawsecret", emptyList()))
        assertEquals("[body omitted: size limit]", ResponseDiagnostics.redactBody("a".repeat(65_537), emptyList()))
    }
}
