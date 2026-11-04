package il.sidewalks.reporter.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** No UI integration yet. Empty-ledger creation is distinct from later legacy migration. */
class LedgerKeyStore(context: Context) {
    private val app = context.applicationContext
    private val custody = LedgerKeyCustody(AndroidStore(app), AndroidKeys(app))
    fun loadOrCreateEmpty(): ByteArray = custody.loadOrCreateEmpty()

    private class AndroidKeys(private val app: Context) : LedgerWrappingKeys {
        private val alias = "parking-reporter-ledger-wrap-v1"
        override fun authenticate() {
            val guard = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            check(guard.isDeviceSecure && !guard.isDeviceLocked) { "Ledger authentication required" }
        }
        override fun existing(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
            load(null); getKey(alias, null) as? SecretKey
        }
        override fun create(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= 30) {
                spec.setUserAuthenticationParameters(30, KeyProperties.AUTH_DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                spec.setUserAuthenticationValidityDurationSeconds(30)
            }
            init(spec.build()); generateKey()
        }
    }
    private class AndroidStore(private val app: Context) : LedgerCustodyStore {
        private val committed = File(app.noBackupFilesDir, "report-ledger-key.enc")
        private val pending = File(app.noBackupFilesDir, "report-ledger-key.pending")
        override fun <T> exclusive(block: () -> T): T = synchronized(processLock) {
            RandomAccessFile(File(app.noBackupFilesDir, "report-ledger-key.lock"), "rw").use { file ->
                file.channel.lock().use { block() }
            }
        }
        override fun hasHistory(): Boolean {
            val files = app.noBackupFilesDir.listFiles() ?: error("Ledger history unavailable")
            return pending.exists() || File(committed.path + ".bak").exists() ||
                app.databaseList().any { it.startsWith("report-ledger") } ||
                files.any { it.name.startsWith("report-ledger-migration") }
        }
        override fun readCommitted(): ByteArray? {
            if (!committed.exists()) return null
            // Interrupted files are not silently promoted or used as authority to reset.
            check(!pending.exists() && !File(committed.path + ".bak").exists()) { "Ledger custody recovery required" }
            syncDirectory()
            return committed.inputStream().use { input ->
                val bytes = ByteArray(61)
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    check(count > 0) { "Invalid ledger envelope" }; offset += count
                }
                check(input.read() == -1) { "Invalid ledger envelope" }; bytes
            }
        }
        override fun commitDurably(envelope: ByteArray) {
            check(!committed.exists() && pending.createNewFile()) { "Ledger custody history exists" }
            FileOutputStream(pending).use { stream -> stream.write(envelope); stream.fd.sync() }
            Os.rename(pending.path, committed.path) // throws on failure, unlike AtomicFile logging
            syncDirectory()
        }
        private fun syncDirectory() {
            check(app.noBackupFilesDir.isDirectory) { "Ledger custody directory unavailable" }
            val directory = Os.open(app.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
        }
        companion object { private val processLock = Any() }
    }
}
