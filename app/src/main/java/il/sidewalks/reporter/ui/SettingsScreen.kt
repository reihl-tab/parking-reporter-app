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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
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

/** A save merges blank PERSONAL fields against the freshly read stored settings:
 *  a blank personal field means "keep the stored value". The municipal credential
 *  fields save exactly as typed when [touchedMunicipal] is null (typed is final);
 *  when the caller passes the screen's touched set, an untouched credential field
 *  keeps its stored value and only a field the user actually edited can clear it
 *  (owner 2026-11-09: clearing a credential field and saving removes its value). */
internal fun mergeReporterSettings(value: ReporterSettings, stored: ReporterSettings?, touchedMunicipal: Set<String>? = null): ReporterSettings {
    val merged = value.copy(
        firstName = value.firstName.ifBlank { stored?.firstName.orEmpty() },
        lastName = value.lastName.ifBlank { stored?.lastName.orEmpty() },
        phone = value.phone.ifBlank { stored?.phone.orEmpty() },
        idNumber = value.idNumber.ifBlank { stored?.idNumber.orEmpty() },
        passportNumber = value.passportNumber.ifBlank { stored?.passportNumber.orEmpty() },
        email = value.email.ifBlank { stored?.email.orEmpty() })
    if (touchedMunicipal == null) return merged
    return merged.copy(
        municipalSubscriptionKey = if ("subscription_key" in touchedMunicipal) merged.municipalSubscriptionKey else stored?.municipalSubscriptionKey.orEmpty(),
        municipalUsername = if ("api_username" in touchedMunicipal) merged.municipalUsername else stored?.municipalUsername.orEmpty(),
        municipalPassword = if ("api_password" in touchedMunicipal) merged.municipalPassword else stored?.municipalPassword.orEmpty())
}

