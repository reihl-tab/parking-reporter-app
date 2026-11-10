package il.sidewalks.reporter

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PrivateOcrDiagnosticsTest {
    private fun sourceFile(path: String): File = listOf(File(path), File("app", path)).first { it.exists() }
    private fun source(path: String): String = sourceFile(path).readText()
    private val forbidden = listOf("Log.", "android.util.Log", "Timber.", "print(", "println(", "System.out", "System.err", "printStackTrace")
    private fun engineFindings(text: String): List<String> = buildList {
        for (token in forbidden) if (text.contains(token)) add(token)
        if (!text.contains("fun recognize(bitmap: Bitmap): List<String> = PlateCandidates.fromText(recognizeText(bitmap))")) add("delegation")
        if (!text.contains("internal fun recognizeText(bitmap: Bitmap): String")) add("internal declaration")
        if (Regex("recognizeText\\s*\\(").findAll(text).count() != 2) add("raw callers")
        if (!text.contains(".take(64_000)")) add("bound")
    }
    private fun privateTestFindings(text: String): List<String> = buildList {
        for (token in forbidden) if (text.contains(token)) add(token)
        if (!text.contains("answer.put(\"raw_text\", raw)")) add("private raw field")
        if (!text.contains(".put(\"error\", failure.javaClass.simpleName)")) add("class-only failure")
        if (text.contains("failure.toString()") || text.contains("failure.message")) add("failure detail")
    }
    @Test fun productionDelegatesToSameCandidateFilterAndPrivateTestDoesNotLogRawText() {
        val engines = setOf("OfflinePlateOcr.kt", "MlKitPlateOcr.kt")
        val main = sourceFile("src/main/java")
        for (file in main.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val text = file.readText()
            if (file.name in engines) assertEquals(file.name, emptyList<String>(), engineFindings(text))
            else assertFalse(file.name, Regex("recognizeText\\s*\\(").containsMatchIn(text))
        }
        assertEquals(emptyList<String>(), privateTestFindings(source("src/androidTest/java/il/sidewalks/reporter/PrivateRealPhotoOcrTest.kt")))
    }
    @Test fun rawLoggingPrintingUnboundedAndExceptionMutantsAreRejected() {
        val engine = source("src/main/java/il/sidewalks/reporter/recognition/MlKitPlateOcr.kt")
        assertTrue(engineFindings(engine.replace("return result.text.take(64_000)", "val raw = result.text.take(64_000); android.util.Log.d(\"OCR\", raw); return raw")).isNotEmpty())
        assertTrue(engineFindings(engine.replace(".take(64_000)", "")).isNotEmpty())
        val productionMutant = "PlateCandidates.fromText(MlKitPlateOcr.recognizeText(bitmap).also { android.util.Log.d(\"OCR\", it) })"
        assertTrue(Regex("recognizeText\\s*\\(").containsMatchIn(productionMutant))
        val test = source("src/androidTest/java/il/sidewalks/reporter/PrivateRealPhotoOcrTest.kt")
        for (print in listOf("print(raw)", "println(raw)", "System.out.print(raw)", "System.err.println(raw)", "failure.printStackTrace()")) {
            assertTrue(print, privateTestFindings(test + print).isNotEmpty())
        }
        assertTrue(privateTestFindings(test.replace("failure.javaClass.simpleName", "failure.toString()")).isNotEmpty())
    }
}
