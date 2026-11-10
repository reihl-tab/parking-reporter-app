package il.sidewalks.reporter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.i18n.LocalStrings
import il.sidewalks.reporter.i18n.ProvideStringsIfMissing

/** A format sample for the ISO time field, the same in every language (not translated). */
private const val ISO_TIME_EXAMPLE = "\u20662026-01-01T12:00:00+02:00\u2069"

@Composable
fun ReviewScreen(initial: ReviewDraft, image: ImageBitmap?, onSave: (ReviewDraft) -> Unit, onBack: () -> Unit, onOutgoingPreview: (() -> Unit)? = null, onSubmit: ((ReviewDraft) -> Unit)? = null, submitting: Boolean = false, submissionNote: String = "", missingCredentialKeys: List<String> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plates by remember(initial.evidence.stableId) { mutableStateOf<List<String>>(emptyList()) }
    var useGoogle by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    // Status lines hold catalog keys and are translated when shown, so they follow a language switch.
    var ocrStatus by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<il.sidewalks.reporter.evidence.AddressSuggestion>>(emptyList()) }
    var geocoding by remember { mutableStateOf(false) }
    var geocodeStatus by remember { mutableStateOf<String?>(null) }
    var draft by remember(initial.evidence.stableId) { mutableStateOf(initial) }
    var time by remember(initial.evidence.stableId) { mutableStateOf(initial.confirmedTime.orEmpty()) }
    var latitude by remember(initial.evidence.stableId) { mutableStateOf(initial.confirmedLatitude?.toString().orEmpty()) }
    var longitude by remember(initial.evidence.stableId) { mutableStateOf(initial.confirmedLongitude?.toString().orEmpty()) }
    var editing by remember(initial.evidence.stableId) { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    suspend fun recognize() {
        recognizing = true
        try {
            plates = withContext(Dispatchers.Default) {
                try { if (useGoogle) il.sidewalks.reporter.recognition.MlKitPlateOcr.recognize(image!!.asAndroidBitmap()) else il.sidewalks.reporter.recognition.OfflinePlateOcr(context).recognize(image!!.asAndroidBitmap()) }
                catch (_: LinkageError) { emptyList() }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            }
            if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate == false) plates = emptyList()
            ocrStatus = if (plates.isEmpty()) "review.ocr.no_plate" else "review.ocr.suggestions_found"
        } finally { recognizing = false }
    }
    LaunchedEffect(initial.evidence.stableId, image) {
        if (image != null && draft.plate.isEmpty() && il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate != false) recognize()
    }
    ProvideStringsIfMissing {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.t("review.title"), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { editing = !editing }) { Text(s.t(if (editing) "review.edit.done" else "review.edit.start")) }
        }
        image?.let { Image(it, s.t("review.image_description"), modifier = Modifier.fillMaxWidth().height(220.dp)) }
        Box {
            TextButton(onClick = { categoryMenu = true }) { Text("${HebrewPresentation.category(s, draft.subject)} ▾") }
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                il.sidewalks.reporter.core.ReportCategory.entries.forEach { category ->
                    DropdownMenuItem(text = { Text(s.t(category.labelKey)) }, onClick = {
                        draft = draft.edit(subject = category.code); if (!category.needsPlate) plates = emptyList(); categoryMenu = false
                    })
                }
            }
        }
        Text(s.t("review.category_note"), style = MaterialTheme.typography.bodySmall)
        if (!editing) {
            if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate == true) {
                Text(reviewPlateText(s, draft.plate), style = MaterialTheme.typography.titleMedium)
                if (recognizing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(s.t("review.ocr.analyzing")) }
                Text(ocrStatus?.let { s.t(it) }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                plates.forEach { plate -> TextButton(onClick = { draft = draft.edit(plate = plate) }) { Text(reviewPlateSuggestionText(s, plate)) } }
            }
            Text(reviewAddressText(s, draft.address))
            Text(reviewTimeText(s, draft.confirmedTime))
            Text(draft.exactHebrewText.ifBlank { s.t("review.text_missing") })
            if (draft.unresolved.isNotEmpty()) Text(unresolvedText(s, draft.unresolved), style = MaterialTheme.typography.bodySmall)
        }
        if (editing) {
        Text(s.t("review.edit.original_time_title"))
        Text("\u2066${HebrewPresentation.evidenceTime(s, draft.evidence.capturedAtIso)}\u2069", modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Ltr))
        Text(reviewGpsText(s, hasGps = draft.evidence.latitude != null))
        Text(s.t("review.edit.privacy_note"))
        if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate != false) {
        Row { Checkbox(checked = useGoogle, enabled = !recognizing, onCheckedChange = { useGoogle = it; plates = emptyList(); ocrStatus = null }); Text(s.t("review.edit.use_mlkit")) }
        Button(enabled = image != null && !recognizing, onClick = { scope.launch { recognize() } }, modifier = Modifier.fillMaxWidth()) { Text(s.t(if (recognizing) "review.edit.recognizing" else "review.edit.suggest_plate")) }
        Text(ocrStatus?.let { s.t(it) }.orEmpty())
        plates.forEach { plate -> TextButton(onClick = { draft = draft.edit(plate = plate) }) { Text(reviewPlateSuggestionText(s, plate)) } }
        if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate == true) OutlinedTextField(draft.plate, { draft = draft.edit(plate = it) }, singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr),
            label = { Text(s.t("review.edit.plate_field")) }, modifier = Modifier.width(216.dp))
        }
        OutlinedTextField(draft.address, { draft = draft.edit(address = it) }, label = { Text(s.t("review.edit.address_field")) }, modifier = Modifier.fillMaxWidth())
        Text(s.t("review.edit.geocode_note"))
        Button(enabled = !geocoding && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null, onClick = {
            geocoding = true
            scope.launch {
                suggestions = withContext(Dispatchers.IO) {
                    try { il.sidewalks.reporter.evidence.AddressResolver(context).suggest(latitude.toDouble(), longitude.toDouble()) }
                    catch (_: Exception) { emptyList() }
                }
                geocoding = false
                geocodeStatus = if (suggestions.isEmpty()) "review.geocode.no_suggestions" else "review.geocode.check_house_number"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.edit.geocode_button")) }
        Text(geocodeStatus?.let { s.t(it) }.orEmpty())
        suggestions.forEach { suggestion ->
            TextButton(onClick = { draft = draft.edit(address = suggestion.display).copy(unresolved = setOf(ReviewWarning.SUGGESTED_ADDRESS_NEEDS_CONFIRMATION.code)) }) {
                Text(reviewAddressSuggestionText(s, suggestion.display))
            }
        }
        OutlinedTextField(draft.exactHebrewText, { draft = draft.edit(text = it) }, label = { Text(s.t("review.edit.text_field")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Text(s.t("review.edit.confirm_note"))
        Text(ISO_TIME_EXAMPLE, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(time, { time = it; draft = draft.copy(unresolved = setOf(ReviewWarning.TIME_NEEDS_CONFIRMATION.code), approvedDigest = null) }, textStyle = TextStyle(textDirection = TextDirection.Ltr), label = { Text(s.t("review.edit.time_field")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(latitude, { latitude = it; draft = draft.copy(unresolved = setOf(ReviewWarning.LOCATION_NEEDS_CONFIRMATION.code), approvedDigest = null) }, label = { Text(s.t("review.edit.latitude_field")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(longitude, { longitude = it; draft = draft.copy(unresolved = setOf(ReviewWarning.LOCATION_NEEDS_CONFIRMATION.code), approvedDigest = null) }, label = { Text(s.t("review.edit.longitude_field")) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { draft = draft.confirmEvidence(time, latitude.toDoubleOrNull(), longitude.toDoubleOrNull()) }, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.edit.confirm_button")) }
        if (draft.unresolved.isNotEmpty()) Text(unresolvedText(s, draft.unresolved))
        }
        Text(s.t("review.approval_note"))
        Button(enabled = draft.isReady(), onClick = { draft = draft.approve(); onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.approve_button")) }
        Text(s.t(if (draft.isApproved()) "review.approved" else "review.not_approved"))
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.save_draft")) }
        onOutgoingPreview?.let { action -> Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.outgoing_preview")) } }
        if (onSubmit != null && missingCredentialKeys.isNotEmpty()) {
            // Owner 2026-11-09: when required municipal values are unset, say which
            // ones and grey out send (UI only; the unlock gate before submit is unchanged).
            // Only where a live submit exists - a trial/disabled build shows the
            // generic disabled button instead.
            Text(s.t("review.submit.missing_credentials") + " " + missingCredentialKeys.joinToString(", ") { s.t(it) }, style = MaterialTheme.typography.bodySmall)
        }
        if (onSubmit != null) {
            // Send the draft this screen actually shows, not some other copy.
            Button(enabled = draft.isApproved() && !submitting && missingCredentialKeys.isEmpty(), onClick = { onSubmit(draft) }, modifier = Modifier.fillMaxWidth()) { Text(s.t(if (submitting) "review.submit.sending" else "review.submit.button")) }
            if (submissionNote.isNotEmpty()) Text(submissionNote, style = MaterialTheme.typography.bodySmall)
        } else {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.submit.disabled_button")) }
            Text(s.t("review.submit.unavailable"), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(s.t("review.back")) }
    }
    }
}
