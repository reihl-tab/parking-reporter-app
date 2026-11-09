package il.sidewalks.reporter.municipal

import il.sidewalks.reporter.core.Evidence
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.security.ReporterSettings
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * M0 engine-level live trial (owner-approved in chat 2026-11-09): exercises
 * the app's real municipal submission engine against the live 106 API from
 * CI. Runs ONLY when J106_LIVE_TRIAL is set ("login-only" or "submit");
 * skipped in ordinary CI. Phase 1 is a login-only diagnostic with no case
 * writes; the submit phase additionally requires the sha256 of the
 * owner-approved Hebrew text to match the dispatch input, exactly like the
 * helper pipeline's --approved-sha256 gate. Report specifics (plate, address,
 * coordinates, street lookup) arrive via secrets/env, never from this source
 * tree. No credential, token, report-specific value, case number or response
 * body is printed: this is a public repo, so Actions logs are world-readable
 * and every failure is reduced to a constant class marker. The municipal case
 * number is never logged; it reaches the owner via the municipal email
 * receipt.
 */
class EngineLiveTrialTest {

    private fun env(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("missing env $name")

    private fun municipalClient(): OkHttpMunicipalClient =
        OkHttpMunicipalClient(env("J106_SUBSCRIPTION_KEY"), env("J106_APP_USERNAME"), env("J106_APP_PASSWORD"))

    private fun settings() = ReporterSettings(
        firstName = env("REPORTER_FIRST_NAME"),
        lastName = env("REPORTER_LAST_NAME"),
        phone = env("REPORTER_PHONE"),
        idNumber = env("REPORTER_ID_NUMBER"),
        email = env("REPORTER_EMAIL"),
        municipalSubscriptionKey = env("J106_SUBSCRIPTION_KEY"),
        municipalUsername = env("J106_APP_USERNAME"),
        municipalPassword = env("J106_APP_PASSWORD"),
    )

    @Test fun loginOnlyDiagnostic() {
        assumeTrue(System.getenv("J106_LIVE_TRIAL") == "login-only")
        try {
            // Login-only phase: least privilege, only the three J106 secrets.
            val token = municipalClient().login()
            assertTrue(token.isNotEmpty())
            println("TRIAL_RESULT LOGIN_OK")
        } catch (e: Throwable) {
            // A 403 or a connect failure on login proves the direct egress is
            // blocked BEFORE any case write could have been attempted (login
            // never writes one), so the workflow may fall back to the owner
            // L2TP tunnel. Any other failure must not trigger the fallback.
            val message = e.message ?: ""
            if (message.contains("HTTP 403") || message.contains("network failure")) {
                File(System.getenv("EGRESS_MARKER_PATH") ?: "/tmp/egress-blocked").writeText("egress blocked\n")
            }
            println("TRIAL_RESULT ERROR class=" + e.javaClass.simpleName)
            throw AssertionError("login-only trial failed; class marker printed")
        }
    }

    @Test fun engineSubmit() {
        assumeTrue(System.getenv("J106_LIVE_TRIAL") == "submit")
        try {
            // Consent binding: the approved text must hash to the exact
            // dispatch input the owner approved in chat; anything else
            // aborts. Constant message only: never print the digests.
            val text = System.getenv("TRIAL_APPROVED_TEXT")
                ?: throw IllegalStateException("TRIAL_APPROVED_TEXT secret absent")
            val expected = env("TRIAL_APPROVED_SHA256").lowercase()
            val textSha = MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            if (expected != textSha) {
                throw AssertionError("approved text digest mismatch - refusing to submit")
            }

            val photo = File(env("TRIAL_PHOTO_PATH"))
            val photoSha = MessageDigest.getInstance("SHA-256")
                .digest(photo.readBytes())
                .joinToString("") { "%02x".format(it) }

            val evidence = Evidence(
                stableId = System.getenv("TRIAL_STABLE_ID") ?: "trial-report-1",
                originalSha256 = photoSha,
                capturedAtIso = env("TRIAL_CAPTURED_AT"),
                latitude = env("TRIAL_LAT").toDouble(),
                longitude = env("TRIAL_LON").toDouble(),
            )
            val draft = ReviewDraft(
                evidence = evidence,
                plate = env("TRIAL_PLATE"),
                address = env("TRIAL_ADDRESS"),
                subject = "sidewalk_parking",
                exactHebrewText = text,
                unresolved = emptySet(),
            ).approve()

            val streets = StreetDirectory.load(env("TRIAL_STREET_LOOKUP_JSON"))
            val stateDir = File(env("TRIAL_STATE_DIR")).apply { mkdirs() }
            val coordinator = SubmissionCoordinator({ s -> municipalClientFrom(s) }, streets) { stableId ->
                FileSubmissionStateStore(File(stateDir, "$stableId.json"))
            }
            // Single attempt: the workflow gives the submit phase no
            // fallback, so a failure here can never re-run a case write.
            when (val outcome = coordinator.submitApproved(draft, settings(), photo)) {
                is SubmissionCoordinator.Outcome.Submitted ->
                    println("TRIAL_RESULT SUBMITTED")
                is SubmissionCoordinator.Outcome.AlreadySubmitted ->
                    println("TRIAL_RESULT ALREADY_SUBMITTED")
                is SubmissionCoordinator.Outcome.Blocked -> {
                    println("TRIAL_RESULT BLOCKED reason=${outcome.reason}")
                    throw AssertionError("trial blocked")
                }
            }
        } catch (e: Throwable) {
            println("TRIAL_RESULT ERROR class=" + e.javaClass.simpleName)
            throw AssertionError("submit trial failed; class marker printed")
        }
    }

    private fun municipalClientFrom(s: ReporterSettings): OkHttpMunicipalClient =
        OkHttpMunicipalClient(s.municipalSubscriptionKey, s.municipalUsername, s.municipalPassword)
}
