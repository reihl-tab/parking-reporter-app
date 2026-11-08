package il.sidewalks.reporter.security

import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Test

/** Plaintext-shape tests only; encryption itself is Keystore-instrumented. */
class SettingsCodecTest {

    @Test
    fun roundtripPreservesMunicipalCredentials() {
        val settings = ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid",
            municipalSubscriptionKey = "synthetic-key", municipalUsername = "synthetic-user",
            municipalPassword = "synthetic-pass")
        val decoded = SettingsCodec.decode(SettingsCodec.encode(settings))
        assertEquals(settings, decoded)
        assertTrue(decoded.hasMunicipalCredentials())
    }

    @Test
    fun oldSavesWithoutCredentialsDecodeToBlank() {
        val legacy = """{"firstName":"א","lastName":"ב","phone":"050","idNumber":"0000000","email":"a@b.invalid"}"""
        val decoded = SettingsCodec.decode(legacy.toByteArray(Charsets.UTF_8))
        assertEquals("", decoded.municipalSubscriptionKey)
        assertEquals("", decoded.municipalUsername)
        assertEquals("", decoded.municipalPassword)
        assertFalse(decoded.hasMunicipalCredentials())
    }

    @Test
    fun blankCredentialsAreOmittedFromJson() {
        val encoded = String(SettingsCodec.encode(ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid")), Charsets.UTF_8)
        assertFalse(encoded.contains("municipal"))
    }

    @Test
    fun partialCredentialsAreNotComplete() {
        assertFalse(ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid",
            municipalSubscriptionKey = "synthetic-key").hasMunicipalCredentials())
    }

    @Test
    fun credentialsNeverAppearInToString() {
        val settings = ReporterSettings("א", "ב", "050", "0000000", "a@b.invalid",
            municipalSubscriptionKey = "synthetic-key", municipalUsername = "synthetic-user",
            municipalPassword = "synthetic-pass")
        val rendered = settings.toString()
        assertFalse(rendered.contains("synthetic-key"))
        assertFalse(rendered.contains("synthetic-user"))
        assertFalse(rendered.contains("synthetic-pass"))
    }

    @Test
    fun diskBytesAreCiphertextNotPlaintext() {
        // Catches a mutant that writes the plaintext JSON to disk: every
        // sentinel value must be absent from the framed bytes.
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val settings = ReporterSettings(
            "SentinelFirst", "SentinelLast", "0500000000", "000000000", "sentinel@example.com",
            municipalSubscriptionKey = "SentinelKey", municipalUsername = "SentinelUser", municipalPassword = "SentinelPass",
        )
        val plaintext = SettingsCodec.encode(settings)
        val disk = SecureSettings.encodeForDisk(plaintext, cipher)
        val diskAsText = String(disk, Charsets.ISO_8859_1)
        for (sentinel in listOf("SentinelFirst", "SentinelLast", "SentinelKey", "SentinelUser", "SentinelPass", "sentinel@example.com", "000000000")) {
            assertFalse("plaintext leaked to disk bytes: $sentinel", diskAsText.contains(sentinel))
        }
        val roundTrip = SecureSettings.decodeForDisk(disk) { iv ->
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        }
        assertEquals("SentinelKey", SettingsCodec.decode(roundTrip).municipalSubscriptionKey)
    }

    @Test
    fun decodeForDiskRejectsNon12ByteIv() {
        // Both directions of a wrong IV-size header are refused before decrypting.
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        for (ivSize in listOf(11, 13)) {
            val data = ByteArray(1 + ivSize + 32) { 0 }.also { it[0] = ivSize.toByte() }
            assertThrows(IllegalArgumentException::class.java) {
                SecureSettings.decodeForDisk(data) { iv ->
                    Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
                }
            }
        }
    }

    @Test
    fun decodeForDiskRejectsTruncatedAndEmpty() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val truncated = ByteArray(1 + 12 + 16) { 0 }.also { it[0] = 12 } // needs > 12+17 framed bytes
        for (data in listOf(truncated, ByteArray(0))) {
            assertThrows(IllegalArgumentException::class.java) {
                SecureSettings.decodeForDisk(data) { iv ->
                    Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
                }
            }
        }
    }

    @Test
    fun decodeFailureCarriesNoValues() {
        val bytes = "SentinelKey-not-json".toByteArray(Charsets.UTF_8)
        val failure = assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(bytes) }
        assertFalse(failure.message.orEmpty().contains("SentinelKey"))
    }
}
