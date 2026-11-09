package il.sidewalks.reporter.municipal

/** Test-only diagnostics: fixed vocabulary, never payloads or exception messages. */
internal class TrialStageClient(
    private val delegate: MunicipalClient,
    private val emit: (String) -> Unit,
) : MunicipalClient {
    private enum class Stage { LOGIN, CONTACT, CREATE_CASE, ATTACH_PHOTO }
    private enum class ErrorKind { HTTP_403, HTTP_OTHER, NETWORK_OR_RESPONSE, API_REJECTED, OTHER }

    private fun kind(error: Throwable): ErrorKind {
        if (error !is MunicipalApiException) return ErrorKind.OTHER
        // Only classify the client's own fixed templates. Never emit error.message.
        val message = error.message.orEmpty()
        return when {
            Regex("^(Login|createupdatecontact|CreateCase|AddFileToCase): HTTP 403\\.$").matches(message) -> ErrorKind.HTTP_403
            Regex("^(Login|createupdatecontact|CreateCase|AddFileToCase): HTTP [0-9]{3}\\.$").matches(message) -> ErrorKind.HTTP_OTHER
            Regex("^(Login|createupdatecontact|CreateCase|AddFileToCase): network failure or invalid response\\.$").matches(message) -> ErrorKind.NETWORK_OR_RESPONSE
            Regex("^(Login|createupdatecontact|CreateCase|AddFileToCase): municipality did not confirm success\\.$").matches(message) -> ErrorKind.API_REJECTED
            else -> ErrorKind.OTHER
        }
    }

    private fun <T> call(stage: Stage, action: () -> T): T {
        emit("STAGE_${stage.name}_START")
        try {
            val result = action()
            emit("STAGE_${stage.name}_RETURNED")
            return result
        } catch (error: Throwable) {
            emit("STAGE_${stage.name}_ERROR_${kind(error).name}")
            throw error // No retry, altered state, or transformed exception.
        }
    }

    override fun login(): String = call(Stage.LOGIN) { delegate.login() }
    override fun upsertContact(token: String, contact: LinkedHashMap<String, Any?>): Map<String, Any?> =
        call(Stage.CONTACT) { delegate.upsertContact(token, contact) }
    override fun createCase(token: String, contact: LinkedHashMap<String, Any?>, report: LinkedHashMap<String, Any?>): Map<String, Any?> =
        call(Stage.CREATE_CASE) { delegate.createCase(token, contact, report) }
    override fun attachPhoto(token: String, caseIdInCRM: String, webCaseIdInCRM: String, fileName: String, jpeg: ByteArray) =
        call(Stage.ATTACH_PHOTO) { delegate.attachPhoto(token, caseIdInCRM, webCaseIdInCRM, fileName, jpeg) }
}
