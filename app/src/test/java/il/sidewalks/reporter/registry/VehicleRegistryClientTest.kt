package il.sidewalks.reporter.registry

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Synthetic plates and recorded synthetic JSON only (src/test/resources/registry). No live network. */
class VehicleRegistryClientTest {
    private companion object {
        const val TEST_HTTPS_BASE_URL = "https://registry.invalid"
    }

    private val resourceId = "00000000-0000-4000-8000-000000000102"
    private val plate = "1234567"

    private class FakeClock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("registry/$name")) { "missing fixture $name" }.readText()

    private fun json(name: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(fixture(name))

    private fun client(
        server: MockWebServer,
        clock: FakeClock = FakeClock(),
        apiKey: String? = null,
        ttl: Long = 60_000L,
        interval: Long = 0L,
        http: OkHttpClient = VehicleRegistryClient.safeClient(),
    ) = VehicleRegistryClient(
        VehicleRegistryConfig(
            resourceId = resourceId,
            baseUrl = server.url("/").toString(),
            apiKey = apiKey,
            cacheTtlMillis = ttl,
            minRequestIntervalMillis = interval,
        ),
        http,
        clock,
    )

    @Test fun foundMapsRegistryFields() {
        MockWebServer().use { server ->
            server.enqueue(json("found.json"))
            val result = client(server).lookup(plate)
            assertTrue(result is VehicleLookupResult.Found)
            val record = (result as VehicleLookupResult.Found).record
            assertEquals(plate, record.plate.digits)
            assertEquals(LocalDate.of(2027, 3, 31), record.licenceValidUntil)
            assertEquals(LocalDate.of(2026, 3, 15), record.lastTestDate)
            assertEquals("SYNTHETIC MOTORS", record.manufacturer)
            assertEquals("TESTCAR", record.commercialName)
            assertEquals("SYNTHETIC GREY", record.colour)
            assertEquals(2020, record.yearOfManufacture)
            assertEquals(LicenceStatus.VALID, record.licenceStatus(LocalDate.of(2027, 3, 31)))
            assertEquals(LicenceStatus.EXPIRED, record.licenceStatus(LocalDate.of(2027, 4, 1)))
        }
    }

    @Test fun foundWithNullOrBadFieldsKeepsThemUnknown() {
        MockWebServer().use { server ->
            server.enqueue(json("found_null_dates.json"))
            val record = (client(server).lookup("12345678") as VehicleLookupResult.Found).record
            assertNull(record.licenceValidUntil)
            assertNull(record.lastTestDate)
            assertNull(record.manufacturer)
            assertNull(record.commercialName)
            assertNull(record.yearOfManufacture)
            assertEquals(LicenceStatus.UNKNOWN, record.licenceStatus(LocalDate.of(2026, 1, 1)))
        }
    }

    @Test fun emptyRecordsMapToNotFound() {
        MockWebServer().use { server ->
            server.enqueue(json("not_found.json"))
            assertEquals(VehicleLookupResult.NotFound, client(server).lookup(plate))
        }
    }

    @Test fun http500IsHttpError() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500).setBody("synthetic server detail"))
            val result = client(server).lookup(plate)
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.HTTP, 500), result)
            assertFalse(result.toString().contains("synthetic server detail"))
        }
    }

    @Test fun redirectIsNotFollowed() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", server.url("/elsewhere")))
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.HTTP, 307), client(server).lookup(plate))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun malformedOrOversizedBodyIsMalformedResponse() {
        for (body in listOf("not-json", "{\"success\":true}", "{\"success\":true,\"result\":{}}", "[]", "x".repeat(70_000))) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals(VehicleLookupResult.Error(LookupErrorKind.MALFORMED_RESPONSE), client(server).lookup(plate))
            }
        }
    }

    @Test fun ckanSuccessFalseIsApiFailure() {
        for (body in listOf(fixture("ckan_failure.json"), "{\"success\":\"true\",\"result\":{\"records\":[]}}", "{\"result\":{\"records\":[]}}")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                assertEquals(VehicleLookupResult.Error(LookupErrorKind.API_FAILURE), client(server).lookup(plate))
            }
        }
    }

    @Test fun recordForAnotherPlateIsRejected() {
        MockWebServer().use { server ->
            server.enqueue(json("other_plate.json"))
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.MALFORMED_RESPONSE), client(server).lookup(plate))
        }
    }

    @Test fun closedServerIsNetworkErrorWithoutRetry() {
        val server = MockWebServer()
        server.start()
        val registry = client(server)
        server.close()
        assertEquals(VehicleLookupResult.Error(LookupErrorKind.NETWORK), registry.lookup(plate))
    }

    @Test fun disconnectAndTimeoutAreNetworkErrorsWithoutRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(json("found.json"))
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.NETWORK), client(server).lookup(plate))
            assertEquals(1, server.requestCount)
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val quick = VehicleRegistryClient.safeClient().newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.NETWORK), client(server, http = quick).lookup(plate))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun connectionFailureIsNotRetried() {
        MockWebServer().use { server ->
            server.enqueue(json("not_found.json"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(json("found.json"))
            val registry = client(server)
            assertEquals(VehicleLookupResult.NotFound, registry.lookup("7654321"))
            // The pooled connection drops mid-call; a retrying client would get the third response.
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.NETWORK), registry.lookup(plate))
            assertEquals(2, server.requestCount)
        }
        val safe = VehicleRegistryClient.safeClient()
        assertFalse(safe.retryOnConnectionFailure)
        assertFalse(safe.followRedirects)
        assertFalse(safe.followSslRedirects)
    }

    @Test fun invalidPlateMakesNoRequest() {
        MockWebServer().use { server ->
            val registry = client(server)
            val invalid = listOf("", "   ", "123456", "123456789", "12a4567", "0123456", "1234567x", "١٢٣٤٥٦٧", "12_34567", "+1234567")
            for (raw in invalid) {
                assertEquals(raw, VehicleLookupResult.Error(LookupErrorKind.INVALID_PLATE), registry.lookup(raw))
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun plateSeparatorsAreNormalised() {
        assertEquals("1234567", PlateNumber.parse(" 12-345-67 ")?.digits)
        assertEquals("12345678", PlateNumber.parse("123.45.678")?.digits)
        assertEquals(PlateNumber.parse("1234567"), PlateNumber.parse("12 345 67"))
    }

    /**
     * Test-only path for a keyed config: the config keeps a real https base URL (so the production
     * https rule applies unchanged), and the injected OkHttp client rewrites each request to the
     * plain-http MockWebServer. The client never sees the http URL. `.invalid` never resolves.
     */
    private fun keyedClientRoutedTo(server: MockWebServer, apiKey: String, seenUrls: MutableList<String>): VehicleRegistryClient {
        val routing = VehicleRegistryClient.safeClient().newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                seenUrls += original.url.toString()
                val target = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(original.newBuilder().url(target).build())
            }.build()
        return VehicleRegistryClient(
            VehicleRegistryConfig(resourceId = resourceId, baseUrl = TEST_HTTPS_BASE_URL, apiKey = apiKey, minRequestIntervalMillis = 0L),
            routing,
            FakeClock(),
        )
    }

    @Test fun apiKeyHeaderSentOnlyWhenSet() {
        MockWebServer().use { server ->
            server.enqueue(json("not_found.json"))
            client(server).lookup(plate)
            assertNull(server.takeRequest().getHeader(VehicleRegistryClient.API_KEY_HEADER))
        }
        MockWebServer().use { server ->
            server.enqueue(json("not_found.json"))
            val seenUrls = mutableListOf<String>()
            assertEquals(VehicleLookupResult.NotFound, keyedClientRoutedTo(server, "synthetic-api-key", seenUrls).lookup(plate))
            assertEquals("synthetic-api-key", server.takeRequest().getHeader(VehicleRegistryClient.API_KEY_HEADER))
            assertEquals(1, seenUrls.size)
            assertTrue(seenUrls.single(), seenUrls.single().startsWith("$TEST_HTTPS_BASE_URL/api/3/action/datastore_search?"))
        }
    }

    @Test fun apiKeyWithHttpBaseUrlIsRejected() {
        for (baseUrl in listOf("http://registry.invalid", "HTTP://registry.invalid/", "http://127.0.0.1:8080")) {
            val error = runCatching { VehicleRegistryConfig(resourceId, baseUrl = baseUrl, apiKey = "synthetic-api-key") }.exceptionOrNull()
            assertTrue(baseUrl, error is IllegalArgumentException)
        }
        MockWebServer().use { server ->
            val error = runCatching { client(server, apiKey = "synthetic-api-key") }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun apiKeyWithMalformedBaseUrlIsRejected() {
        for (baseUrl in listOf("", "registry.invalid", "ftp://registry.invalid", "https//registry.invalid", "https://")) {
            val error = runCatching { VehicleRegistryConfig(resourceId, baseUrl = baseUrl, apiKey = "synthetic-api-key") }.exceptionOrNull()
            assertTrue(baseUrl, error is IllegalArgumentException)
        }
    }

    @Test fun apiKeyWithHttpsBaseUrlIsAccepted() {
        for (baseUrl in listOf(TEST_HTTPS_BASE_URL, "$TEST_HTTPS_BASE_URL/", "HTTPS://registry.invalid:8443", VehicleRegistryConfig.DEFAULT_BASE_URL)) {
            val config = VehicleRegistryConfig(resourceId, baseUrl = baseUrl, apiKey = "synthetic-api-key")
            assertEquals("synthetic-api-key", config.apiKey)
            VehicleRegistryClient(config)
        }
        assertEquals("synthetic-api-key", VehicleRegistryConfig(resourceId, apiKey = "synthetic-api-key").apiKey)
    }

    @Test fun nullKeyWithHttpBaseUrlIsStillAccepted() {
        assertNull(VehicleRegistryConfig(resourceId, baseUrl = "http://registry.invalid").apiKey)
        MockWebServer().use { server ->
            assertTrue(server.url("/").isHttps.not())
            server.enqueue(json("not_found.json"))
            assertEquals(VehicleLookupResult.NotFound, client(server, apiKey = null).lookup(plate))
            assertNull(server.takeRequest().getHeader(VehicleRegistryClient.API_KEY_HEADER))
        }
    }

    @Test fun httpsRejectionMessageNamesNeitherKeyNorUrl() {
        val key = "synthetic-api-key-7f3a"
        val baseUrl = "http://synthetic-host.invalid:8080/synthetic-path"
        val error = runCatching { VehicleRegistryConfig(resourceId, baseUrl = baseUrl, apiKey = key) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        val text = "${error?.message} ${error.toString()}"
        assertTrue(text, text.contains("https"))
        for (secret in listOf(key, "7f3a", baseUrl, "synthetic-host", "synthetic-path", "8080")) {
            assertFalse(secret, text.contains(secret))
        }
    }

    @Test fun onlyGetToDatastoreSearchWithExpectedQuery() {
        MockWebServer().use { server ->
            server.enqueue(json("found.json"))
            client(server).lookup("123-45-67")
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals(0L, request.bodySize)
            val url = requireNotNull(request.requestUrl)
            assertEquals(VehicleRegistryClient.SEARCH_PATH, url.encodedPath)
            assertEquals(setOf("resource_id", "filters", "fields", "limit"), url.queryParameterNames)
            assertEquals(resourceId, url.queryParameter("resource_id"))
            val filters = JSONObject(requireNotNull(url.queryParameter("filters")))
            assertEquals(setOf("mispar_rechev"), filters.keys().asSequence().toSet())
            assertEquals(1234567L, filters.getLong("mispar_rechev"))
            assertEquals(VehicleRegistryClient.FIELDS.joinToString(","), url.queryParameter("fields"))
            assertEquals("1", url.queryParameter("limit"))
        }
    }

    @Test fun cacheHitMakesNoSecondRequest() {
        MockWebServer().use { server ->
            server.enqueue(json("found.json"))
            server.enqueue(json("found.json"))
            val clock = FakeClock()
            val registry = client(server, clock)
            val first = registry.lookup(plate)
            clock.now += 59_999L
            val second = registry.lookup("12-345-67")
            assertTrue(first is VehicleLookupResult.Found)
            assertEquals(first, second)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun notFoundIsCachedButErrorsAreNot() {
        MockWebServer().use { server ->
            server.enqueue(json("not_found.json"))
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(json("found.json"))
            val registry = client(server)
            assertEquals(VehicleLookupResult.NotFound, registry.lookup(plate))
            assertEquals(VehicleLookupResult.NotFound, registry.lookup(plate))
            assertEquals(1, server.requestCount)
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.HTTP, 500), registry.lookup("12345678"))
            assertTrue(registry.lookup("12345678") is VehicleLookupResult.Error)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun ttlExpiryRefetches() {
        MockWebServer().use { server ->
            server.enqueue(json("not_found.json"))
            server.enqueue(json("found.json"))
            val clock = FakeClock()
            val registry = client(server, clock, ttl = 60_000L)
            assertEquals(VehicleLookupResult.NotFound, registry.lookup(plate))
            clock.now += 60_000L
            assertTrue(registry.lookup(plate) is VehicleLookupResult.Found)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun rateLimiterDeniesWithinIntervalAndAllowsAfter() {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(json("not_found.json")) }
            val clock = FakeClock()
            val registry = client(server, clock, interval = 1_000L)
            assertEquals(VehicleLookupResult.NotFound, registry.lookup("1111111"))
            clock.now += 999L
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.RATE_LIMITED), registry.lookup("2222222"))
            assertEquals(1, server.requestCount)
            // A cache hit is not a request and is never rate limited.
            assertEquals(VehicleLookupResult.NotFound, registry.lookup("1111111"))
            clock.now += 1L
            assertEquals(VehicleLookupResult.NotFound, registry.lookup("2222222"))
            assertEquals(2, server.requestCount)
            assertEquals(VehicleLookupResult.Error(LookupErrorKind.RATE_LIMITED), registry.lookup("3333333"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun lruEvictsLeastRecentlyUsed() {
        val clock = FakeClock()
        val cache = TtlLruCache<String, String>(2, 60_000L, clock)
        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals("1", cache.get("a"))
        cache.put("c", "3")
        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))
    }

    @Test fun plateNeverAppearsInResultsErrorsOrToString() {
        val outcomes = mutableListOf<Any>()
        for (response in listOf(json("found.json"), json("not_found.json"), MockResponse().setResponseCode(500), MockResponse().setBody("not-json"))) {
            MockWebServer().use { server ->
                server.enqueue(response)
                outcomes += client(server).lookup(plate)
            }
        }
        val throwing = VehicleRegistryClient.safeClient().newBuilder()
            .addInterceptor { throw IOException("synthetic failure for $plate") }.build()
        MockWebServer().use { server -> outcomes += client(server, http = throwing).lookup(plate) }
        outcomes += requireNotNull(PlateNumber.parse(plate))
        outcomes += VehicleRegistryConfig(resourceId, apiKey = "synthetic-api-key")
        for (outcome in outcomes) {
            val text = outcome.toString()
            assertFalse(text, text.contains(plate))
            assertFalse(text, text.contains("synthetic-api-key"))
        }
        assertEquals(VehicleLookupResult.Error(LookupErrorKind.NETWORK), outcomes[4])
        val configError = runCatching { VehicleRegistryConfig("$plate-not-a-uuid") }.exceptionOrNull()
        assertTrue(configError is IllegalArgumentException)
        assertFalse(configError?.message.orEmpty().contains(plate))
    }

    @Test fun knownResourceIdIsAValidConfig() {
        val config = VehicleRegistryConfig(VehicleRegistryConfig.KNOWN_VEHICLE_RESOURCE_ID)
        assertEquals("https://data.gov.il", config.baseUrl)
        assertNull(config.apiKey)
    }
}
