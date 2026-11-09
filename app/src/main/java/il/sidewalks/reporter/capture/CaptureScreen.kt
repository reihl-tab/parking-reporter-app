package il.sidewalks.reporter.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import il.sidewalks.reporter.i18n.LocalStrings
import il.sidewalks.reporter.i18n.ProvideStringsIfMissing
import java.io.File
import java.util.UUID

/** Permission must be granted by the caller. Original JPEG is written once, never re-encoded. */
@Composable
fun CaptureScreen(onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val capture = remember { ImageCapture.Builder().build() }
    var ready by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    // A catalog key, translated when shown so it follows a language switch.
    var error by remember { mutableStateOf<String?>(null) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    DisposableEffect(lifecycle) {
        onDispose { if (providerFuture.isDone) providerFuture.get().unbindAll() }
    }
    ProvideStringsIfMissing {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Text(s.t("capture.title"), style = MaterialTheme.typography.titleLarge)
        AndroidView(factory = { ctx ->
            PreviewView(ctx).also { view ->
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        ready = true
                    } catch (_: Exception) { error = "capture.camera_unavailable" }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, modifier = Modifier.weight(1f).fillMaxWidth())
        error?.let { Text(s.t(it)) }
        Button(enabled = ready && !pending, onClick = {
            pending = true
            val file = originalFile(context)
            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { pending = false; onCaptured(file) }
                    override fun onError(exception: ImageCaptureException) {
                        pending = false; file.delete(); error = "capture.failed"
                    }
                })
        }) { Text(s.t(if (pending) "capture.saving" else "capture.shutter")) }
        TextButton(onClick = onCancel) { Text(s.t("capture.back")) }
    }
    }
}
private fun originalFile(context: Context): File {
    val directory = File(context.noBackupFilesDir, "originals").apply { check(mkdirs() || isDirectory) }
    return File(directory, "${UUID.randomUUID()}.jpg")
}
