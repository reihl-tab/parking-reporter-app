package il.sidewalks.reporter

// Historical M0 screen retained only in synthetic instrumented tests, not production UI.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DiagnosticScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exactConsentRequiredAndSecretsCleared() {
        var calls = 0
        val fake = DiagnosticRunner { _, _ -> calls++; DiagnosticResult(DiagnosticOutcome.SUCCESS, 200) }
        compose.setContent { MaterialTheme { DiagnosticScreen(fake) { true } } }
        compose.onNodeWithText("בדוק התחברות בלבד").assertIsNotEnabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.filesDir, "m0-synthetic.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("מפתח מנוי").performTextInput("synthetic-key")
        compose.onNodeWithText("שם משתמש").performTextInput("synthetic-user")
        compose.onNodeWithText("סיסמה").performTextInput("synthetic-password")
        compose.onNodeWithText("בדוק התחברות בלבד").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("בדוק התחברות בלבד").performClick()
        compose.waitUntil(5000) { calls == 1 }
        compose.waitForIdle()
        compose.onNodeWithText("synthetic-user").assertDoesNotExist()
        compose.onNodeWithText("בדוק התחברות בלבד").assertIsNotEnabled()
        assertEquals(1, calls)
    }
    @Test fun editingCredentialsRevokesConsent() {
        compose.setContent { MaterialTheme { DiagnosticScreen(DiagnosticRunner { _, _ -> error("must not run") }) { true } } }
        compose.onNodeWithText("מפתח מנוי").performTextInput("synthetic-key")
        compose.onNodeWithText("שם משתמש").performTextInput("synthetic-user")
        compose.onNodeWithText("סיסמה").performTextInput("synthetic-password")
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("שם משתמש").performTextInput("changed")
        compose.onNodeWithText("בדוק התחברות בלבד").assertIsNotEnabled()
    }
}

@Composable
fun DiagnosticScreen(runner: DiagnosticRunner, isMobile: () -> Boolean) {
    // Deliberately not rememberSaveable: secrets never enter saved instance state.
    var key by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var approved by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("מדרכות פנויות", style = MaterialTheme.typography.headlineMedium)
            Text("בדיקת התחברות בלבד - ⁦M0⁩", style = MaterialTheme.typography.titleLarge)
            Text("בדיקה מקומית של פרטי הגישה לאפליקציית 106. ללא יצירת פנייה, ללא העלאה.")
            OutlinedTextField(key, { key = it; approved = false }, label = { Text("מפתח מנוי") }, visualTransformation = PasswordVisualTransformation(), enabled = !running, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it; approved = false }, label = { Text("שם משתמש") }, enabled = !running, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it; approved = false }, label = { Text("סיסמה") }, visualTransformation = PasswordVisualTransformation(), enabled = !running, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(approved, { approved = it }, enabled = !running); Text("מאשר ניסיון התחברות אחד בלבד, ללא דיווח") }
            Button(onClick = {
                val credentials = LoginCredentials(key, username, password)
                val mobile = isMobile()
                running = true
                key = ""; username = ""; password = ""; approved = false
                scope.launch {
                    result = withContext(Dispatchers.IO) { runner.run(credentials, mobile) }
                    running = false
                }
            }, enabled = approved && !running && listOf(key, username, password).all { it.isNotBlank() }) {
                Text(if (running) "בודק..." else "בדוק התחברות בלבד")
            }
            result?.let { r ->
                // Name the missing values when the diagnostic reports them
                // (constant field IDs, never values).
                Text("תוצאת בדיקה: ⁦${r.outcome.name}⁩ ⁦${r.httpStatus ?: "-"}⁩" +
                    (if (r.missingFields.isNotEmpty()) " ⁦חסר: " + r.missingFields.joinToString(", ") + "⁩" else ""))
            }
            Text("אין ניסיון חוזר אוטומטי. דווח רק על התוצאה, לא על הפרטים.")
        }
    }
    }
}
