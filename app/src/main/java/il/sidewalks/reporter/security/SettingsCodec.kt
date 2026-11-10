package il.sidewalks.reporter.security

import org.json.JSONObject

/**
 * Plaintext JSON shape of ReporterSettings, encrypted as one blob by
 * SecureSettings. Municipal API credentials are optional, entered manually by
 * the user (owner decision 2026-11-08), stored only inside the encrypted
 * envelope, never logged, and omitted from JSON entirely when blank.
 */
object SettingsCodec {
    fun encode(settings: ReporterSettings): ByteArray {
        val json = JSONObject()
            .put("firstName", settings.firstName)
            .put("lastName", settings.lastName)
            .put("phone", settings.phone)
            .put("idNumber", settings.idNumber)
            .put("email", settings.email)
        if (settings.municipalSubscriptionKey.isNotBlank()) {
            json.put("municipalSubscriptionKey", settings.municipalSubscriptionKey)
        }
        if (settings.municipalUsername.isNotBlank()) {
            json.put("municipalUsername", settings.municipalUsername)
        }
        if (settings.municipalPassword.isNotBlank()) {
            json.put("municipalPassword", settings.municipalPassword)
        }
        if (settings.passportMode) json.put("passportMode", true)
        if (settings.passportNumber.isNotBlank()) json.put("passportNumber", settings.passportNumber)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(plaintext: ByteArray): ReporterSettings {
        // org.json error messages embed the whole input; never leak stored values.
        val json = try {
            JSONObject(String(plaintext, Charsets.UTF_8))
        } catch (failure: Exception) {
            throw IllegalArgumentException("Stored settings are unreadable")
        }
        return ReporterSettings(
            json.getString("firstName"), json.getString("lastName"), json.getString("phone"),
            json.getString("idNumber"), json.getString("email"),
            municipalSubscriptionKey = json.optString("municipalSubscriptionKey"),
            municipalUsername = json.optString("municipalUsername"),
            municipalPassword = json.optString("municipalPassword"),
            passportMode = json.optBoolean("passportMode", false),
            passportNumber = json.optString("passportNumber"),
        )
    }
}
