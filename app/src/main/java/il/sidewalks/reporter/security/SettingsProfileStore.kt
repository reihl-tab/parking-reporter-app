package il.sidewalks.reporter.security

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Plaintext record of WHICH municipal credential fields are unset - field IDs only,
 *  never values - so the review screen can disable send without an unlock-gated
 *  settings read. A missing file means "unknown" (installs from before this record
 *  existed) and keeps the old answer-after-unlock behavior until the next save. */
class SettingsProfileStore(private val file: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "settings-profile.txt"))

    /** Temp file + atomic rename (plain java.nio, so the class stays JVM-testable):
     *  a failed save never leaves a half-written record. */
    fun save(missing: List<String>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(missing.joinToString("\n"))
        try {
            Files.move(tmp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (failure: Exception) {
            tmp.delete()
            throw failure
        }
    }

    /** Remove the record: the review screen falls back to "unknown". */
    fun clear() {
        file.delete()
    }

    fun read(): List<String>? =
        if (!file.isFile) null else file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
}
