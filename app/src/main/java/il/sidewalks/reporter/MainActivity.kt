package il.sidewalks.reporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.WindowManager
import android.app.KeyguardManager
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import il.sidewalks.reporter.ui.AppShell
import il.sidewalks.reporter.ui.FirstRunStore
import il.sidewalks.reporter.ui.theme.AppTheme
import il.sidewalks.reporter.ui.theme.NavyStatusBar
import il.sidewalks.reporter.security.SecureSettings
import il.sidewalks.reporter.security.ReporterSettings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var entryNonce = 0L
    private val entrySignal = mutableStateOf<EntrySignal?>(null)
    private var authenticatedAction: (() -> Unit)? = null
    private var cameraAction: (() -> Unit)? = null
    private val authentication = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val action = authenticatedAction
        authenticatedAction = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = cameraAction
        cameraAction = null
        if (granted) action?.invoke()
    }
    private fun authenticate(title: String, subtitle: String, action: () -> Unit) {
        val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val intent = manager.createConfirmDeviceCredentialIntent(title, subtitle) ?: return
        authenticatedAction = action
        authentication.launch(intent)
    }
    private fun camera(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) action()
        else { cameraAction = action; cameraPermission.launch(Manifest.permission.CAMERA) }
    }
    private fun handleEntryIntent(intent: Intent?) {
        @Suppress("DEPRECATION")
        val stream = intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        val request = EntryIntents.parse(intent?.action, intent?.dataString, intent?.type, stream) ?: return
        entryNonce += 1
        entrySignal.value = EntrySignal(entryNonce, request)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEntryIntent(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Only the first creation consumes the entry intent: after a rotation
        // the request has already routed (its draft is reserved in the ledger)
        // and re-firing it would steal the screen mid-flow.
        if (savedInstanceState == null) handleEntryIntent(intent)
        // Issue #187: one-time first-run welcome before the main flow.
        val firstRun = FirstRunStore(this)
        setContent { AppTheme { NavyStatusBar {
            var showFirstRun by remember { mutableStateOf(!firstRun.seen()) }
            AppShell(
                authenticate = ::authenticate,
                requestCamera = ::camera,
                readSettings = { SecureSettings(this).read() },
                saveSettings = { settings -> SecureSettings(this).save(settings) },
                entry = entrySignal,
                showFirstRun = showFirstRun,
                onFirstRunDone = { firstRun.markSeen(); showFirstRun = false },
            )
        } } }
    }
}
