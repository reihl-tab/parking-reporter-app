package il.sidewalks.reporter.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated wrapper. Neither key material nor plaintext is printable. */
object LedgerEnvelope {
    private val aad = "parking-reporter/ledger-key/v1".toByteArray(Charsets.UTF_8)
    private const val VERSION = 1
    private const val NONCE_BYTES = 12
    private const val DATA_KEY_BYTES = 32
    private const val TAG_BYTES = 16

    fun wrap(dataKey: ByteArray, wrappingKey: SecretKey): ByteArray {
        require(dataKey.size == DATA_KEY_BYTES) { "Invalid ledger key size" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(dataKey)
        require(cipher.iv.size == NONCE_BYTES) { "Invalid ledger nonce" }
        return ByteBuffer.allocate(1 + NONCE_BYTES + encrypted.size)
            .put(VERSION.toByte()).put(cipher.iv).put(encrypted).array()
    }

    fun unwrap(envelope: ByteArray, wrappingKey: SecretKey): ByteArray {
        require(envelope.size == 1 + NONCE_BYTES + DATA_KEY_BYTES + TAG_BYTES &&
            envelope[0].toInt() == VERSION) { "Invalid ledger envelope" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey,
            GCMParameterSpec(128, envelope.copyOfRange(1, 1 + NONCE_BYTES)))
        cipher.updateAAD(aad)
        return cipher.doFinal(envelope.copyOfRange(1 + NONCE_BYTES, envelope.size))
    }

    fun newDataKey(): ByteArray = ByteArray(DATA_KEY_BYTES).also { SecureRandom().nextBytes(it) }
}
