package il.sidewalks.reporter

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live submission E2E (androidTest live/ package + its manual workflow)
 * must stay opt-in, secret-free in public output and encrypted in storage.
 * Complements StubE2eSafetyTest, which keeps the synthetic E2E stub-only;
 * both guards run as plain JVM unit tests in every build.
 */
class LiveE2eSafetyTest {
    private fun repoFile(vararg candidates: String): File =
        candidates.map(::File).first { it.isFile }

    private val liveTest by lazy {
        repoFile(
            "src/androidTest/java/il/sidewalks/reporter/live/LiveEmulatorSubmissionTest.kt",
            "app/src/androidTest/java/il/sidewalks/reporter/live/LiveEmulatorSubmissionTest.kt")
    }
    private val workflow by lazy {
        repoFile(
            "../.github/workflows/live-emulator-e2e.yml",
            ".github/workflows/live-emulator-e2e.yml")
    }

    @Test fun liveE2eRunsOnlyWithStagedParams() {
        val source = liveTest.readText()
        assertTrue(source.contains("live-params.json"))
        assertTrue(source.contains("if (!paramsFile.isFile) return"))
        // Digest binding to the owner-approved text, with a constant failure
        // message (assertEquals would print both digests on mismatch).
        assertTrue(source.contains("approvedSha256"))
        assertTrue(source.contains("SHA-256"))
        assertFalse(source.contains("assertEquals("))
        // A photo that already produced a report is never re-filed.
        assertTrue(source.contains("refusing to file again"))
    }

    @Test fun liveE2eNeverLeaksSecrets() {
        val source = liveTest.readText()
        assertFalse(source.contains("android.util.Log"))
        assertFalse(source.contains("println"))
        assertFalse(source.contains("getArguments("))
        // Settings reach the UI in memory; nothing persists them on device.
        assertFalse(source.contains("SecureSettings"))
        // The scrubbed marker log is the only text output of the test.
        assertTrue(source.contains("live-e2e-log.txt"))
        // The staged params carry secrets and must always be wiped.
        assertTrue(source.contains("paramsFile.delete()"))
        // The municipal case number is owner-private: only a fixed marker
        // reaches the public log, never the interpolated number.
        assertTrue(source.contains("\"SUBMITTED case=\" + if (caseNumber != null) \"present\" else \"unparsed\""))
        assertFalse(Regex("SUBMITTED case=\\$").containsMatchIn(source))
    }

    @Test fun ambiguousSubmitKeepsItsEvidence() {
        val source = liveTest.readText()
        // A timeout after the send tap may mean the case exists: it gets a
        // distinct marker, the submission state is preserved for the
        // encrypted bundle, and cleanup is skipped.
        assertTrue(source.contains("OUTCOME_UNKNOWN"))
        assertTrue(source.contains("preserveSubmissionState"))
        assertTrue(source.contains("live-e2e-submission-state.json"))
        assertTrue(source.contains("if (!cleaned && !preserveState)"))
        assertTrue(source.contains("do not re-dispatch; check 106 first"))
        // Only a refused send (nothing reached the city) retries in-run.
        // SEND_NOT_FIRED is arbitrated against the submission-state record
        // (written before the first network call), never retried on a
        // missing label alone: a record means ambiguous, check 106 first.
        // A municipal failure note (SUBMIT_FAILED) is classified the same
        // way: "prepared"/absent means CreateCase was never reached and a
        // re-dispatch is safe; anything later means the 106 side decides.
        assertTrue(source.contains("DRAFT_NOT_SAVED && sendAttempts < 3"))
        assertTrue(source.contains("SEND_NOT_FIRED"))
        assertTrue(source.contains("submissionsBefore"))
        assertTrue(source.contains("submission record appeared without the sending state - check 106 first"))
        assertTrue(source.contains("FlowDriver.SendOutcome.SUBMIT_FAILED"))
        assertTrue(source.contains("SUBMIT_FAILED_PRE_CASE"))
        assertTrue(source.contains("submit failed after case creation started - check 106 first"))
    }

