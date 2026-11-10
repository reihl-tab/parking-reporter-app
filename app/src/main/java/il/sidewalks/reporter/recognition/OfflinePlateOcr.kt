package il.sidewalks.reporter.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import il.sidewalks.reporter.intake.EncryptedPackage
import java.io.File

/** Bundled model, no SDK networking/telemetry; explicit user confirmation always required. */
class OfflinePlateOcr(private val context: Context) {
    companion object {
        const val MODEL_HASH = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
        private val lock = Any()
    }
    private fun modelDirectory(): File = synchronized(lock) {
        val directory = File(context.noBackupFilesDir, "offline-ocr").apply { check(mkdirs() || isDirectory) }
        val data = File(directory, "tessdata").apply { check(mkdirs() || isDirectory) }
        val model = File(data, "eng.traineddata")
        if (!model.exists()) {
            val temp = File(data, "eng.tmp")
            try {
                context.assets.open("tessdata/eng.traineddata").use { input -> temp.outputStream().use { input.copyTo(it) } }
                check(EncryptedPackage.sha256(temp.readBytes()) == MODEL_HASH)
                check(temp.renameTo(model))
            } finally { temp.delete() }
        }
        check(EncryptedPackage.sha256(model.readBytes()) == MODEL_HASH)
        directory
    }
    /** Input JPEG must already be bounded and orientation-corrected. */
    fun recognize(jpeg: ByteArray): List<String> {
        require(jpeg.size <= 1_100_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        require(bounds.outWidth in 1..2560 && bounds.outHeight in 1..2560)
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: error("Invalid image")
        try { return recognize(bitmap) } finally { bitmap.recycle() }
    }
    fun recognize(bitmap: Bitmap): List<String> = PlateCandidates.fromText(recognizeText(bitmap))
    /** Raw text for encrypted private diagnostics only; no logs or public output. */
    internal fun recognizeText(bitmap: Bitmap): String {
        require(bitmap.width in 1..2560 && bitmap.height in 1..2560)
        val tess = TessBaseAPI()
        try {
            check(tess.init(modelDirectory().absolutePath, "eng", TessBaseAPI.OEM_LSTM_ONLY))
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            tess.setImage(bitmap)
            return (tess.utF8Text ?: "").take(64_000)
        } finally { tess.recycle() }
    }
}
