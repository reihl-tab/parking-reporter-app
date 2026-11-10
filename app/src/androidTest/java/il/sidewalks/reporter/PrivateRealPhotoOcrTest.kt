package il.sidewalks.reporter

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import il.sidewalks.reporter.evidence.OutgoingEvidence
import il.sidewalks.reporter.recognition.MlKitPlateOcr
import il.sidewalks.reporter.recognition.PlateCandidates
import il.sidewalks.reporter.recognition.OfflinePlateOcr
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File

/** Manual private benchmark only. Normal CI stages no input and performs no private work.
 * Both engines receive the SAME production-prepared image. Never imports a ledger,
 * constructs a municipal client, approves or submits anything. All output stays private. */
class PrivateRealPhotoOcrTest {
    @Test fun comparePrivatePhotoSample() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "ocr-benchmark")
        val manifest = File(folder, "manifest.json")
        if (!manifest.isFile) return
        val cases = JSONObject(manifest.readText()).getJSONArray("cases")
        require(cases.length() in 1..30) { "invalid benchmark case count" }
        val results = JSONArray()
        for (i in 0 until cases.length()) {
            val item = cases.getJSONObject(i)
            val filename = item.getString("file")
            require(filename.matches(Regex("sample-[0-9]+\\.jpg"))) { "invalid benchmark file" }
            val original = File(folder, filename)
            val prepared = OutgoingEvidence.prepare(original)
            val bitmap = BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size)
                ?: error("benchmark decode failed")
            val row = JSONObject().put("sample", i).put("expected", item.getString("expected"))
                .put("original_sha256", prepared.originalHash).put("prepared_sha256", prepared.hash)
                .put("width", prepared.width).put("height", prepared.height)
            try {
                // Alternate order to reduce consistent warm/cold first-engine bias.
                val engines = if (i % 2 == 0) listOf("tesseract", "mlkit") else listOf("mlkit", "tesseract")
                for (engine in engines) {
                    val start = System.nanoTime()
                    val answer = JSONObject()
                    try {
                        val raw = if (engine == "tesseract") OfflinePlateOcr(context).recognizeText(bitmap)
                            else MlKitPlateOcr.recognizeText(bitmap)
                        val values = PlateCandidates.fromText(raw)
                        // This file is exported only by the manual benchmark and encrypted
                        // before artifact upload. Never print raw OCR to instrumentation/logcat.
                        answer.put("raw_text", raw).put("candidates", JSONArray(values)).put("error", JSONObject.NULL)
                    } catch (failure: Throwable) {
                        if (failure is ThreadDeath || failure is VirtualMachineError) throw failure
                        // Class only, never exception messages containing paths/private input.
                        answer.put("candidates", JSONArray()).put("error", failure.javaClass.simpleName)
                    }
                    answer.put("elapsed_ms", (System.nanoTime() - start) / 1_000_000)
                    row.put(engine, answer)
                }
                results.put(row)
                File(context.filesDir, "ocr-private-results.json").writeText(results.toString(2))
            } finally { bitmap.recycle() }
        }
    }
}
