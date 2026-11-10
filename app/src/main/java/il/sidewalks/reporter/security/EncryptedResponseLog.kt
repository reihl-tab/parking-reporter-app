package il.sidewalks.reporter.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import il.sidewalks.reporter.municipal.ResponseDiagnostic

/** Diagnostics only, no UI/share path. App-private, no backup; AES-GCM key never leaves Keystore. */
class EncryptedResponseLog(context: Context) {
    private val directory = File(context.noBackupFilesDir, "municipal-response-log")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun append(reportId: String, entry: ResponseDiagnostic) = synchronized(LOCK) {
        require(reportId.matches(Regex("[A-Za-z0-9-]+")))
        check(directory.mkdirs() || directory.isDirectory)
        val now = System.currentTimeMillis()
        prune(now)
        val envelope = JSONObject().put("report_id", reportId).put("at", now)
            .put("endpoint", entry.endpoint).put("status", entry.status ?: JSONObject.NULL)
            .put("headers", JSONObject(entry.headers)).put("body", entry.body)
            .put("failure", entry.failure ?: JSONObject.NULL).toString()
        val json = JSONObject(envelope)
        if (envelope.toByteArray(Charsets.UTF_8).size > 80_000) json.put("body", "[body omitted: envelope size limit]")
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        try {
            require(payload.size <= 80_000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val data = SecureSettings.encodeForDisk(payload, cipher)
            val target = File(directory, "$now-${UUID.randomUUID()}.enc")
            val tmp = File(directory, target.name + ".tmp")
            try { tmp.outputStream().use { it.write(data) }; check(tmp.renameTo(target)) }
            finally { tmp.delete() }
            prune(now)
        } finally { payload.fill(0) }
    }
    private fun prune(now: Long) {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        var total = 0L
        files.forEachIndexed { i, file ->
            total += file.length()
            if (i >= 20 || total > 2_000_000 || now - file.lastModified() > 7L * 24 * 60 * 60 * 1000) file.delete()
        }
    }
    companion object { private val LOCK = Any(); private const val ALIAS = "parking-response-diagnostics-v1" }
}
