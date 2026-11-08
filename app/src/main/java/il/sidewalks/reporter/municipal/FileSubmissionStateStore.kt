package il.sidewalks.reporter.municipal

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable on-device SubmissionStateStore: one JSON state file per report,
 * written atomically (tmp + rename) so an interrupted write can never leave
 * a half-written state the machine would refuse on. Files live under the
 * app's no-backup files dir, next to the originals they belong to; deleting
 * a report's state file is never an automatic action.
 *
 * Fail-closed by contract: a read that finds unreadable or wrongly shaped
 * data throws, so the state machine refuses instead of resubmitting.
 */
class FileSubmissionStateStore(private val file: File) : SubmissionStateStore {

    override fun read(): MutableMap<String, Any?>? {
        if (!file.exists()) return null
        check(file.isFile) { "Submission state path is not a file." }
        val parsed = PythonJson.loads(file.readText(Charsets.UTF_8))
        check(parsed is MutableMap<*, *>) { "Submission state is not an object." }
        @Suppress("UNCHECKED_CAST")
        return parsed as MutableMap<String, Any?>
    }

    override fun write(state: Map<String, Any?>) {
        val dir = file.parentFile ?: throw MunicipalInputException("State file has no directory.")
        check(dir.mkdirs() || dir.isDirectory) { "Submission state directory unavailable." }
        val tmp = File(dir, file.name + ".tmp")
        try {
            java.io.FileOutputStream(tmp).use { out ->
                out.write(PythonJson.writeBytes(state))
                // Flush AND fsync the content before the rename: without this
                // a power loss can resurrect the old file after renameTo.
                out.fd.sync()
            }
            check(tmp.renameTo(file)) { "Submission state persist failed." }
            // Best-effort directory fsync so the rename itself is durable.
            // Not supported on every filesystem - failure must not fail the write.
            try {
                java.nio.channels.FileChannel.open(
                    dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
            } catch (_: Exception) {
            }
        } finally {
            if (tmp.exists() && !tmp.delete()) tmp.deleteOnExit()
        }
    }

    override fun <T> withSubmissionLock(block: () -> T): T {
        // One claim per state file, process-wide: two FileSubmissionStateStore
        // instances for the same report share it. A second submit for the same
        // report while one is in flight is refused, never raced. AtomicBoolean,
        // not ReentrantLock: a lock would re-acquire on the SAME thread, but a
        // nested claim for the same report is exactly what must be refused.
        val claim = LOCKS.computeIfAbsent(file.canonicalFile.absolutePath) { AtomicBoolean() }
        if (!claim.compareAndSet(false, true)) {
            throw MunicipalReviewNeededException(
                "Another submission for this report is already in flight. Wait for it to finish.")
        }
        try {
            return block()
        } finally {
            claim.set(false)
        }
    }

    companion object {
        private val LOCKS = ConcurrentHashMap<String, AtomicBoolean>()

        /** The state file for one report, keyed by the ledger's stable id. */
        fun forReport(submissionsDir: File, stableId: String): FileSubmissionStateStore {
            require(stableId.matches(Regex("[A-Za-z0-9\\-]+"))) { "Invalid report id." }
            return FileSubmissionStateStore(File(submissionsDir, "$stableId.json"))
        }
    }
}
