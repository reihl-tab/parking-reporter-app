package il.sidewalks.reporter.municipal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** #165: the on-device lookup is refused unless its sha256 is the pinned one. */
class AddressLookupPinTest {
    @Test fun sha256HexMatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AddressLookupPin.sha256Hex("abc"),
        )
    }

    @Test fun mismatchedCopyIsRefused() {
        assertThrows(AddressLookupMismatchException::class.java) {
            AddressLookupPin.verifyPinned("{\"version\": 1, \"streets\": [], \"addresses\": []}")
        }
    }

    @Test fun pinMatchesThePrivateFileWhenPresentLocally() {
        // The lookup is municipality-supplied private data and is NOT
        // committed to this public repo, so this pins the real file only on a
        // developer machine that has it (same pattern as
        // BundledAddressLookupTest).
        val file = File("src/main/assets/addresses.json")
        org.junit.Assume.assumeTrue(
            "private lookup asset not present locally; skipping", file.isFile)
        AddressLookupPin.verifyPinned(file.readText(Charsets.UTF_8))
    }
}