/** Caller obtains fresh system authentication before opening and again before saving. */
@Composable
fun SettingsScreen(
    initial: ReporterSettings?,
    save: (ReporterSettings, Set<String>, () -> Unit) -> Unit,
    back: () -> Unit,
    debugTools: (@Composable () -> Unit)? = null,
    selectedLanguage: String? = null,
    onLanguageSelected: ((String?) -> Unit)? = null,
    onLicenses: (() -> Unit)? = null,
    basicSetup: Boolean = false,
    setupError: String = "",
) {
    var firstName by remember { mutableStateOf(initial?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(initial?.lastName.orEmpty()) }
    var id by remember { mutableStateOf(initial?.idNumber.orEmpty()) }
    var passportMode by remember { mutableStateOf(initial?.passportMode ?: false) }
    var passport by remember { mutableStateOf(initial?.passportNumber.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    var subscriptionKey by remember { mutableStateOf(initial?.municipalSubscriptionKey.orEmpty()) }
    var apiUsername by remember { mutableStateOf(initial?.municipalUsername.orEmpty()) }
    var apiPassword by remember { mutableStateOf(initial?.municipalPassword.orEmpty()) }
    // Municipal fields the user actually edited this visit: only a touched field
    // saves as typed (blank = cleared); an untouched field keeps its stored value.
    var touchedMunicipal by remember { mutableStateOf(setOf<String>()) }
    var saved by remember { mutableStateOf(false) }
    var phoneFocused by remember { mutableStateOf(false) }
    var advanced106 by remember { mutableStateOf(false) }
    ProvideStringsIfMissing {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (basicSetup) {
            Text(s.t("setup.title"), style = MaterialTheme.typography.headlineMedium)
            Text(s.t("setup.description"))
            onLanguageSelected?.let { LanguageMenu(s.catalog.languages, selectedLanguage, it) }
            if (setupError.isNotBlank()) Text(setupError, color = MaterialTheme.colorScheme.error)
        } else Text(s.t("settings.privacy.mlkit_note"))
        Text(s.t("settings.privacy.title"), style = MaterialTheme.typography.titleLarge)
        if (!basicSetup) Text(s.t("settings.privacy.description"))
        OutlinedTextField(firstName, { firstName = ReporterInput.nameInput(it); saved = false }, label = { Text(s.t("settings.field.first_name")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = ReporterInput.nameInput(it); saved = false }, label = { Text(s.t("settings.field.last_name")) }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = passportMode, onCheckedChange = { passportMode = it; saved = false })
            Text(s.t("settings.passport.mode"))
        }
        if (passportMode) {
            OutlinedTextField(passport, { passport = it; saved = false }, label = { Text(s.t("settings.passport.number")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        } else OutlinedTextField(id, { id = ReporterInput.digits(it); saved = false }, label = { Text(s.t("settings.field.id_number")) }, isError = id.isNotBlank() && !ReporterInput.validId(id), supportingText = { if (id.isNotBlank() && !ReporterInput.validId(id)) Text(s.t("settings.id.invalid")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = ReporterInput.phoneInput(it); saved = false },
            label = { Text(s.t("settings.field.phone")) },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            isError = phone.isNotBlank() && !ReporterInput.validPhone(phone),
            supportingText = {
                if (phone.isNotBlank() && !ReporterInput.validPhone(phone)) Text(s.t("settings.phone.invalid"))
                else if (phone.isNotBlank() && !il.sidewalks.reporter.municipal.PassportSubmission.isIsraeliPhone(phone)) Text(s.t("settings.phone.international"))
            },
            visualTransformation = if (phoneFocused) VisualTransformation.None else PhoneDisplayTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                phoneFocused = focus.isFocused
                if (!focus.isFocused) phone = ReporterInput.canonicalPhone(phone)
            })
        OutlinedTextField(email, { email = it; saved = false }, label = { Text(s.t("settings.field.email")) }, isError = email.isNotBlank() && !ReporterInput.validEmail(email), supportingText = { if (email.isNotBlank() && !ReporterInput.validEmail(email)) Text(s.t("settings.email.invalid")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        if (!basicSetup) {
        HorizontalDivider()
        TextButton(onClick = { advanced106 = !advanced106 }) { Text(s.t("settings.api.advanced")) }
        if (advanced106) {
        Text(s.t("settings.api.title"), style = MaterialTheme.typography.titleMedium)
        Text(s.t("settings.api.description"), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(subscriptionKey, { subscriptionKey = it; saved = false; touchedMunicipal = touchedMunicipal + "subscription_key" }, label = { Text(s.t("settings.field.subscription_key")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiUsername, { apiUsername = it; saved = false; touchedMunicipal = touchedMunicipal + "api_username" }, label = { Text(s.t("settings.field.api_username")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiPassword, { apiPassword = it; saved = false; touchedMunicipal = touchedMunicipal + "api_password" }, label = { Text(s.t("settings.field.api_password")) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        }
        }
        // Blank personal fields keep the stored values via mergeReporterSettings in the
        // caller's save callback; a municipal credential field saves as typed only when
        // the user touched it (blank = cleared), otherwise its stored value is kept.
        // The fields clear after a save so no credential lingers on screen; a touched-then-
        // blanked credential still enables the button, so clearing works on a blank screen too.
        // stays disabled when every field is blank (and nothing was touched) or right after a save.
        val anyField = listOf(firstName, lastName, id, passport, phone, email, subscriptionKey, apiUsername, apiPassword).any { it.isNotBlank() }
        Button(enabled = !saved && (anyField || touchedMunicipal.isNotEmpty()) && (if (passportMode) il.sidewalks.reporter.municipal.PassportSubmission.validPassport(passport.ifBlank { initial?.passportNumber.orEmpty() }) else id.isBlank() || ReporterInput.validId(id)) && (phone.isBlank() || ReporterInput.validPhone(phone)) && (email.isBlank() || ReporterInput.validEmail(email)), onClick = { save(ReporterSettings(firstName.trim(), lastName.trim(), ReporterInput.canonicalPhone(phone), id.trim(), email.trim(),
            passportMode = passportMode, passportNumber = passport.trim(),
            municipalSubscriptionKey = subscriptionKey.trim(),
            municipalUsername = apiUsername.trim(),
            municipalPassword = apiPassword.trim()), touchedMunicipal) {
            firstName = ""; lastName = ""; id = ""; passport = ""; phone = ""; email = ""
            subscriptionKey = ""; apiUsername = ""; apiPassword = ""
            touchedMunicipal = setOf(); saved = true
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (basicSetup) s.t("setup.save_continue") else s.t("settings.save.button")) }
        if (saved) Text(s.t("settings.save.done"))
        // The UI language lives in "ui-prefs": picking one never calls save, never asks for
        // authentication and never touches the fields above or the encrypted settings.
        if (!basicSetup) onLanguageSelected?.let { select ->
            HorizontalDivider()
            LanguagePicker(s.catalog.languages, selectedLanguage, select)
        }
        debugTools?.let { tools ->
            HorizontalDivider()
            tools()
        }
        onLicenses?.let { open ->
            TextButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text(s.t("licenses.screen.title")) }
        }
        TextButton(onClick = back, modifier = Modifier.fillMaxWidth()) { Text(if (basicSetup) s.t("setup.later") else s.t("settings.back")) }
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

/** Compact language selection for onboarding, with native language names. No profile access. */
@Composable
private fun LanguageMenu(languages: List<Language>, selected: String?, onSelect: (String?) -> Unit) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val current = languages.firstOrNull { it.code == selected }?.let { LanguageResolver.pickerLabel(it) }
        ?: s.t("settings.language.system_default")
    Text(s.t("settings.language.title"), style = MaterialTheme.typography.titleMedium)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(current) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(s.t("settings.language.system_default")) }, onClick = { expanded = false; onSelect(null) })
            languages.forEach { language ->
                DropdownMenuItem(text = { Text(LanguageResolver.pickerLabel(language)) }, onClick = { expanded = false; onSelect(language.code) })
            }
        }
    }
}
