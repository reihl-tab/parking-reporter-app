package il.sidewalks.reporter

import il.sidewalks.reporter.security.LedgerEnvelope
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class LedgerEnvelopeTest {
    private fun wrappingKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected authenticated envelope rejection") } catch (expected: Exception) { }
    }
    @Test fun roundTripPreservesExactKeyAndDoesNotStorePlaintext() {
        val plain = ByteArray(32) { (it + 40).toByte() }
        val wrap = wrappingKey()
        val encoded = LedgerEnvelope.wrap(plain, wrap)
        assertArrayEquals(plain, LedgerEnvelope.unwrap(encoded, wrap))
        assertFalse(encoded.toList().windowed(plain.size).any { it == plain.toList() })
    }
    @Test fun freshNonceChangesEveryEnvelope() {
        val key = wrappingKey(); val data = ByteArray(32)
        assertFalse(LedgerEnvelope.wrap(data, key).contentEquals(LedgerEnvelope.wrap(data, key)))
    }
    @Test fun wrongKeyAndEveryChangedByteAreRejected() {
        val key = wrappingKey(); val encoded = LedgerEnvelope.wrap(ByteArray(32), key)
        rejects { LedgerEnvelope.unwrap(encoded, wrappingKey()) }
        for (i in encoded.indices) {
            val changed = encoded.copyOf(); changed[i] = (changed[i].toInt() xor 1).toByte()
            rejects { LedgerEnvelope.unwrap(changed, key) }
        }
    }
    @Test fun truncatedAndExtendedInputsRejected() {
        val key = wrappingKey(); val encoded = LedgerEnvelope.wrap(ByteArray(32), key)
        for (n in encoded.indices) rejects { LedgerEnvelope.unwrap(encoded.copyOf(n), key) }
        rejects { LedgerEnvelope.unwrap(encoded + byteArrayOf(0), key) }
    }
    @Test fun wrongPlaintextSizeRejected() {
        for (n in listOf(0, 16, 31, 33, 64)) rejects { LedgerEnvelope.wrap(ByteArray(n), wrappingKey()) }
    }
    @Test fun generatedKeysHaveExpectedSizeAndDiffer() {
        val first = LedgerEnvelope.newDataKey(); val second = LedgerEnvelope.newDataKey()
        assertEquals(32, first.size); assertEquals(32, second.size)
        assertFalse(first.contentEquals(second))
    }
}
