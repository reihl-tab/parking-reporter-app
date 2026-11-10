package il.sidewalks.reporter.evidence

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressSuggestionTest {
    @Test fun reviewUsesStreetAndHouseNotProviderFreeformAddress() {
        assertEquals("רחוב בדיקה 12", AddressSuggestion(" רחוב בדיקה ", " 12 ", "City, postcode, unrelated text").streetAndHouse)
    }
    @Test fun missingHouseIsNeverInvented() {
        assertEquals("רחוב בדיקה", AddressSuggestion("רחוב בדיקה", null, "unverified 99").streetAndHouse)
        assertEquals("רחוב בדיקה", AddressSuggestion("רחוב בדיקה", " ", "").streetAndHouse)
    }
}
