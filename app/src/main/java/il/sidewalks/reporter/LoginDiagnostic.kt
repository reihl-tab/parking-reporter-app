package il.sidewalks.reporter

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LoginCredentials(val subscriptionKey: String, val username: String, val password: String) {
    fun isComplete(): Boolean = listOf(subscriptionKey, username, password).all { it.isNotBlank() }
    fun missingFields(): List<String> = listOfNotNull(
        "subscription_key".takeIf { subscriptionKey.isBlank() },
        "api_username".takeIf { username.isBlank() },
        "api_password".takeIf { password.isBlank() },
    )
    override fun toString(): String = "LoginCredentials(redacted)"
}
enum class DiagnosticOutcome { SUCCESS, DENIED, INVALID_RESPONSE, NETWORK_ERROR, MISSING_CREDENTIALS, NOT_MOBILE }
data class DiagnosticResult(val outcome: DiagnosticOutcome, val httpStatus: Int? = null, val missingFields: List<String> = emptyList())
fun interface DiagnosticRunner { fun run(credentials: LoginCredentials, mobileData: Boolean): DiagnosticResult }

/** Only Login exists in this client. It cannot create contacts, cases or attachments. */
class LoginDiagnostic internal constructor(
    private val client: OkHttpClient,
    private val loginUrl: String,
) : DiagnosticRunner {
    override fun run(credentials: LoginCredentials, mobileData: Boolean): DiagnosticResult {
        if (!mobileData) return DiagnosticResult(DiagnosticOutcome.NOT_MOBILE)
        if (!credentials.isComplete()) return DiagnosticResult(DiagnosticOutcome.MISSING_CREDENTIALS, missingFields = credentials.missingFields())
        val body = JSONObject().put("UserName", credentials.username).put("Password", credentials.password).toString()
        val request = Request.Builder().url(loginUrl)
            .header("Ocp-Apim-Subscription-Key", credentials.subscriptionKey)
            .header("User-Agent", "App 106")
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType())).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return DiagnosticResult(DiagnosticOutcome.DENIED, response.code)
                // Bound response size and discard token immediately. Never log server text.
                val bytes = response.body?.byteStream()?.use { stream ->
                    val buffer = ByteArray(65537)
                    var count = 0
                    while (count < buffer.size) {
                        val read = stream.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    buffer.copyOf(count)
                }
                if (bytes == null || bytes.size > 65536) return DiagnosticResult(DiagnosticOutcome.INVALID_RESPONSE, response.code)
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                val ok = json.opt("Success") == true && json.optString("Token").isNotBlank()
                DiagnosticResult(if (ok) DiagnosticOutcome.SUCCESS else DiagnosticOutcome.INVALID_RESPONSE, response.code)
            }
        } catch (_: Exception) { DiagnosticResult(DiagnosticOutcome.NETWORK_ERROR) }
    }
    companion object {
        fun safeClient(): OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .callTimeout(45, TimeUnit.SECONDS).build()
    }
}
