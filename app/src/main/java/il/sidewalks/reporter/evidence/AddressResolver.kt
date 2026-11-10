package il.sidewalks.reporter.evidence

import android.content.Context
import android.location.Geocoder
import java.util.Locale

data class AddressSuggestion(val street: String, val house: String?, val display: String) {
    /** Do not pass a provider's city/postcode as the confirmed street address. */
    val streetAndHouse: String get() = listOfNotNull(street.trim().takeIf { it.isNotEmpty() }, house?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")
}
/** Suggestions are not evidence or approval. House/street always require owner confirmation. */
class AddressResolver(context: Context) {
    private val geocoder = Geocoder(context, Locale.forLanguageTag("he"))
    @Suppress("DEPRECATION")
    fun suggest(latitude: Double, longitude: Double): List<AddressSuggestion> {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        if (!Geocoder.isPresent()) return emptyList()
        return try {
            geocoder.getFromLocation(latitude, longitude, 5).orEmpty().mapNotNull { address ->
                address.thoroughfare?.let { AddressSuggestion(it, address.subThoroughfare, address.getAddressLine(0).orEmpty()) }
            }.distinct()
        } catch (_: Exception) { emptyList() }
    }
}
