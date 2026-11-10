package il.sidewalks.reporter.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ReporterSettings(
    val firstName: String, val lastName: String, val phone: String, val idNumber: String, val email: String,
    val municipalSubscriptionKey: String = "", val municipalUsername: String = "", val municipalPassword: String = "",
    val passportMode: Boolean = false, val passportNumber: String = "",
) {
    fun hasMunicipalCredentials(): Boolean =
        municipalSubscriptionKey.isNotBlank() && municipalUsername.isNotBlank() && municipalPassword.isNotBlank()
    /** IDs of unset municipal API credential fields (never the values). */
    fun missingMunicipalFields(): List<String> = listOfNotNull(
        "subscription_key".takeIf { municipalSubscriptionKey.isBlank() },
        "api_username".takeIf { municipalUsername.isBlank() },
        "api_password".takeIf { municipalPassword.isBlank() },
    )
    override fun toString(): String = "ReporterSettings(redacted)"
}
/** Requires recent system device-credential authentication for every encryption/decryption. */
class SecureSettings(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "reporter-settings.enc"))
    private val alias = "parking-reporter-settings-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setUserAuthenticationValidityDurationSeconds(30)
                .build())
            generateKey()
        }
    }
    fun save(settings: ReporterSettings) {
        val plaintext = SettingsCodec.encode(settings)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val diskBytes = encodeForDisk(plaintext, cipher)
            val stream = file.startWrite()
            try {
                stream.write(diskBytes)
                file.finishWrite(stream)
            } catch (failure: Exception) { file.failWrite(stream); throw failure }
        } finally { plaintext.fill(0) }
    }
    fun read(): ReporterSettings? {
        if (!file.baseFile.exists()) return null
        val plaintext = decodeForDisk(file.readFully()) { iv ->
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
        }
        try {
            return SettingsCodec.decode(plaintext)
        } finally { plaintext.fill(0) }
    }
    // No plaintext fallback, no automatic key reset on invalidation.

    companion object {
        /** Framed as iv-length byte + IV + AES-GCM ciphertext. Unit-tested with a non-Keystore key. */
        internal fun encodeForDisk(plaintext: ByteArray, cipher: Cipher): ByteArray {
            val encrypted = cipher.doFinal(plaintext)
            val out = ByteArray(1 + cipher.iv.size + encrypted.size)
            out[0] = cipher.iv.size.toByte()
            System.arraycopy(cipher.iv, 0, out, 1, cipher.iv.size)
            System.arraycopy(encrypted, 0, out, 1 + cipher.iv.size, encrypted.size)
            return out
        }
        internal fun decodeForDisk(data: ByteArray, cipherFor: (ByteArray) -> Cipher): ByteArray {
            val size = data.firstOrNull()?.toInt()?.and(255) ?: throw IllegalArgumentException("Invalid encrypted settings")
            require(size == 12 && data.size > size + 17) { "Invalid encrypted settings" }
            val cipher = cipherFor(data.copyOfRange(1, size + 1))
            return cipher.doFinal(data.copyOfRange(size + 1, data.size))
        }
    }
}
