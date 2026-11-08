package il.sidewalks.reporter.municipal

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the dev-only bundled lookup asset when a developer places it locally:
 * it must be the pipeline's version-1 file and resolve a well-known
 * Jerusalem street. The lookup is municipality-supplied private data and is
 * NOT committed to this public repo, so the test skips when the file is
 * absent (e.g. in CI).
 */
class BundledAddressLookupTest {
    @Test fun bundledLookupLoadsAndResolves() {
        val file = File("src/main/assets/addresses.json")
        org.junit.Assume.assumeTrue(
            "private lookup asset not present locally; skipping", file.isFile)
        val directory = StreetDirectory.load(file.readText(Charsets.UTF_8))
        val resolved = directory.resolve("אגריפס", "12", null)
        org.junit.Assert.assertEquals("613", resolved.streetCode)
        org.junit.Assert.assertEquals("אגריפס", resolved.streetName)
        org.junit.Assert.assertEquals("12", resolved.house)
        org.junit.Assert.assertEquals("address_lookup", resolved.coordinateSource)
        // pyproj EPSG:2039 for the pipeline's own lookup point.
        assertTrue(kotlin.math.abs(resolved.x - 220579) <= 1)
        assertTrue(kotlin.math.abs(resolved.y - 632269) <= 1)
    }
}
