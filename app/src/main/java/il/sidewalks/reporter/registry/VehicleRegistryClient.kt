package il.sidewalks.reporter.registry

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * A plate number reduced to its digits. Built only through [parse], so every instance is a
 * 7- or 8-digit Israeli plate. [toString] never shows the digits.
 */
class PlateNumber private constructor(val digits: String) {
    override fun equals(other: Any?): Boolean = other is PlateNumber && other.digits == digits
    override fun hashCode(): Int = digits.hashCode()
    override fun toString(): String = "PlateNumber(redacted)"

    companion object {
        private val SEPARATORS = setOf(' ', '-', '.')

        /**
         * Strips spaces, hyphens and dots, then accepts only ASCII digits, 7 or 8 of them,
         * with no leading zero (the registry stores the plate as a number). Returns null otherwise.
         */
        fun parse(raw: String): PlateNumber? {
            val digits = raw.trim().filterNot { it in SEPARATORS }
            if (digits.length !in 7..8) return null
            if (!digits.all { it in '0'..'9' }) return null
            if (digits[0] == '0') return null
            return PlateNumber(digits)
        }
    }
}

enum class LicenceStatus { VALID, EXPIRED, UNKNOWN }

/** The registry fields this app reads. [toString] omits the plate. */
data class VehicleRecord(
    val plate: PlateNumber,
    /** `tokef_dt`: the date the annual licence is valid until. */
    val licenceValidUntil: LocalDate?,
    /** `mivchan_acharon_dt`: the date of the last annual test. */
    val lastTestDate: LocalDate?,
    /** `tozeret_nm`: manufacturer name. */
    val manufacturer: String?,
    /** `kinuy_mishari`: commercial model name. */
    val commercialName: String?,
    /** `tzeva_rechev`: colour name. */
    val colour: String?,
    /** `shnat_yitzur`: year of manufacture. */
    val yearOfManufacture: Int?,
) {
    fun licenceStatus(today: LocalDate): LicenceStatus = when {
        licenceValidUntil == null -> LicenceStatus.UNKNOWN
        licenceValidUntil.isBefore(today) -> LicenceStatus.EXPIRED
        else -> LicenceStatus.VALID
    }

    override fun toString(): String =
        "VehicleRecord(plate=redacted, licenceValidUntil=$licenceValidUntil, lastTestDate=$lastTestDate, " +
            "manufacturer=$manufacturer, commercialName=$commercialName, colour=$colour, yearOfManufacture=$yearOfManufacture)"
}

enum class LookupErrorKind {
    /** Input is not a 7–8 digit plate. No request was made. */
    INVALID_PLATE,
    /** The client-side rate limit denied the request. No request was made. */
    RATE_LIMITED,
    /** Connection failure, timeout or other I/O error. Not retried. */
    NETWORK,
    /** A non-2xx HTTP status (redirects are not followed). */
    HTTP,
    /** The body was not the expected CKAN JSON, was oversized or did not match the plate. */
    MALFORMED_RESPONSE,
    /** CKAN answered `success: false`. */
    API_FAILURE,
}

sealed interface VehicleLookupResult {
    data class Found(val record: VehicleRecord) : VehicleLookupResult
    data object NotFound : VehicleLookupResult
    data class Error(val kind: LookupErrorKind, val httpStatus: Int? = null) : VehicleLookupResult
}

/**
 * Runtime configuration. [resourceId] has no default: the caller passes it (for example
 * [KNOWN_VEHICLE_RESOURCE_ID]). [apiKey] is optional; the public dataset needs none.
 */
