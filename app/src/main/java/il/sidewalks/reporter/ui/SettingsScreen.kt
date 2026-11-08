package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.BuildConfig
import il.sidewalks.reporter.security.ReporterSettings

/** A save merges blank fields against the freshly read stored settings: after a save
 *  the screen clears itself and the caller drops its copy, so blank always means
 *  "keep the stored value". Removing a stored value needs a dedicated action. */
internal fun mergeReporterSettings(value: ReporterSettings, stored: ReporterSettings?): ReporterSettings = value.copy(
    firstName = value.firstName.ifBlank { stored?.firstName.orEmpty() },
    lastName = value.lastName.ifBlank { stored?.lastName.orEmpty() },
    phone = value.phone.ifBlank { stored?.phone.orEmpty() },
    idNumber = value.idNumber.ifBlank { stored?.idNumber.orEmpty() },
    email = value.email.ifBlank { stored?.email.orEmpty() },
    municipalSubscriptionKey = value.municipalSubscriptionKey.ifBlank { stored?.municipalSubscriptionKey.orEmpty() },
    municipalUsername = value.municipalUsername.ifBlank { stored?.municipalUsername.orEmpty() },
    municipalPassword = value.municipalPassword.ifBlank { stored?.municipalPassword.orEmpty() })

/** Caller obtains fresh system authentication before opening and again before saving. */
@Composable
fun SettingsScreen(initial: ReporterSettings?, save: (ReporterSettings, () -> Unit) -> Unit, back: () -> Unit, debugTools: (@Composable () -> Unit)? = null) {
    var firstName by remember { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(initial?.lastName.orEmpty()) }
    var id by remember { mutableStateOf(initial?.idNumber.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var subscriptionKey by remember { mutableStateOf(initial?.municipalSubscriptionKey.orEmpty()) }
    var apiUsername by remember { mutableStateOf(initial?.municipalUsername.orEmpty()) }
    var apiPassword by remember { mutableStateOf(initial?.municipalPassword.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google ML Kit: התמונות והלוחיות נשארות במכשיר; נתוני שימוש וביצועים נשלחים ל-Google. ה-SDK עשוי לפנות לשרתיה לעדכונים.")
        Text("הגדרות פרטיות", style = MaterialTheme.typography.titleLarge)
        Text("כל מדווח מזין את פרטיו שלו. הפרטים מוצפנים במכשיר, ללא גיבוי. אין שליחה או בדיקת התחברות ממסך זה. פרטי הגישה העירוניים מוזנים ידנית ונשמרים מוצפנים במכשיר בלבד.")
        OutlinedTextField(firstName, { firstName = it; saved = false }, label = { Text("שם פרטי") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it; saved = false }, label = { Text("שם משפחה") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(id, { id = it; saved = false }, label = { Text("מספר זהות") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it; saved = false }, label = { Text("טלפון") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it; saved = false }, label = { Text("אימייל") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("גישת API עירונית (הזנה ידנית)", style = MaterialTheme.typography.titleMedium)
        Text("המפתחות מוזנים ידנית ונשמרים מוצפנים במכשיר בלבד, ללא גיבוי. הם אינם חלק מהאפליקציה ואינם נשלחים למקום אחר. שליחה חיה אינה זמינה ותופעל רק באישור נפרד.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(subscriptionKey, { subscriptionKey = it; saved = false }, label = { Text("מפתח מנוי") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiUsername, { apiUsername = it; saved = false }, label = { Text("שם משתמש API") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiPassword, { apiPassword = it; saved = false }, label = { Text("סיסמת API") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        // Blank fields keep the stored values via mergeReporterSettings in the caller's
        // save callback, and the button stays disabled when every field is blank or right
        // after a save. Values are trimmed; blank can never mean "delete a stored value".
        val anyField = listOf(firstName, lastName, id, phone, email, subscriptionKey, apiUsername, apiPassword).any { it.isNotBlank() }
        Button(enabled = !saved && anyField, onClick = { save(ReporterSettings(firstName.trim(), lastName.trim(), phone.trim(), id.trim(), email.trim(),
            municipalSubscriptionKey = subscriptionKey.trim().ifBlank { initial?.municipalSubscriptionKey.orEmpty() },
            municipalUsername = apiUsername.trim().ifBlank { initial?.municipalUsername.orEmpty() },
            municipalPassword = apiPassword.trim().ifBlank { initial?.municipalPassword.orEmpty() })) {
            firstName = ""; lastName = ""; id = ""; phone = ""; email = ""
            subscriptionKey = ""; apiUsername = ""; apiPassword = ""; saved = true
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
