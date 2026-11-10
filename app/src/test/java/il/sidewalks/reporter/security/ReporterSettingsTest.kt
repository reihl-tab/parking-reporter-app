package il.sidewalks.reporter.security

import org.junit.Assert.assertEquals
import org.junit.Test

class ReporterSettingsTest {
    private fun settings(key: String = "k", user: String = "u", pass: String = "p") =
        ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid",
            municipalSubscriptionKey = key, municipalUsername = user, municipalPassword = pass)

    @Test
    fun completeCredentialsHaveNoMissingFields() {
        assertEquals(emptyList<String>(), settings().missingMunicipalFields())
    }

    @Test
    fun missingFieldsAreNamedInOrder() {
        assertEquals(listOf("subscription_key", "api_username", "api_password"),
            settings(key = "", user = "", pass = "").missingMunicipalFields())
        assertEquals(listOf("api_password"), settings(pass = "").missingMunicipalFields())
        assertEquals(listOf("subscription_key", "api_password"),
            settings(key = " ", pass = "").missingMunicipalFields())
    }
}
