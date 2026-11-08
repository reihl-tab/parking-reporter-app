package il.sidewalks.reporter.municipal

import java.util.concurrent.atomic.AtomicBoolean

/** Test-double store: memory-only, with a real single-flight claim. */
class InMemorySubmissionStateStore : SubmissionStateStore {
    var stored: MutableMap<String, Any?>? = null
    val writes = mutableListOf<String>()
    private val held = AtomicBoolean(false)

    override fun read(): MutableMap<String, Any?>? = stored?.let { LinkedHashMap(it) }

    override fun write(state: Map<String, Any?>) {
        writes.add(state["status"] as? String ?: "?")
        stored = LinkedHashMap(state)
    }

    override fun <T> withSubmissionLock(block: () -> T): T {
        if (!held.compareAndSet(false, true)) {
            throw MunicipalReviewNeededException(
                "Another submission for this report is already in flight. Wait for it to finish.")
        }
        try {
            return block()
        } finally {
            held.set(false)
        }
    }
}