class VehicleRegistryConfig(
    val resourceId: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String? = null,
    val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
    val cacheMaxEntries: Int = DEFAULT_CACHE_MAX_ENTRIES,
    val minRequestIntervalMillis: Long = DEFAULT_MIN_REQUEST_INTERVAL_MILLIS,
) {
    init {
        require(RESOURCE_ID.matches(resourceId)) { "resourceId must be a CKAN resource UUID" }
        require(cacheTtlMillis >= 0) { "cacheTtlMillis must be >= 0" }
        require(cacheMaxEntries >= 0) { "cacheMaxEntries must be >= 0" }
        require(minRequestIntervalMillis >= 0) { "minRequestIntervalMillis must be >= 0" }
    }

    override fun toString(): String =
        "VehicleRegistryConfig(resourceId=$resourceId, baseUrl=$baseUrl, apiKey=${if (apiKey == null) "none" else "redacted"})"

    companion object {
        const val DEFAULT_BASE_URL = "https://data.gov.il"

        /** data.gov.il "מאגר מספרי רישוי של כלי רכב" (vehicle registration numbers). See docs/VEHICLE_REGISTRY.md. */
        const val KNOWN_VEHICLE_RESOURCE_ID = "053cea08-09bc-40ec-8f7a-156f0677aff3"
        const val DEFAULT_CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000
        const val DEFAULT_CACHE_MAX_ENTRIES = 128
        const val DEFAULT_MIN_REQUEST_INTERVAL_MILLIS = 1_000L
        private val RESOURCE_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

/**
 * Read-only plate lookup against the data.gov.il CKAN `datastore_search` action. It makes GET
 * requests to that one path only, never writes, never retries and never logs. [lookup] blocks;
 * call it off the main thread. [clockMillis] must be monotonic; it drives both the cache TTL
 * and the rate limiter.
 */
class VehicleRegistryClient(
    private val config: VehicleRegistryConfig,
    private val client: OkHttpClient = safeClient(),
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val searchUrl: HttpUrl = config.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
        .addPathSegments(SEARCH_PATH.removePrefix("/")).build()
    private val cache = TtlLruCache<PlateNumber, VehicleLookupResult>(config.cacheMaxEntries, config.cacheTtlMillis, clockMillis)
    private val limiter = MinIntervalRateLimiter(config.minRequestIntervalMillis, clockMillis)

    fun lookup(rawPlate: String): VehicleLookupResult {
        val plate = PlateNumber.parse(rawPlate) ?: return VehicleLookupResult.Error(LookupErrorKind.INVALID_PLATE)
        cache.get(plate)?.let { return it }
        if (!limiter.tryAcquire()) return VehicleLookupResult.Error(LookupErrorKind.RATE_LIMITED)
        val result = fetch(plate)
        if (result is VehicleLookupResult.Found || result is VehicleLookupResult.NotFound) cache.put(plate, result)
        return result
    }

    private fun fetch(plate: PlateNumber): VehicleLookupResult {
        val url = searchUrl.newBuilder()
            .addQueryParameter("resource_id", config.resourceId)
            .addQueryParameter("filters", JSONObject().put(FIELD_PLATE, plate.digits.toLong()).toString())
            .addQueryParameter("fields", FIELDS.joinToString(","))
            .addQueryParameter("limit", "1")
            .build()
        val builder = Request.Builder().url(url).get().header("Accept", "application/json")
        config.apiKey?.let { builder.header(API_KEY_HEADER, it) }
        val body = try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return VehicleLookupResult.Error(LookupErrorKind.HTTP, response.code)
                readBounded(response.body.byteStream()) ?: return VehicleLookupResult.Error(LookupErrorKind.MALFORMED_RESPONSE)
            }
        } catch (_: IOException) {
            return VehicleLookupResult.Error(LookupErrorKind.NETWORK)
        }
        return try { parse(body, plate) } catch (_: RuntimeException) { VehicleLookupResult.Error(LookupErrorKind.MALFORMED_RESPONSE) }
    }

    private fun parse(body: String, plate: PlateNumber): VehicleLookupResult {
        val json = JSONObject(body)
        if (json.opt("success") != true) return VehicleLookupResult.Error(LookupErrorKind.API_FAILURE)
        val records: JSONArray = json.getJSONObject("result").getJSONArray("records")
        if (records.length() == 0) return VehicleLookupResult.NotFound
        val record = records.getJSONObject(0)
        // The filter is the server's job; still refuse a record for some other plate.
        val returnedPlate = record.opt(FIELD_PLATE).let { if (it is Number) it.toLong().toString() else it?.toString() }
        if (returnedPlate != plate.digits) return VehicleLookupResult.Error(LookupErrorKind.MALFORMED_RESPONSE)
        return VehicleLookupResult.Found(
            VehicleRecord(
                plate = plate,
                licenceValidUntil = date(record, "tokef_dt"),
                lastTestDate = date(record, "mivchan_acharon_dt"),
                manufacturer = text(record, "tozeret_nm"),
                commercialName = text(record, "kinuy_mishari"),
                colour = text(record, "tzeva_rechev"),
                yearOfManufacture = (record.opt("shnat_yitzur") as? Number)?.toInt(),
            ),
        )
    }

    private fun text(record: JSONObject, key: String): String? =
        record.opt(key)?.takeIf { it != JSONObject.NULL }?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun date(record: JSONObject, key: String): LocalDate? {
        val value = text(record, key) ?: return null
        if (!ISO_DATE_PREFIX.containsMatchIn(value)) return null
        return runCatching { LocalDate.parse(value.substring(0, 10)) }.getOrNull()
    }

    private fun readBounded(stream: java.io.InputStream): String? = stream.use {
        val buffer = ByteArray(MAX_BODY_BYTES + 1)
        var count = 0
        while (count < buffer.size) {
            val read = it.read(buffer, count, buffer.size - count)
            if (read < 0) break
            count += read
        }
        if (count > MAX_BODY_BYTES) null else String(buffer, 0, count, Charsets.UTF_8)
    }

    companion object {
        const val SEARCH_PATH = "/api/3/action/datastore_search"
        /** CKAN's API key header. Sent only when [VehicleRegistryConfig.apiKey] is non-null. */
        const val API_KEY_HEADER = "Authorization"
        const val FIELD_PLATE = "mispar_rechev"
        val FIELDS = listOf(FIELD_PLATE, "tokef_dt", "mivchan_acharon_dt", "tozeret_nm", "kinuy_mishari", "tzeva_rechev", "shnat_yitzur")
        private const val MAX_BODY_BYTES = 65_536
        private val ISO_DATE_PREFIX = Regex("^\\d{4}-\\d{2}-\\d{2}")

        fun safeClient(): OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).build()
    }
}

/** Denies (does not wait) any acquire sooner than [minIntervalMillis] after the last granted one. */
internal class MinIntervalRateLimiter(private val minIntervalMillis: Long, private val clockMillis: () -> Long) {
    private var lastGranted: Long? = null

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clockMillis()
        val last = lastGranted
        if (last != null && now - last < minIntervalMillis) return false
        lastGranted = now
        return true
    }
}

/** In-memory LRU with a per-entry TTL. Expired entries are dropped on read. */
internal class TtlLruCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val clockMillis: () -> Long,
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (clockMillis() - entry.storedAt >= ttlMillis) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        if (maxEntries == 0 || ttlMillis == 0L) return
        map[key] = Entry(value, clockMillis())
    }
}
