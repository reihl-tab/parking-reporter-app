package il.sidewalks.reporter

import il.sidewalks.reporter.security.*
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class LedgerKeyCustodyTest {
    private class Store : LedgerCustodyStore {
        var bytes: ByteArray? = null; var history = false; var failure: String? = null
        var writes = 0
        override fun <T> exclusive(block: () -> T): T = synchronized(this) { block() }
        override fun hasHistory(): Boolean { check(failure != "history-inspection"); return history }
        override fun readCommitted(): ByteArray? = bytes?.copyOf()?.also {
            if (failure == "readback") it[5] = (it[5].toInt() xor 1).toByte()
        }
        override fun commitDurably(envelope: ByteArray) {
            history = true; writes++
            if (failure in listOf("write", "sync", "rename")) error("Injected custody failure")
            bytes = envelope.copyOf()
            if (failure == "directory-sync") error("Injected custody failure")
        }
    }
    private class Keys : LedgerWrappingKeys {
        var key: SecretKey? = null; var authenticated = true; var invalid = false; var creates = 0
        override fun authenticate() { check(authenticated) }
        override fun existing(): SecretKey? { check(!invalid); return key }
        override fun create(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it; creates++ }
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (expected: Exception) { }
    }
    @Test fun freshCreateAndNewFactoryReopenUseSameCommittedKey() {
        val s = Store(); val k = Keys(); val first = LedgerKeyCustody(s, k).loadOrCreateEmpty()
        assertArrayEquals(first, LedgerKeyCustody(s, k).loadOrCreateEmpty())
        assertEquals(1, s.writes); assertEquals(1, k.creates)
    }
    @Test fun unauthenticatedAndInvalidatedNeverWrite() {
        for (invalid in listOf(false, true)) {
            val s = Store(); val k = Keys().apply { authenticated = invalid; this.invalid = invalid }
            rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }; assertEquals(0, s.writes)
        }
    }
    @Test fun unavailableHistoryInspectionNeverCreatesOrWrites() {
        val s = Store().apply { failure = "history-inspection" }; val k = Keys()
        rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }
        assertEquals(0, k.creates); assertEquals(0, s.writes)
    }
    @Test fun anyExistingDatabaseOrMigrationHistoryRefusesCreation() {
        val s = Store().apply { history = true }; val k = Keys()
        rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }; assertEquals(0, k.creates)
    }
    @Test fun missingAliasAndCorruptWrapperNeverReplace() {
        val s = Store(); val k = Keys(); LedgerKeyCustody(s, k).loadOrCreateEmpty()
        val original = k.key
        k.key = null; rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }; assertEquals(1, k.creates)
        k.key = original
        s.bytes = ByteArray(61); rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }; assertEquals(1, s.writes)
    }
    @Test fun allDurabilityFailuresStopAndInterruptedHistoryNeverResets() {
        for (stage in listOf("write", "sync", "rename", "directory-sync", "readback")) {
            val s = Store().apply { failure = stage }; val k = Keys()
            rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }; assertEquals(1, s.writes)
            if (stage in listOf("write", "sync", "rename")) {
                s.failure = null; rejects { LedgerKeyCustody(s, k).loadOrCreateEmpty() }; assertEquals(1, s.writes)
            }
        }
    }
    @Test fun concurrentFactoriesCommitOnlyOnce() {
        val s = Store(); val k = Keys(); val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..8).map { pool.submit<ByteArray> { LedgerKeyCustody(s, k).loadOrCreateEmpty() } }
            val expected = futures.first().get()
            futures.forEach { assertArrayEquals(expected, it.get()) }; assertEquals(1, s.writes)
        } finally { pool.shutdownNow() }
    }
}
