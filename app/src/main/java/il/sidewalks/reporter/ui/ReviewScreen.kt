package il.sidewalks.reporter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

@Composable
fun ReviewScreen(initial: ReviewDraft, image: ImageBitmap?, onSave: (ReviewDraft) -> Unit, onBack: () -> Unit, onOutgoingPreview: (() -> Unit)? = null, onSubmit: ((ReviewDraft) -> Unit)? = null, submitting: Boolean = false, submissionNote: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plates by remember(initial.evidence.stableId) { mutableStateOf<List<String>>(emptyList()) }
    var useGoogle by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    var ocrStatus by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<il.sidewalks.reporter.evidence.AddressSuggestion>>(emptyList()) }
    var geocoding by remember { mutableStateOf(false) }
    var geocodeStatus by remember { mutableStateOf("") }
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
            ocrStatus = if (plates.isEmpty()) "לא זוהתה לוחית; ניתן לערוך" else "נמצאו הצעות. בדוק כל ספרה בצילום"
        } finally { recognizing = false }
    }
    LaunchedEffect(initial.evidence.stableId, image) {
        if (image != null && draft.plate.isEmpty() && il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate != false) recognize()
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("בדיקת הדיווח", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { editing = !editing }) { Text(if (editing) "סיום עריכה" else "ערוך") }
        }
        image?.let { Image(it, "צילום ראיה", modifier = Modifier.fillMaxWidth().height(220.dp)) }
        Box {
            TextButton(onClick = { categoryMenu = true }) { Text("${HebrewPresentation.category(draft.subject)} ▾") }
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                il.sidewalks.reporter.core.ReportCategory.entries.forEach { category ->
                    DropdownMenuItem(text = { Text(category.hebrew) }, onClick = {
                        draft = draft.edit(subject = category.code); if (!category.needsPlate) plates = emptyList(); categoryMenu = false
                    })
                }
            }
        }
        Text("זיהוי קטגוריה אוטומטי עדיין בפיתוח. בחר קטגוריה לבדיקה.", style = MaterialTheme.typography.bodySmall)
        if (!editing) {
            if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate == true) {
                Text("לוחית: \u2066${draft.plate.ifBlank { "לא אושרה" }}\u2069", style = MaterialTheme.typography.titleMedium)
                if (recognizing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("מנתח את הצילום במכשיר...") }
                Text(ocrStatus, style = MaterialTheme.typography.bodySmall)
                plates.forEach { plate -> TextButton(onClick = { draft = draft.edit(plate = plate) }) { Text("הצעה בלבד: \u2066$plate\u2069") } }
            }
            Text("כתובת: ${draft.address.ifBlank { "נדרשת השלמה בעריכה" }}")
            Text("זמן: \u2066${HebrewPresentation.evidenceTime(draft.confirmedTime)}\u2069")
            Text(draft.exactHebrewText.ifBlank { "תיאור הדיווח נדרש בעריכה" })
            if (draft.unresolved.isNotEmpty()) Text("דרוש בירור: ${draft.unresolved.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
        if (editing) {
        Text("זמן הראיה המקורי")
        Text("\u2066${HebrewPresentation.evidenceTime(draft.evidence.capturedAtIso)}\u2069", modifier = Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Ltr))
        Text("GPS: ${if (draft.evidence.latitude == null) "חסר" else "נקרא מהראיה"}")
        Text("התמונות והלוחיות מעובדות במכשיר ואינן נשלחות ל-Google. ML Kit שולח ל-Google נתוני שימוש וביצועים ועשוי לפנות לשרתיה לעדכונים. תוצאה היא הצעה בלבד.")
        if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate != false) {
        Row { Checkbox(checked = useGoogle, enabled = !recognizing, onCheckedChange = { useGoogle = it; plates = emptyList(); ocrStatus = "" }); Text("זיהוי עם Google ML Kit במקום Tesseract") }
        Button(enabled = image != null && !recognizing, onClick = { scope.launch { recognize() } }, modifier = Modifier.fillMaxWidth()) { Text(if (recognizing) "מזהה במכשיר..." else "הצע לוחית מתוך הצילום") }
        Text(ocrStatus)
        plates.forEach { plate -> TextButton(onClick = { draft = draft.edit(plate = plate) }) { Text("הצעה בלבד: \u2066$plate\u2069") } }
        if (il.sidewalks.reporter.core.ReportCategory.from(draft.subject)?.needsPlate == true) OutlinedTextField(draft.plate, { draft = draft.edit(plate = it) }, singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr),
            label = { Text("לוחית ללא מקפים") }, modifier = Modifier.width(216.dp))
        }
        OutlinedTextField(draft.address, { draft = draft.edit(address = it) }, label = { Text("רחוב ומספר בית שאישרת") }, modifier = Modifier.fillMaxWidth())
        Text("חיפוש כתובת שולח קואורדינטות בלבד לספק המיקום של המכשיר. מספרי בית הם הצעות בלבד.")
        Button(enabled = !geocoding && latitude.toDoubleOrNull() != null && longitude.toDoubleOrNull() != null, onClick = {
            geocoding = true
            scope.launch {
                suggestions = withContext(Dispatchers.IO) {
                    try { il.sidewalks.reporter.evidence.AddressResolver(context).suggest(latitude.toDouble(), longitude.toDouble()) }
                    catch (_: Exception) { emptyList() }
                }
                geocoding = false
                geocodeStatus = if (suggestions.isEmpty()) "אין הצעות, הזן כתובת שבדקת" else "בדוק ואשר את מספר הבית בעצמך"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("חפש הצעות כתובת לפי המיקום") }
        Text(geocodeStatus)
        suggestions.forEach { suggestion ->
            TextButton(onClick = { draft = draft.edit(address = suggestion.display).copy(unresolved = setOf("כתובת דורשת אישור")) }) {
                Text("הצעה בלבד: ${suggestion.display}")
            }
        }
        OutlinedTextField(draft.exactHebrewText, { draft = draft.edit(text = it) }, label = { Text("הטקסט המדויק לבדיקה") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Text("מלא ואשר זמן עם אזור זמן וקואורדינטות ידועות לך. אין לנחש מידע חסר. הערכים המקוריים נשמרים בנפרד.")
        Text("\u20662026-01-01T12:00:00+02:00\u2069", modifier = Modifier.fillMaxWidth())
        OutlinedTextField(time, { time = it; draft = draft.copy(unresolved = setOf("זמן דורש אישור"), approvedDigest = null) }, textStyle = TextStyle(textDirection = TextDirection.Ltr), label = { Text("זמן מאושר עם אזור זמן (ISO)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(latitude, { latitude = it; draft = draft.copy(unresolved = setOf("מיקום דורש אישור"), approvedDigest = null) }, label = { Text("קו רוחב") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(longitude, { longitude = it; draft = draft.copy(unresolved = setOf("מיקום דורש אישור"), approvedDigest = null) }, label = { Text("קו אורך") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { draft = draft.confirmEvidence(time, latitude.toDoubleOrNull(), longitude.toDoubleOrNull()) }, modifier = Modifier.fillMaxWidth()) { Text("אשר זמן, מיקום וכתובת שבדקת") }
        if (draft.unresolved.isNotEmpty()) Text("דרוש בירור: ${draft.unresolved.joinToString()}")
        }
        Text("כל שינוי מבטל אישור. האישור כאן אינו שולח לעירייה או לרשת חברתית.")
        Button(enabled = draft.isReady(), onClick = { draft = draft.approve(); onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text("אשר את התוכן לבדיקה") }
        Text(if (draft.isApproved()) "התוכן אושר מקומית, לא נשלח" else "התוכן לא אושר")
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) { Text("שמור טיוטה") }
        onOutgoingPreview?.let { action -> Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text("בדוק תמונה נפרדת ללא נתוני צילום") } }
        if (onSubmit != null) {
            // Send the draft this screen actually shows, not some other copy.
            Button(enabled = draft.isApproved() && !submitting, onClick = { onSubmit(draft) }, modifier = Modifier.fillMaxWidth()) { Text(if (submitting) "שולח לעירייה..." else "שלח לעירייה") }
            if (submissionNote.isNotEmpty()) Text(submissionNote, style = MaterialTheme.typography.bodySmall)
        } else {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("שלח") }
            Text("שליחה אינה זמינה בגרסת הבדיקה", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("חזור") }
    }
    }
}
