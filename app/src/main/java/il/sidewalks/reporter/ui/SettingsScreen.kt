package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.BuildConfig
import il.sidewalks.reporter.i18n.Language
import il.sidewalks.reporter.i18n.LanguageResolver
import il.sidewalks.reporter.i18n.LocalStrings
import il.sidewalks.reporter.i18n.ProvideStringsIfMissing
import il.sidewalks.reporter.i18n.TextDir
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
fun SettingsScreen(
    initial: ReporterSettings?,
    save: (ReporterSettings, () -> Unit) -> Unit,
    back: () -> Unit,
    debugTools: (@Composable () -> Unit)? = null,
    selectedLanguage: String? = null,
    onLanguageSelected: ((String?) -> Unit)? = null,
) {
    var firstName by remember { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(initial?.lastName.orEmpty()) }
    var id by remember { mutableStateOf(initial?.idNumber.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var subscriptionKey by remember { mutableStateOf(initial?.municipalSubscriptionKey.orEmpty()) }
    var apiUsername by remember { mutableStateOf(initial?.municipalUsername.orEmpty()) }
    var apiPassword by remember { mutableStateOf(initial?.municipalPassword.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    ProvideStringsIfMissing {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.t("settings.privacy.mlkit_note"))
        Text(s.t("settings.privacy.title"), style = MaterialTheme.typography.titleLarge)
        Text(s.t("settings.privacy.description"))
        OutlinedTextField(firstName, { firstName = it; saved = false }, label = { Text(s.t("settings.field.first_name")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it; saved = false }, label = { Text(s.t("settings.field.last_name")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(id, { id = it; saved = false }, label = { Text(s.t("settings.field.id_number")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it; saved = false }, label = { Text(s.t("settings.field.phone")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it; saved = false }, label = { Text(s.t("settings.field.email")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
        Text(s.t("settings.api.title"), style = MaterialTheme.typography.titleMedium)
        Text(s.t("settings.api.description"), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(subscriptionKey, { subscriptionKey = it; saved = false }, label = { Text(s.t("settings.field.subscription_key")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiUsername, { apiUsername = it; saved = false }, label = { Text(s.t("settings.field.api_username")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiPassword, { apiPassword = it; saved = false }, label = { Text(s.t("settings.field.api_password")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
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
        } }, modifier = Modifier.fillMaxWidth()) { Text(s.t("settings.save.button")) }
        if (saved) Text(s.t("settings.save.done"))
        // The UI language lives in "ui-prefs": picking one never calls save, never asks for
        // authentication and never touches the fields above or the encrypted settings.
        onLanguageSelected?.let { select ->
            HorizontalDivider()
            LanguagePicker(s.catalog.languages, selectedLanguage, select)
        }
        debugTools?.let { tools ->
            HorizontalDivider()
            tools()
        }
        TextButton(onClick = back, modifier = Modifier.fillMaxWidth()) { Text(s.t("settings.back")) }
        Text(s.t("settings.version", mapOf("version" to BuildConfig.VERSION_NAME)), style = MaterialTheme.typography.bodySmall)
    }
    }
}

/** System default first, then each catalog language by its native name, in catalog order. */
@Composable
private fun LanguagePicker(languages: List<Language>, selected: String?, onSelect: (String?) -> Unit) {
    val s = LocalStrings.current
    Text(s.t("settings.language.title"), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        LanguageOption(s.t("settings.language.system_default"), null, selected == null) { onSelect(null) }
        languages.forEach { language ->
            LanguageOption(LanguageResolver.pickerLabel(language), language, selected == language.code) { onSelect(language.code) }
        }
    }
}

/** A native name is shown in its own language and direction, whatever the active UI language. */
@Composable
private fun LanguageOption(label: String, language: Language?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        val style = if (language == null) {
            LocalTextStyle.current
        } else {
            LocalTextStyle.current.copy(
                textDirection = if (language.dir == TextDir.RTL) TextDirection.Rtl else TextDirection.Ltr,
                localeList = LocaleList(language.code),
            )
        }
        Text(label, style = style)
    }
}
