package il.sidewalks.reporter.municipal

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Transport for the Jerusalem 106 submission API, ported from
 * parking-report-helper api106.py `Municipality` (helper 0.6.0, pinned
 * 51dd5c02916b1ebbe62fdd2390132f9f58a39eb8).
 *
 * Scope is the submission path only: Login, createupdatecontact, CreateCase,
 * AddFileToCase. Case-listing and case-file endpoints are deliberately not
 * ported (owner decision 2026-11-02: no municipal status APIs).
 *
 * Behavioural contract copied from the helper:
 * - POST with Content-Type "text/plain; charset=utf-8", User-Agent "App 106",
 *   Ocp-Apim-Subscription-Key header; body is Python-json byte-identical.
 * - Redirects are refused, never followed; no automatic retries.
 * - A response counts only as a JSON object with Success === true (boolean).
 * - 45 s timeouts. Response bodies are capped at 16 MB (the helper caps its
 *   GET reads at 16 MB; its POST reads are uncapped, so this is a hardening
 *   that no real municipal response can reach).
 * - Errors never contain credentials, tokens, URLs, or response bodies.
 */
interface MunicipalClient {
    /** POST Login; returns the session token. */
    fun login(): String

    /**
     * POST createupdatecontact with Token and SignUpByApp=true added to the
     * contact; returns the raw response map (ContactIdInCRM required by caller).
     */
    fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?>

    /** POST CreateCase with Token and contact added to the report; returns the raw response map. */
    fun createCase(
        token: String,
        contact: LinkedHashMap<String, Any?>,
        report: LinkedHashMap<String, Any?>,
    ): Map<String, Any?>

    /** POST AddFileToCase with the base64 JPEG. */
    fun attachPhoto(token: String, caseIdInCRM: String, webCaseIdInCRM: String, fileName: String, jpeg: ByteArray)
}

class OkHttpMunicipalClient private constructor(
    private val baseUrl: HttpUrl,
    private val subscriptionKey: String,
    private val username: String,
    private val password: String,
) : MunicipalClient {

    /**
     * Production path: the documented municipal endpoint only. The base URL
     * is not a parameter on purpose - a client that can be pointed at
     * another host can leak the municipal credentials there.
     */
    constructor(subscriptionKey: String, username: String, password: String) :
        this(DEFAULT_BASE_URL, subscriptionKey, username, password)

    init {
        if (subscriptionKey.isEmpty() || username.isEmpty() || password.isEmpty()) {
            throw MunicipalInputException(
                "Credentials must contain subscription_key and login.UserName/login.Password."
            )
        }
    }

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override fun login(): String {
        val body = LinkedHashMap<String, Any?>()
        body["UserName"] = username
        body["Password"] = password
        val result = post("Login", body)
        val token = result["Token"] as? String
        if (token.isNullOrEmpty()) throw MunicipalApiException("Login returned no token.")
        return token
    }

    override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> {
        val body = LinkedHashMap(contact)
        body["Token"] = token
        body["SignUpByApp"] = true
        return post("createupdatecontact", body)
    }

    override fun createCase(
        token: String,
        contact: LinkedHashMap<String, Any?>,
        report: LinkedHashMap<String, Any?>,
    ): Map<String, Any?> {
        val body = LinkedHashMap(report)
        body["Token"] = token
        body["contact"] = contact
        return post("CreateCase", body)
    }

    override fun attachPhoto(
        token: String,
        caseIdInCRM: String,
        webCaseIdInCRM: String,
        fileName: String,
        jpeg: ByteArray,
    ) {
        val body = LinkedHashMap<String, Any?>()
        body["token"] = token
        body["caseIdInCRM"] = caseIdInCRM
        body["webCaseIdInCRM"] = webCaseIdInCRM
        body["fileName"] = fileName
        // java.util.Base64 (not android.util.Base64) so JVM unit tests can run this path.
        body["fileContent"] = java.util.Base64.getEncoder().encodeToString(jpeg)
        post("AddFileToCase", body)
    }

    private fun post(endpoint: String, body: LinkedHashMap<String, Any?>): Map<String, Any?> {
        val url = baseUrl.toString().trimEnd('/') + "/" + endpoint
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("Content-Type", "text/plain; charset=utf-8")
            .header("User-Agent", "App 106")
            .header("Ocp-Apim-Subscription-Key", subscriptionKey)
            .post(PythonJson.writeBytes(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val raw: ByteArray
        // Diagnostics: which endpoint, how long, and the exception class are
        // what distinguishes "never reached the city" (connect failure) from
        // "the city may have it" (read timeout after the request went out).
        // No request or response content is ever included.
        val startedAt = System.currentTimeMillis()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MunicipalApiException("$endpoint: HTTP ${response.code}.")
                }
                // BufferedSource.readByteArray(n) requires EXACTLY n bytes and throws
                // EOFException at end-of-stream, so cap enforcement reads in chunks:
                // Buffer.read(sink, count) reads up to count and returns -1 at end.
                val source = response.body.source()
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, CHUNK_BYTES)
                    if (read == -1L) break
                    if (buffer.size > MAX_RESPONSE_BYTES) {
                        throw MunicipalApiException("$endpoint: municipality did not confirm success.")
                    }
                }
                raw = buffer.readByteArray()
            }
        } catch (e: MunicipalApiException) {
            throw e
        } catch (e: IOException) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            throw MunicipalApiException(
                "$endpoint: network failure or invalid response " +
                    "(${e.javaClass.simpleName} after ${elapsedMs}ms).")
        }
        val parsed: JSONObject = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (e: JSONException) {
            throw MunicipalApiException("$endpoint: network failure or invalid response.")
        }
        val success = parsed.opt("Success")
        if (success !is Boolean || !success) {
            throw MunicipalApiException("$endpoint: municipality did not confirm success.")
        }
        return parsed.toMap()
    }

    companion object {
        /** The documented municipal endpoint root (helper api106.BASE). */
        val DEFAULT_BASE_URL: HttpUrl =
            "https://api.jerusalem.muni.il/CRMCityConnect/api/jerusalem/".toHttpUrl()

        /** Test-only: points the client at a mock server. Never used in app code. */
        internal fun forTesting(
            baseUrl: HttpUrl,
            subscriptionKey: String,
            username: String,
            password: String,
        ): OkHttpMunicipalClient = OkHttpMunicipalClient(baseUrl, subscriptionKey, username, password)

        private const val TIMEOUT_SECONDS = 45L
        private const val MAX_RESPONSE_BYTES = 16L * 1024 * 1024
        private const val CHUNK_BYTES = 65536L
        private val JSON_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()

        private fun JSONObject.toMap(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            for (key in keys()) {
                val value = get(key)
                map[key] = when (value) {
                    JSONObject.NULL -> null
                    is JSONObject -> value.toMap()
                    else -> value
                }
            }
            return map
        }
    }
}
