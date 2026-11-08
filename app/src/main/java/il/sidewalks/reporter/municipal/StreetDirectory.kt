package il.sidewalks.reporter.municipal

import java.text.Normalizer
import kotlin.math.hypot

/**
 * Jerusalem street/address resolution, a faithful port of the helper
 * pipeline's api106.Addresses (helper 0.6.0, pinned 51dd5c0): same guards,
 * same distances, same error semantics, so an address the app accepts is an
 * address the pipeline would have accepted. Data is the pipeline's own
 * version-1 lookup file (streets + address points), bundled as an asset.
 */
class StreetDirectory private constructor(
    private val streets: Map<String, String>,
    private val aliases: Map<String, Set<String>>,
    private val points: List<Point>,
) {
    private data class Point(val x: Double, val y: Double, val house: String, val code: String?, val name: String)

    data class Resolved(
        val streetCode: String,
        val streetName: String,
        val house: String,
        val x: Int,
        val y: Int,
        val coordinateSource: String,
    )

    private fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .map { HEBREW_FINALS[it] ?: it }
        return folded.filter { it.isLetterOrDigit() }.joinToString("")
    }

    /**
     * Resolves like the helper: an explicit street+house wins when it
     * uniquely matches; otherwise the photo GPS picks the nearest clear
     * address. Every ambiguous or distant case throws MunicipalInputException
     * with the helper's guidance instead of guessing.
     */
    fun resolve(streetName: String?, house: String?, gps: Pair<Double, Double>?): Resolved {
        val gpsXy = gps?.let { (lat, lon) ->
            val (x, y) = Itm.toItm(lat, lon)
            x.toDouble() to y.toDouble()
        }
        var code: String
        var resolvedHouse: String
        var x: Double
        var y: Double
        var source: String
        if (!streetName.isNullOrBlank()) {
            val codes = aliases[normalize(streetName)] ?: emptySet()
            if (codes.size != 1) {
                throw MunicipalInputException(
                    "Street name has no unique municipal match. Use the exact Hebrew street name.")
            }
            code = codes.single()
            resolvedHouse = house?.trim() ?: ""
            if (!Regex("[0-9]+[\\p{L}\\p{N} /\\-]*").matches(resolvedHouse) || resolvedHouse.length > 16) {
                throw MunicipalInputException("Invalid house number.")
            }
            val housePoints = points.filter { it.code == code && normalize(it.house) == normalize(resolvedHouse) }
            if (gpsXy != null) {
                x = gpsXy.first; y = gpsXy.second
                if (housePoints.isNotEmpty() &&
                    housePoints.minOf { p -> hypot(p.x - x, p.y - y) } > 200) {
                    throw MunicipalInputException(
                        "The supplied address is more than 200 m from the photo GPS. Check the address.")
                }
                if (housePoints.isEmpty()) {
                    val streetPoints = points.filter { it.code == code }
                    if (streetPoints.isNotEmpty() &&
                        streetPoints.minOf { p -> hypot(p.x - x, p.y - y) } > 200) {
                        throw MunicipalInputException(
                            "The supplied street is more than 200 m from the photo GPS. Check the street.")
                    }
                }
                source = "photo_gps"
            } else {
                if (housePoints.isEmpty()) {
                    throw MunicipalInputException(
                        "Photo has no usable GPS and this house number is absent from the address data.")
                }
                x = housePoints[0].x; y = housePoints[0].y
                if (housePoints.any { p -> hypot(p.x - x, p.y - y) > 100 }) {
                    throw MunicipalInputException(
                        "This address has multiple locations. Use a photo with GPS.")
                }
                source = "address_lookup"
            }
        } else {
            if (gpsXy == null) {
                throw MunicipalInputException(
                    "Photo has no usable GPS. Supply an address.")
            }
            x = gpsXy.first; y = gpsXy.second
            val nearby = mutableListOf<Triple<Double, Point, String>>()
            val seen = mutableSetOf<Pair<String, String>>()
            for (p in points.sortedBy { p -> (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) }) {
                val distance = hypot(p.x - x, p.y - y)
                if (distance > 150) break
                val identity = (p.code ?: normalize(p.name)) to p.house
                if (identity in seen) continue
                seen.add(identity)
                nearby.add(Triple(distance, p, p.name))
                if (nearby.size == 5) break
            }
            if (nearby.isEmpty() || nearby[0].second.code == null || nearby[0].first > 35 ||
                (nearby.size > 1 && nearby[1].first - nearby[0].first < 8)) {
                val choices = nearby.joinToString("; ") { (d, p, name) ->
                    "${p.code?.let { streets[it] } ?: name} ${p.house} (${d.toInt()} m)" }
                throw MunicipalInputException(
                    "GPS does not identify one clear address. Supply an address. Nearby: " +
                        choices.ifEmpty { "none" })
            }
            code = nearby[0].second.code!!
            resolvedHouse = nearby[0].second.house
            source = "photo_gps"
        }
        return Resolved(code, streets[code]!!, resolvedHouse, x.toInt(), y.toInt(), source)
    }

    companion object {
        private val HEBREW_FINALS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

        private fun inJerusalem(lat: Double, lon: Double): Boolean =
            lat in 31.65..31.95 && lon in 35.05..35.35

        /** Loads the pipeline's version-1 lookup JSON (streets + addresses). */
        @Suppress("UNCHECKED_CAST")
        fun load(json: String): StreetDirectory {
            val data = PythonJson.loads(json) as? Map<String, Any?>
                ?: throw MunicipalInputException("Unsupported address lookup format.")
            if (data["version"] != 1L) throw MunicipalInputException("Unsupported address lookup format.")
            val streetRows = data["streets"] as? List<Any?>
                ?: throw MunicipalInputException("Unsupported address lookup format.")
            val streets = LinkedHashMap<String, String>()
            val aliases = LinkedHashMap<String, MutableSet<String>>()
            for (row in streetRows) {
                val street = row as? Map<String, Any?> ?: continue
                val code = (street["StreetCode"] as? Any)?.toString() ?: continue
                val name = street["StreetName"] as? String ?: continue
                streets[code] = name
                val names = mutableListOf<String>()
                (street["StreetName"] as? String)?.let { names.add(it) }
                (street["StreetArabicName"] as? String)?.let { names.add(it) }
                (street["aliases"] as? List<Any?>)?.filterIsInstance<String>()?.let { names.addAll(it) }
                for (n in names) {
                    val key = StreetDirectoryHolder.normalizeStatic(n)
                    if (key.isNotEmpty()) aliases.getOrPut(key) { mutableSetOf() }.add(code)
                }
            }
            val points = mutableListOf<Point>()
            val addressRows = data["addresses"] as? List<Any?> ?: emptyList()
            for (row in addressRows) {
                val entry = row as? List<Any?> ?: continue
                if (entry.size < 4) continue
                val lat = (entry[0] as? Number)?.toDouble() ?: continue
                val lon = (entry[1] as? Number)?.toDouble() ?: continue
                val house = entry[2]?.toString() ?: continue
                val name = entry[3]?.toString() ?: continue
                if (!Regex("\\p{Nd}+[\\p{L}\\p{N} /\\-]*").matches(house)) continue
                if (!inJerusalem(lat, lon)) continue
                val (x, y) = Itm.toItm(lat, lon)
                val codes = aliases[StreetDirectoryHolder.normalizeStatic(name)] ?: emptySet()
                points.add(Point(x.toDouble(), y.toDouble(), house, codes.singleOrNull(), name))
            }
            return StreetDirectory(streets, aliases, points)
        }
    }
}

private object StreetDirectoryHolder {
    private val HEBREW_FINALS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')
    fun normalizeStatic(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .map { HEBREW_FINALS[it] ?: it }
        return folded.filter { it.isLetterOrDigit() }.joinToString("")
    }
}