    @Test fun liveProviderServesOnlyAStagedPhoto() {
        val provider = repoFile(
            "src/androidTest/java/il/sidewalks/reporter/live/LiveShareProvider.java",
            "app/src/androidTest/java/il/sidewalks/reporter/live/LiveShareProvider.java")
            .readText()
        assertTrue(provider.contains("live-photo.jpg"))
        assertTrue(provider.contains("FileNotFoundException"))
        // No write surface: the provider only ever reads the staged file.
        assertFalse(provider.contains("MODE_WRITE"))
    }

    @Test fun categoryLabelResolvesFromStagedSubject() {
        val resolver = repoFile(
            "src/test/java/il/sidewalks/reporter/core/LiveCategoryResolutionTest.kt",
            "app/src/test/java/il/sidewalks/reporter/core/LiveCategoryResolutionTest.kt")
            .readText()
        // The picker label comes from the same TRIAL_SUBJECT code the engine
        // trial files with, never from free text.
        assertTrue(resolver.contains("TRIAL_SUBJECT"))
        assertTrue(resolver.contains("missing env TRIAL_SUBJECT"))
        assertTrue(resolver.contains("unsupported trial subject"))
        val yaml = workflow.readText()
        assertTrue(yaml.contains("TRIAL_SUBJECT"))
        assertTrue(yaml.contains("LiveCategoryResolutionTest"))
        assertFalse(yaml.contains("category_label"))
    }

    @Test fun liveWorkflowIsManualGatedAndEncryptsEvidence() {
        val yaml = workflow.readText()
        assertTrue(yaml.contains("workflow_dispatch:"))
        assertFalse(Regex("(?m)^  (push|pull_request|schedule):").containsMatchIn(yaml))
        assertTrue(yaml.contains("environment: m0-trial-submit"))
        assertTrue(yaml.contains("FILE-LIVE-REPORT"))
        // The staged text digest is validated on the runner before staging.
        assertTrue(yaml.contains("approved text digest mismatch"))
        // A digest that already went through a live run is refused: the
        // cross-run duplicate guard reads prior runs' encrypted records.
        assertTrue(yaml.contains("actions: read"))
        // Expired records are skipped (14-day memory), and the digest burn
        // is fail-safe: it may be skipped only when a positively collected
        // marker log (log.collected sentinel) proves the test started and
        // the send was never tapped.
        assertTrue(yaml.contains("select(.expired | not)"))
        assertTrue(yaml.contains("LIVE_E2E SEND_TAPPED"))
        assertTrue(yaml.contains("log.collected"))
        assertTrue(yaml.contains("LIVE_E2E START"))
        assertTrue(yaml.contains("live-e2e-run-record"))
        assertTrue(yaml.contains("refusing to re-dispatch"))
        // An ambiguous outcome fails loudly with the check-106-first rule.
        assertTrue(yaml.contains("OUTCOME_UNKNOWN"))
        assertTrue(yaml.contains("check 106 first"))
        // Recording, screenshots and raw instrumentation output are uploaded
        // only inside the openssl-encrypted bundle.
        assertTrue(yaml.contains("openssl enc"))
        val uploadPaths = Regex("(?m)^\\s+path:\\s*(\\S.*)$")
            .findAll(yaml).map { it.groupValues[1].trim() }.toList()
        assertTrue("expected an encrypted upload", uploadPaths.any { it.endsWith(".enc") })
        uploadPaths.forEach { path ->
            assertFalse("unencrypted private evidence upload: $path",
                path.endsWith(".mp4") || path.endsWith(".png") ||
                    path.endsWith("instrumentation.txt") || path.endsWith("params.json") ||
                    path.endsWith("case-number.txt") || path.endsWith("submission-state.json"))
        }
        // The staged params and photo are wiped from the device afterwards.
        assertTrue(yaml.contains("rm -f files/live-params.json"))
        assertTrue(yaml.contains("rm -f files/live-photo.jpg"))
    }

    @Test fun regularCiNeverStagesLiveData() {
        val androidCi = repoFile(
            "../.github/workflows/android.yml",
            ".github/workflows/android.yml").readText()
        assertFalse(androidCi.contains("live-params.json"))
        assertFalse(androidCi.contains("live-photo.jpg"))
        assertFalse(androidCi.contains("liveshare"))
    }
}
