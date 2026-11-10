package il.sidewalks.reporter

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PrivateOcrSafetyTest {
    private fun read(vararg paths: String) = paths.map(::File).first { it.isFile }.readText()
    @Test fun privateBenchmarkHasNoTransportOrLogging() {
        val test = read("src/androidTest/java/il/sidewalks/reporter/PrivateRealPhotoOcrTest.kt", "app/src/androidTest/java/il/sidewalks/reporter/PrivateRealPhotoOcrTest.kt")
        assertTrue(test.contains("if (!manifest.isFile) return"))
        assertTrue(test.contains("OutgoingEvidence.prepare(original)"))
        for (forbidden in listOf("MunicipalClient", "SubmissionCoordinator", "SecureSettings", "android.util.Log", "println(", "assertEquals("))
            assertFalse(forbidden, test.contains(forbidden))
    }
    @Test fun workflowUploadsOnlyEncryptedResultsAndUsesNoLiveSecrets() {
        val workflow = read("../.github/workflows/private-ocr-benchmark.yml", ".github/workflows/private-ocr-benchmark.yml")
        assertTrue(workflow.contains("github.ref == 'refs/heads/main'"))
        assertTrue(workflow.contains("inputs.confirm == 'COMPARE-ONLY'"))
        assertTrue(workflow.contains("path: /tmp/ocr-benchmark/results.tar.enc"))
        assertFalse(workflow.contains("TRIAL_"))
        assertFalse(workflow.contains("J106_"))
        val script = read("../scripts/run-private-ocr-benchmark.sh", "scripts/run-private-ocr-benchmark.sh")
        assertTrue(script.contains("svc wifi disable"))
        assertTrue(script.contains("svc data disable"))
        assertTrue(script.contains("OCR_BENCH_INPUT_SHA256"))
        assertTrue(script.contains("openssl enc -aes-256-cbc"))
        assertFalse(script.contains("screenrecord"))
        assertFalse(script.contains("screencap"))
    }
}
