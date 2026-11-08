package il.sidewalks.reporter.evidence

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GalleryImportTest {
    @Test fun byteExactCopy() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        val source = ByteArray(10240) { (it * 31 % 251).toByte() }
        val out = GalleryImport.copyToOriginals({ source.inputStream() }, dir, "picked.jpg")
        assertEquals("picked.jpg", out.name)
        assertArrayEquals(source, out.readBytes())
    }
    @Test fun unreadableImageFailsAndLeavesNoFiles() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        try {
            GalleryImport.copyToOriginals({ null }, dir)
            fail("expected failure")
        } catch (_: Exception) {}
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }
    @Test fun defaultNameIsUniqueJpg() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        val a = GalleryImport.copyToOriginals({ "x".byteInputStream() }, dir)
        val b = GalleryImport.copyToOriginals({ "x".byteInputStream() }, dir)
        assertNotEquals(a.name, b.name)
        assertTrue(a.name.endsWith(".jpg"))
    }
    @Test fun deleteOrphanedRemovesUnreservedCopy() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        val out = GalleryImport.copyToOriginals({ "orphan".byteInputStream() }, dir)
        assertTrue(out.exists())
        assertTrue(GalleryImport.deleteOrphaned(out))
        assertFalse(out.exists())
        assertTrue(GalleryImport.deleteOrphaned(out))
    }
    @Test fun failedCopyLeavesNoPartialFiles() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        val broken = object : java.io.InputStream() {
            private var sent = 0
            override fun read(): Int = if (sent++ < 100) 65 else throw java.io.IOException("synthetic mid-copy failure")
        }
        try {
            GalleryImport.copyToOriginals({ broken }, dir)
            fail("expected failure")
        } catch (_: java.io.IOException) {}
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }
    @Test fun sweepUnreferencedRemovesOnlyUnreferenced() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-sweep").toFile()
        val keep = GalleryImport.copyToOriginals({ "keep".byteInputStream() }, dir, "keep.jpg")
        val drop = GalleryImport.copyToOriginals({ "drop".byteInputStream() }, dir, "drop.jpg")
        assertEquals(1, GalleryImport.sweepUnreferenced(dir, setOf("originals/keep.jpg"), Long.MAX_VALUE))
        assertTrue(keep.exists())
        assertFalse(drop.exists())
        assertEquals(0, GalleryImport.sweepUnreferenced(dir, setOf("originals/keep.jpg"), Long.MAX_VALUE))
    }
    @Test fun sweepUnreferencedMatchesRelativePathsByFileName() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-sweep").toFile()
        val keep = GalleryImport.copyToOriginals({ "keep".byteInputStream() }, dir, "a1b2.jpg")
        // A caller passing the ledger's relative path protects the file; a
        // full-path bug here would delete every saved report's original.
        assertEquals(0, GalleryImport.sweepUnreferenced(dir, setOf("originals/a1b2.jpg"), Long.MAX_VALUE))
        assertTrue(keep.exists())
    }
    @Test fun sweepUnreferencedRemovesStaleTemp() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-sweep").toFile()
        val keep = GalleryImport.copyToOriginals({ "keep".byteInputStream() }, dir, "keep.jpg")
        val stale = File(dir, "keep.jpg.tmp").apply { writeBytes("partial".toByteArray()) }
        assertTrue(stale.setLastModified(System.currentTimeMillis() - 120_000))
        assertEquals(1, GalleryImport.sweepUnreferenced(dir, setOf("originals/keep.jpg")))
        assertTrue(keep.exists())
        assertFalse(stale.exists())
    }
    @Test fun sweepUnreferencedSkipsInFlightImport() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-sweep").toFile()
        // A slow stream with the sweep running mid-copy: the sweep sees the
        // .tmp (or the just-renamed copy) with no ledger row, but the age
        // guard keeps it. Regression test for the startup-sweep race: the
        // activity recreated while the system picker is open delivers the
        // picker result in the same first composition as the sweep.
        val gate = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val slow = object : java.io.InputStream() {
            private var sent = 0
            override fun read(): Int {
                if (sent == 0) { gate.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS) }
                return if (sent++ < 100) 65 else -1
            }
        }
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val copier = Thread {
            try {
                GalleryImport.copyToOriginals({ slow }, dir, "inflight.jpg")
            } catch (t: Throwable) { errors.add(t) }
        }
        copier.start()
        assertTrue(gate.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(0, GalleryImport.sweepUnreferenced(dir, emptySet()))
        release.countDown()
        copier.join(5000)
        assertTrue("copy failed: $errors", errors.isEmpty())
        val saved = File(dir, "inflight.jpg")
        assertTrue(saved.exists())
        assertEquals(100, saved.readBytes().size)
    }
    @Test fun oversizedImportRefusedAndLeavesNoFiles() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        try {
            GalleryImport.copyToOriginals({ ByteArray(200) { 7 }.inputStream() }, dir, maxBytes = 100)
            fail("expected failure")
        } catch (_: java.io.IOException) {}
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }
    @Test fun exactLimitImportSucceeds() {
        val dir = java.nio.file.Files.createTempDirectory("gallery-import").toFile()
        val out = GalleryImport.copyToOriginals({ ByteArray(100) { 7 }.inputStream() }, dir, maxBytes = 100)
        assertEquals(100, out.readBytes().size)
    }
}
