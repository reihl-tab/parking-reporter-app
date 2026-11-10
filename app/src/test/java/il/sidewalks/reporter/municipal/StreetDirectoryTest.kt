package il.sidewalks.reporter.municipal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetDirectoryTest {

    /** Tiny synthetic lookup in the pipeline's version-1 shape. */
    private fun fixture(): StreetDirectory {
        // Two streets, one with a Hebrew-final-letter name; address points
        // near a known ITM location (pyproj: 31.7683, 35.2137 -> 220333, 630626).
        val json = """
        {"version": 1,
         "streets": [
           {"StreetCode": "1001", "StreetName": "רחוב הנביאים", "CityName": "ירושלים", "CityCode": "3000"},
           {"StreetCode": "1002", "StreetName": "שדרות בן צבי", "StreetArabicName": "شارع بن تسفي", "aliases": ["בן-צבי"]}
         ],
         "addresses": [
           [31.7683, 35.2137, "12", "רחוב הנביאים"],
           [31.7686, 35.2142, "14", "רחוב הנביאים"],
           [31.77, 35.214, "5", "שדרות בן צבי"],
           [31.90, 35.30, "9", "רחוב הנביאים"]
         ]}
        """.trimIndent()
        return StreetDirectory.load(json)
    }

    @Test fun exactAddressSuppliesSeparateConfirmedLocationWithoutPhotoGps() {
        val location = fixture().findConfirmedAddress("רחוב הנביאים", "12")
        assertEquals("רחוב הנביאים 12", location.address)
        assertEquals(31.7683, location.latitude, 0.0)
        assertEquals(35.2137, location.longitude, 0.0)
        val evidence = il.sidewalks.reporter.core.Evidence("manual-fixture", "a".repeat(64), "2026-01-01T12:00:00+02:00", null, null)
        val draft = il.sidewalks.reporter.core.ReviewDraft(evidence, "1234567", location.address, "sidewalk_parking", "synthetic", emptySet(),
            confirmedLatitude = location.latitude, confirmedLongitude = location.longitude)
        val approved = draft.approve()
        assertEquals(null, approved.evidence.latitude)
        assertTrue(approved.isApproved())
        assertTrue(!approved.copy(confirmedLatitude = 31.77).isApproved())
        val edited = approved.edit(address = "another address")
        assertTrue(!edited.isApproved())
        assertEquals(null, edited.confirmedLatitude)
        assertEquals(null, edited.confirmedLongitude)
        assertTrue(!edited.isReady())
        assertTrue("location_needs_confirmation" in edited.unresolved)
    }

    @Test fun missingOrAmbiguousAddressCannotSupplyConfirmedLocation() {
        for ((street, house) in listOf("unknown" to "1", "רחוב הנביאים" to "999")) {
            try { fixture().findConfirmedAddress(street, house); org.junit.Assert.fail("must refuse") }
            catch (_: MunicipalInputException) { }
        }
        val ambiguous = StreetDirectory.load("""{"version":1,"streets":[{"StreetCode":"1001","StreetName":"רחוב בדיקה"}],"addresses":[[31.77,35.21,"12","רחוב בדיקה"],[31.7701,35.21,"12","רחוב בדיקה"]]}""")
        try { ambiguous.findConfirmedAddress("רחוב בדיקה", "12"); org.junit.Assert.fail("must refuse") }
        catch (_: MunicipalInputException) { }
    }

    @Test fun uniqueStreetNameResolves() {
        val resolved = fixture().resolve("רחוב הנביאים", "12", null)
        assertEquals("1001", resolved.streetCode)
        assertEquals("12", resolved.house)
        assertEquals("address_lookup", resolved.coordinateSource)
    }

    @Test fun finalLettersNormalize() {
        // "בן צבי" with a regular tsadi vs the alias "בן-צבי" with final tsadi.
        val resolved = fixture().resolve("בן-צבי", "5", null)
        assertEquals("1002", resolved.streetCode)
    }

    @Test fun hebrewLetterHouseNumberResolves() {
        // House numbers like 12א exist in the bundled lookup; ASCII \w/\d
        // must not drop or refuse them (JVM \w is ASCII-only).
        val json = """
        {"version": 1,
         "streets": [{"StreetCode": "1001", "StreetName": "הנביאים"}],
         "addresses": [[31.7683, 35.2137, "12א", "הנביאים"]]}
        """.trimIndent()
        val resolved = StreetDirectory.load(json).resolve("הנביאים", "12א", null)
        assertEquals("1001", resolved.streetCode)
        assertEquals("12א", resolved.house)
    }

    @Test fun unknownStreetRefused() {
        try {
            fixture().resolve("רחוב שאינו קיים", "1", null)
            org.junit.Assert.fail("unknown street must throw")
        } catch (e: MunicipalInputException) {
            assertTrue(e.message!!.contains("no unique municipal match"))
        }
    }

    @Test fun addressFarFromGpsRefused() {
        // House 9 sits ~15 km away in the fixture; GPS near 31.7683, 35.2137.
        try {
            fixture().resolve("רחוב הנביאים", "9", 31.7683 to 35.2137)
            org.junit.Assert.fail("distant house must throw")
        } catch (e: MunicipalInputException) {
            assertTrue(e.message!!.contains("200 m"))
        }
    }

    @Test fun gpsPicksNearestClearAddress() {
        val resolved = fixture().resolve(null, null, 31.7683 to 35.21371)
        assertEquals("1001", resolved.streetCode)
        assertEquals("12", resolved.house)
        assertEquals("photo_gps", resolved.coordinateSource)
    }

    @Test fun missingGpsAndAddressRefused() {
        try {
            fixture().resolve(null, null, null)
            org.junit.Assert.fail("no address and no GPS must throw")
        } catch (e: MunicipalInputException) {
            assertTrue(e.message!!.contains("GPS"))
        }
    }

    @Test fun houseNumberAbsentWithoutGpsRefused() {
        try {
            fixture().resolve("רחוב הנביאים", "999", null)
            org.junit.Assert.fail("absent house must throw")
        } catch (e: MunicipalInputException) {
            assertTrue(e.message!!.contains("absent"))
        }
    }
}
