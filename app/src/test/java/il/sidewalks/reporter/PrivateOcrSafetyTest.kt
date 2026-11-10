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
        val isolation = read("../scripts/ocr-network-isolation.sh", "scripts/ocr-network-isolation.sh")
        assertTrue(isolation.contains("if ! adb shell 'echo | toybox nc -w 3 1.1.1.1 443'"))
        assertTrue(isolation.contains("svc wifi disable"))
        assertTrue(isolation.contains("svc data disable"))
        assertFalse(script.contains("ocr_network_isolation \"\$work/output\" ||"))
        assertTrue(isolation.contains("adb get-state"))
        assertTrue(isolation.contains("network positive control failed - refusing OCR"))
        assertTrue(isolation.indexOf("if ! adb shell 'echo | toybox nc") < isolation.indexOf("svc wifi disable"))
        assertTrue(isolation.indexOf("svc data disable") < isolation.indexOf("if adb shell 'echo | toybox nc"))
        assertTrue(isolation.contains("if adb shell 'echo | toybox nc -w 3 1.1.1.1 443'"))
        assertTrue(isolation.contains("network isolation check failed - refusing OCR"))
        assertTrue(script.contains("OCR_BENCH_INPUT_SHA256"))
        assertTrue(script.contains("trap finish_private_benchmark EXIT"))
        assertTrue(script.indexOf("ocr_network_isolation") < script.indexOf("adb shell run-as il.sidewalks.reporter mkdir"))
        assertTrue(script.contains("openssl enc -aes-256-cbc"))
        assertFalse(script.contains("screenrecord"))
        assertFalse(script.contains("screencap"))
    }
}
