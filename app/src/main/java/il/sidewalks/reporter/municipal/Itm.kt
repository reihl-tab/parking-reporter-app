package il.sidewalks.reporter.municipal

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * WGS84 -> Israeli Transverse Mercator (EPSG:2039), the CRS the municipal
 * CreateCase payload carries in CoordinateX/CoordinateY. Reproduces the
 * helper pipeline's pyproj 4326 -> 2039 transform exactly (to well under a
 * meter): pyproj applies "Inverse of Israel 1993 to WGS 84 (2)" - a 7-param
 * Coordinate Frame Helmert (EPSG method 1032) - before the TM projection,
 * so a pure Snyder series on the raw WGS84 input lands ~66 m east and
 * ~41 m north of the pipeline's coordinates. The Helmert stage below is the
 * numerically equal inverse (translation applied before rotation).
 * Golden values in ItmTest are pyproj-generated and pinned to the meter.
 */
object Itm {
    // GRS80 (the Israel 1993 ellipsoid) and TM grid parameters.
    private const val A = 6378137.0               // GRS80 semi-major axis
    private const val INV_F = 298.257222101       // GRS80 flattening
    private const val LAT0 = 31.73439361111111    // grid origin latitude
    private const val LON0 = 35.20451694444445    // grid origin longitude
    private const val K0 = 1.0000067              // scale factor at origin
    private const val FALSE_E = 219529.584
    private const val FALSE_N = 626907.39

    // WGS84 ellipsoid, for the geodetic->geocentric stage of the source CRS.
    private const val INV_F_WGS84 = 298.257223563

    // "Israel 1993 to WGS 84 (2)" (EPSG:1241), Coordinate Frame rotation.
    // Applied here in inverse (WGS84 -> Israel 1993): subtract the
    // translation, then apply the forward rotation and scale - numerically
    // equal to PROJ's negated-parameter inverse at this magnitude.
    private const val DX = 23.772
    private const val DY = 17.49
    private const val DZ = 17.859
    private val RX = Math.toRadians(0.3132 / 3600.0)
    private val RY = Math.toRadians(1.85274 / 3600.0)
    private val RZ = Math.toRadians(-1.67299 / 3600.0)
    private const val DS = -5.4262e-6

    private val F = 1.0 / INV_F
    private val E2 = F * (2 - F)
    private val E4 = E2 * E2
    private val E6 = E4 * E2
    private val EP2 = E2 / (1 - E2)
    private val F_WGS84 = 1.0 / INV_F_WGS84
    private val E2_WGS84 = F_WGS84 * (2 - F_WGS84)

    private fun meridionalArc(latRad: Double): Double {
        return A * ((1 - E2 / 4 - 3 * E4 / 64 - 5 * E6 / 256) * latRad -
            (3 * E2 / 8 + 3 * E4 / 32 + 45 * E6 / 1024) * sin(2 * latRad) +
            (15 * E4 / 256 + 45 * E6 / 1024) * sin(4 * latRad) -
            (35 * E6 / 3072) * sin(6 * latRad))
    }

    /** WGS84 geodetic (h=0) -> Israel 1993 geodetic, via geocentric Helmert. */
    private fun wgs84ToIsrael1993(latRad: Double, lonRad: Double): Pair<Double, Double> {
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val n = A / sqrt(1 - E2_WGS84 * sinLat * sinLat)
        var x = n * cosLat * cos(lonRad)
        var y = n * cosLat * sin(lonRad)
        var z = n * (1 - E2_WGS84) * sinLat
        x -= DX
        y -= DY
        z -= DZ
        val m = 1 / (1 + DS)
        val x2 = m * (x - RZ * y + RY * z)
        val y2 = m * (RZ * x + y - RX * z)
        val z2 = m * (-RY * x + RX * y + z)
        val lon2 = atan2(y2, x2)
        val p = sqrt(x2 * x2 + y2 * y2)
        var lat2 = atan2(z2, p * (1 - E2))
        repeat(10) {
            val n2 = A / sqrt(1 - E2 * sin(lat2) * sin(lat2))
            lat2 = atan2(z2 + E2 * n2 * sin(lat2), p)
        }
        return Pair(lat2, lon2)
    }

    /**
     * Projects WGS84 [latitude]/[longitude] (degrees) to integer ITM
     * easting/northing, truncated exactly like the helper's int() cast.
     * Refuses implausible inputs instead of sending a corrupt coordinate.
     */
    fun toItm(latitude: Double, longitude: Double): Pair<Int, Int> {
        require(latitude.isFinite() && abs(latitude) <= 90.0 &&
            longitude.isFinite() && abs(longitude) <= 180.0) {
            "Invalid WGS84 coordinate."
        }
        val (lat, lon) = wgs84ToIsrael1993(Math.toRadians(latitude), Math.toRadians(longitude))
        val lat0 = Math.toRadians(LAT0)
        val dLon = lon - Math.toRadians(LON0)
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val n = A / sqrt(1 - E2 * sinLat * sinLat)
        val t = tan(lat).pow(2)
        val c = EP2 * cosLat * cosLat
        val aCoeff = cosLat * dLon
        val m = meridionalArc(lat)
        val m0 = meridionalArc(lat0)
        val x = FALSE_E + K0 * n * (aCoeff + (1 - t + c) * aCoeff.pow(3) / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * EP2) * aCoeff.pow(5) / 120)
        val y = FALSE_N + K0 * (m - m0 + n * tan(lat) * (aCoeff.pow(2) / 2 +
            (5 - t + 9 * c + 4 * c * c) * aCoeff.pow(4) / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * EP2) * aCoeff.pow(6) / 720))
        return Pair(x.toInt(), y.toInt())
    }
}
