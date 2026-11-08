package il.sidewalks.reporter.evidence

import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Gallery originals are copied byte-for-byte into the no-backup originals
 * directory; the picked image is never re-encoded. The system photo picker
 * requires no permissions.
 */
object GalleryImport {
    /** Refuse oversized picks: no legitimate evidence photo is near this. */
    const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

    fun copyToOriginals(
        open: () -> InputStream?,
        originals: File,
        name: String = "${UUID.randomUUID()}.jpg",
        maxBytes: Long = MAX_IMPORT_BYTES,
    ): File {
        check(originals.mkdirs() || originals.isDirectory) { "originals directory unavailable" }
        val target = File(originals, name)
        val temp = File(originals, "$name.tmp")
        try {
            requireNotNull(open()) { "gallery image unreadable" }.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw java.io.IOException("image exceeds the import size limit")
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(temp.renameTo(target)) { "gallery import failed" }
            return target
        } finally {
            temp.delete()
        }
    }

    /**
     * Deletes a copied original that never reached the ledger (for example a
     * refused duplicate reserve), so no EXIF-carrying file stays orphaned in
     * originals/. Idempotent: true when the file is gone afterwards.
     */
    fun deleteOrphaned(file: File): Boolean = file.delete() || !file.exists()

    /**
     * Deletes every original the ledger does not reference (for example a
     * copy whose import was interrupted by rotation or a process kill).
     * [referencedPaths] are the ledger rows' relative paths (for example
     * "originals/<uuid>.jpg"); only their file names are compared, so a
     * caller passing a full relative path still protects the file.
     * Files modified after [notAfterMillis] are never touched: a copy still
     * being written (the activity recreated while the system picker is open,
     * or a reserve committing just after the ledger snapshot) is always
     * newer than the cutoff, while a copy stranded by an earlier rotation
     * or kill is removed at a later start.
     * Returns the number of files removed.
     */
    fun sweepUnreferenced(
        originals: File,
        referencedPaths: Set<String>,
        notAfterMillis: Long = System.currentTimeMillis() - IN_FLIGHT_AGE_MILLIS,
    ): Int {
        val referencedNames = referencedPaths.map { File(it).name }.toSet()
        val files = originals.listFiles() ?: return 0
        var removed = 0
        for (file in files) {
            if (file.isFile && file.name !in referencedNames &&
                file.lastModified() <= notAfterMillis && file.delete()) removed++
        }
        return removed
    }

    /** A file newer than this may still be written by an in-flight import. */
    const val IN_FLIGHT_AGE_MILLIS = 60_000L
}
