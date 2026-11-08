package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.BuildConfig
import il.sidewalks.reporter.security.ReporterSettings

/** Caller obtains fresh system authentication before opening and again before saving. */
@Composable
fun SettingsScreen(initial: ReporterSettings?, save: (ReporterSettings, () -> Unit) -> Unit, back: () -> Unit, debugTools: (@Composable () -> Unit)? = null) {
    var firstName by remember { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(initial?.lastName.orEmpty()) }
    var id by remember { mutableStateOf(initial?.idNumber.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google ML Kit: התמונות והלוחיות נשארות במכשיר; נתוני שימוש וביצועים נשלחים ל-Google. ה-SDK עשוי לפנות לשרתיה לעדכונים.")
        Text("הגדרות פרטיות", style = MaterialTheme.typography.titleLarge)
        Text("כל מדווח מזין את פרטיו שלו. הפרטים מוצפנים במכשיר, ללא גיבוי. אין שליחה או בדיקת התחברות ממסך זה. פרטי הגישה העירוניים אינם מוצגים למשתמש.")
        OutlinedTextField(firstName, { firstName = it; saved = false }, label = { Text("שם פרטי") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it; saved = false }, label = { Text("שם משפחה") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(id, { id = it; saved = false }, label = { Text("מספר זהות") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it; saved = false }, label = { Text("טלפון") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it; saved = false }, label = { Text("אימייל") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { save(ReporterSettings(firstName, lastName, phone, id, email)) {
            firstName = ""; lastName = ""; id = ""; phone = ""; email = ""; saved = true
        } }, modifier = Modifier.fillMaxWidth()) { Text("אמת זהות ושמור מוצפן") }
        if (saved) Text("נשמר מוצפן, הפרטים הוסרו מהמסך")
        debugTools?.let { tools ->
            HorizontalDivider()
            tools()
        }
        TextButton(onClick = back, modifier = Modifier.fillMaxWidth()) { Text("חזור") }
        Text("גרסה " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall)
    }
    }
}
