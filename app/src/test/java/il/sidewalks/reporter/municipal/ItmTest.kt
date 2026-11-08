package il.sidewalks.reporter.municipal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden values generated with pyproj Transformer EPSG:4326 -> EPSG:2039
 * (the exact transform the helper pipeline uses), then int()-truncated.
 */
class ItmTest {
    @Test fun goldenPointsMatchPyprojExactly() {
        val golden = listOf(
            Triple(31.7683, 35.2137, Pair(220333, 630626)),
            Triple(31.732972, 35.155405, Pair(214809, 626709)),
            Triple(31.7515, 35.1905, Pair(218135, 628763)),
            Triple(31.7964, 35.223, Pair(221213, 633742)),
            Triple(32.0, 35.0, Pair(200137, 656336)),
        )
        for ((lat, lon, expected) in golden) {
            val (x, y) = Itm.toItm(lat, lon)
            assertEquals("easting off for ($lat,$lon)", expected.first, x)
            assertEquals("northing off for ($lat,$lon)", expected.second, y)
        }
    }

    @Test fun invalidInputRefused() {
        for (bad in listOf(Double.NaN to 35.0, 95.0 to 35.0, 31.0 to 200.0)) {
            try {
                Itm.toItm(bad.first, bad.second)
                org.junit.Assert.fail("accepted $bad")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun truncationMatchesHelperCast() {
        // int() in Python truncates toward zero, like Kotlin Double.toInt().
        assertEquals(220333, Itm.toItm(31.7683, 35.2137).first)
    }
}
