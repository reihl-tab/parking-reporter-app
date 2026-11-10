package il.sidewalks.reporter

import il.sidewalks.reporter.security.ReporterSettings
import il.sidewalks.reporter.security.SettingsCodec
import org.junit.Assert.*
import org.junit.Test

class PassportSettingsCodecTest {
    @Test fun encryptedEnvelopeCodecRoundTripsPassportAndDefaultsOldProfilesToIdMode() {
        val settings = ReporterSettings("Test", "User", "+14155552671", "", "a@example.com", passportMode = true, passportNumber = "SYNTHETIC-PASSPORT")
        assertEquals(settings, SettingsCodec.decode(SettingsCodec.encode(settings)))
        assertEquals("ReporterSettings(redacted)", settings.toString())
        val old = """{"firstName":"Test","lastName":"User","phone":"0501234567","idNumber":"000000018","email":"a@example.com"}""".toByteArray()
        assertFalse(SettingsCodec.decode(old).passportMode)
        assertEquals("", SettingsCodec.decode(old).passportNumber)
    }
}
