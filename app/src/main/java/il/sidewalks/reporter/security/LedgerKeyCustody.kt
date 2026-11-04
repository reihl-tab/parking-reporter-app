package il.sidewalks.reporter.security

import javax.crypto.SecretKey

/** Implementations must hold both cross-instance and OS-process locks for the whole block. */
interface LedgerCustodyStore {
    fun <T> exclusive(block: () -> T): T
    fun hasHistory(): Boolean
    fun readCommitted(): ByteArray?
    fun commitDurably(envelope: ByteArray)
}
interface LedgerWrappingKeys {
    fun authenticate()
    fun existing(): SecretKey?
    fun create(): SecretKey
}

/** No caller-supplied boolean can authorize creating a replacement key for existing data. */
class LedgerKeyCustody(private val store: LedgerCustodyStore, private val keys: LedgerWrappingKeys) {
    fun loadOrCreateEmpty(): ByteArray = store.exclusive {
        keys.authenticate()
        val existing = keys.existing()
        val committed = store.readCommitted()
        if (committed != null) {
            check(existing != null) { "Ledger wrapping key unavailable" }
            try { LedgerEnvelope.unwrap(committed, existing) } finally { committed.fill(0) }
        } else {
            check(!store.hasHistory()) { "Ledger key unavailable" }
            val wrapping = existing ?: keys.create()
            val data = LedgerEnvelope.newDataKey()
            try {
                val envelope = LedgerEnvelope.wrap(data, wrapping)
                try { store.commitDurably(envelope) } finally { envelope.fill(0) }
                val readback = store.readCommitted() ?: error("Ledger custody verification failed")
                val verified = try { LedgerEnvelope.unwrap(readback, wrapping) } finally { readback.fill(0) }
                try { check(java.security.MessageDigest.isEqual(data, verified)) { "Ledger custody verification failed" } }
                finally { verified.fill(0) }
                data
            } catch (failure: Exception) { data.fill(0); throw failure }
        }
    }
    // Legacy migration deliberately has no creation API yet. A validated, separately reviewed
    // migration state/proof will authorize new custody, never this empty-ledger path.
}
