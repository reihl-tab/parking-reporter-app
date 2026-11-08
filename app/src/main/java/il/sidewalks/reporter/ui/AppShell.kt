package il.sidewalks.reporter.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import il.sidewalks.reporter.BuildConfig
import il.sidewalks.reporter.EntrySignal
import il.sidewalks.reporter.EntryIntents
import il.sidewalks.reporter.capture.CaptureScreen
import il.sidewalks.reporter.core.ReviewDraft
import il.sidewalks.reporter.evidence.EvidenceReader
import il.sidewalks.reporter.evidence.GalleryImport
import il.sidewalks.reporter.ledger.ReportDatabase
import il.sidewalks.reporter.ledger.ReportRecord
import il.sidewalks.reporter.security.ReporterSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppShell(
    authenticate: (() -> Unit) -> Unit,
    requestCamera: (() -> Unit) -> Unit,
    readSettings: () -> ReporterSettings?,
    saveSettings: (ReporterSettings) -> Unit,
    entry: State<EntrySignal?> = mutableStateOf(null),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { Room.databaseBuilder(context, ReportDatabase::class.java, "report-ledger.db").build() }
    DisposableEffect(Unit) { onDispose { database.close() } }
    var page by remember { mutableStateOf("home") }
    var profile by remember { mutableStateOf<ReporterSettings?>(null) }
    var draft by remember { mutableStateOf<ReviewDraft?>(null) }
    var outgoing by remember { mutableStateOf<il.sidewalks.reporter.evidence.OutgoingImage?>(null) }
    var original by remember { mutableStateOf<File?>(null) }
    var revision by remember { mutableLongStateOf(0) }
    var records by remember { mutableStateOf<List<ReportRecord>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    suspend fun reload() { records = withContext(Dispatchers.IO) { database.reports().all() } }
    LaunchedEffect(Unit) {
        reload()
        // A copy whose import never finished (rotation or process kill while
        // the location dialog was open, a failed reserve) has no ledger row:
        // sweep any original the ledger does not reference.
        withContext(Dispatchers.IO) {
            try {
                // Ledger rows' relative paths; GalleryImport compares only the file names.
                val referenced = database.reports().all().map { it.originalRelativePath }.toSet()
                GalleryImport.sweepUnreferenced(File(context.noBackupFilesDir, "originals"), referenced)
            } catch (_: Exception) {}
        }
    }
    var status by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<File?>(null) }
    fun saveDraft(value: ReviewDraft) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    database.reports().store(value.evidence.stableId, revision,
                        il.sidewalks.reporter.ledger.DraftCodec.encode(value),
                        value.digest().takeIf { value.isApproved() })
                }
                check(updated == 1)
                revision += 1
                draft = value; status = "נשמר מקומית, לא נשלח"
                reload()
            } catch (_: Exception) { status = "שמירה נכשלה, לא בוצעה שליחה" }
            finally { saving = false }
        }
    }
    fun openSettings() {
        authenticate {
            try { profile = readSettings(); page = "settings" }
            catch (_: Exception) { status = "לא ניתן לקרוא מידע מוצפן" }
        }
    }
    fun startReview(file: File, usePhotoLocation: Boolean = true) {
        scope.launch {
            try {
                var evidence = withContext(Dispatchers.IO) { EvidenceReader().read(file) }
                if (!usePhotoLocation && (evidence.latitude != null || evidence.longitude != null)) {
                    evidence = evidence.copy(latitude = null, longitude = null)
                }
                val missing = buildSet {
                    if (evidence.latitude == null) add("GPS חסר")
                    if (evidence.capturedAtIso == null) add("זמן חסר") else add("אזור זמן דורש אישור")
                    add("כתובת ומספר בית דורשים אישור")
                }
                try {
                    withContext(Dispatchers.IO) {
                        database.reports().reserve(ReportRecord(evidence.stableId, evidence.originalSha256,
                            "originals/${file.name}", 0,
                            il.sidewalks.reporter.ledger.DraftCodec.encode(ReviewDraft(evidence, "", "", "", "", missing)), null))
                    }
                } catch (e: Exception) {
                    // The reserve failed (for example a duplicate original), so
                    // the copied file has no ledger row: delete it rather than
                    // leave an EXIF-carrying original orphaned on disk.
                    withContext(Dispatchers.IO) { GalleryImport.deleteOrphaned(file) }
                    throw e
                }
                original = file; revision = 0
                draft = ReviewDraft(evidence, "", "", "", "", missing)
                reload(); page = "review"
            } catch (e: Exception) {
                Log.w("AppShell", "review start failed", e)
                status = "הראיה כפולה או שלא נשמרה, לא בוצעה שליחה"; page = "home"
            }
        }
    }
    fun openRecord(record: ReportRecord) {
        try {
            draft = il.sidewalks.reporter.ledger.DraftCodec.decode(record.draftJson).copy(approvedDigest = record.reviewedDigest)
            val file = File(context.noBackupFilesDir, record.originalRelativePath)
            check(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + "/") && file.isFile)
            check(EvidenceReader().read(file, record.stableId).originalSha256 == record.originalSha256)
            original = file; revision = record.revision; page = "review"
        } catch (_: Exception) { status = "לא ניתן לשחזר את הראיה; אין שליחה" }
    }
    // The system photo picker needs no permissions (no camera, no location).
    // EXIF time and GPS, when present, come from the picked original bytes.
    // One import pipeline for the system picker and the share-sheet target
    // (issue #149): copy bytes, read EXIF from the copy, confirm location use.
    fun importShared(uri: Uri) {
        scope.launch {
            var copied: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    GalleryImport.copyToOriginals({ context.contentResolver.openInputStream(uri) },
                        File(context.noBackupFilesDir, "originals"))
                }
                copied = file
                val hasGps = withContext(Dispatchers.IO) { EvidenceReader().read(file).latitude != null }
                if (hasGps) pendingImport = file else startReview(file)
            } catch (e: Exception) {
                Log.w("AppShell", "shared/gallery image import failed", e)
                copied?.let { orphan -> withContext(Dispatchers.IO) { GalleryImport.deleteOrphaned(orphan) } }
                status = "התמונה לא נקראה, לא בוצעה שליחה"
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importShared(uri)
    }
    // Deep link / Quick Settings tile / share-sheet entry (issue #149 phase 1).
    LaunchedEffect(entry.value) {
        when (val request = entry.value?.request) {
            // An entry arriving over the settings screen drops the decrypted
            // profile from memory before any navigation.
            is EntryIntents.Request.SharedPhoto -> { profile = null; importShared(Uri.parse(request.uriString)) }
            EntryIntents.Request.NewReport -> { profile = null; requestCamera { page = "camera" } }
            null -> {}
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Scaffold(
        bottomBar = {
            if (page == "home" || page == "settings" || page == "reports") {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == "home",
                        onClick = { profile = null; page = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("בית") },
                    )
                    NavigationBarItem(
                        selected = page == "reports",
                        onClick = { profile = null; page = "reports" },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("הדיווחים שלי") },
                    )
                    NavigationBarItem(
                        selected = page == "settings",
                        onClick = { openSettings() },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("הגדרות") },
                    )
                }
            }
        },
    ) { innerPadding ->
    Box(Modifier.padding(innerPadding)) {
    when (page) {
        "camera" -> CaptureScreen(onCaptured = { file -> startReview(file) }, onCancel = { page = "home" })
        "reports" -> ReportsScreen(records, open = { openRecord(it) })
        "review" -> draft?.let { value ->
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, original) {
                this.value = withContext(Dispatchers.IO) {
                    try {
                        original?.let { file ->
                            val prepared = il.sidewalks.reporter.evidence.OutgoingEvidence.prepare(file)
                            BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size)?.asImageBitmap()
                        }
                    } catch (_: Exception) { null }
                }
            }
            ReviewScreen(value, image, ::saveDraft, onBack = { page = "home" }, onOutgoingPreview = {
                scope.launch {
                    try {
                        val derivative = withContext(Dispatchers.IO) {
                            val file = original ?: error("Missing original")
                            il.sidewalks.reporter.evidence.OutgoingEvidence.prepare(file)
                        }
                        check(derivative.originalHash == value.evidence.originalSha256)
                        outgoing = derivative; page = "outgoing"
                    } catch (_: Exception) { status = "לא ניתן להכין תמונה לבדיקה; אין שליחה" }
                }
            })
        }
        "outgoing" -> outgoing?.let { value ->
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("תמונה נפרדת לבדיקה", style = MaterialTheme.typography.titleLarge)
                val bitmap = remember(value.hash) { BitmapFactory.decodeByteArray(value.jpeg, 0, value.jpeg.size).asImageBitmap() }
                Image(bitmap, "התמונה המדויקת המיועדת להעברה בעתיד", Modifier.fillMaxWidth().height(360.dp))
                Text("המקור לא השתנה. נתוני EXIF הוסרו מהעותק. בדוק שהלוחית והעבירה קריאות; אל תאשר תמונה שאיבדה ראיה.")
                Text("${value.width} × ${value.height}, ${value.jpeg.size} bytes")
                Text("שליחה והתחברות אינן זמינות בגרסת הבדיקה. אין אישור שליחה ממסך זה.")
                TextButton(onClick = { outgoing = null; page = "review" }, modifier = Modifier.fillMaxWidth()) { Text("חזור לבדיקה") }
            }
        }
        "settings" -> SettingsScreen(profile, save = { value, cleared ->
            authenticate {
                try {
                    // The screen clears itself and profile is dropped after a save, so blank
                    // fields must keep the stored values: merge against a fresh read.
                    saveSettings(mergeReporterSettings(value, readSettings())); profile = null; cleared(); status = "נשמר מוצפן"
                }
                catch (_: Exception) { status = "הצפנה או אימות נכשלו, לא נשמר מידע גלוי" }
            }
        }, back = { profile = null; page = "home" }, debugTools = if (BuildConfig.DEBUG) {
            {
                Text("כלי בדיקה (מצב פיתוח בלבד)", style = MaterialTheme.typography.titleMedium)
                Text("בדיקת פיתוח מקומית של חבילת ההצפנה: יוצרת דיווח מלאכותי, מצפינה ופותחת במכשיר. ללא רשת, ללא שליחה. מוסתרת לחלוטין מחוץ למצב פיתוח.")
                Button(onClick = {
                    scope.launch {
                        status = "מריץ בדיקה סינתטית מקומית"
                        status = withContext(Dispatchers.Default) {
                            try { il.sidewalks.reporter.intake.SyntheticIntakeDemo.run() }
                            catch (_: Exception) { "הבדיקה המקומית נכשלה, אין שליחה" }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("בדיקת פיתוח: חבילה מוצפנת ללא רשת") }
                if (status.isNotEmpty()) Text(status)
            }
        } else null)
        else -> Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("מדרכות פנויות", style = MaterialTheme.typography.headlineMedium)
            Text("צלם תמונה כדי להתחיל דיווח", style = MaterialTheme.typography.titleLarge)
            Text("הניתוח נעשה במכשיר. תוכל לבדוק ולתקן לפני שליחה.")
            Button(onClick = { requestCamera { page = "camera" } }, modifier = Modifier.fillMaxWidth()) {
                Icon(PhotoCameraIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("צלם תמונה")
            }
            OutlinedButton(onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.fillMaxWidth()) { Text("העלה תמונה") }
            Text("העלאה מהגלריה אינה דורשת הרשאות; זמן ומיקום נקראים מהתמונה אם קיימים.", style = MaterialTheme.typography.bodySmall)
            Text("גרסת בדיקה מקומית. אין שליחה.", style = MaterialTheme.typography.bodySmall)
            Text("טיוטות", style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty() && draft == null) Text("אין טיוטות שמורות", style = MaterialTheme.typography.bodySmall)
            records.forEach { record ->
                OutlinedButton(onClick = { openRecord(record) }, modifier = Modifier.fillMaxWidth()) { Text("פתח טיוטה ${record.stableId.take(8)}") }
            }
            if (draft != null) OutlinedButton(onClick = { page = "review" }, modifier = Modifier.fillMaxWidth()) { Text("המשך בדיקת טיוטה") }
            if (status.isNotEmpty()) Text(status)
            Text("התחברות לשירות הדיווח עדיין אינה זמינה. פרטי גישה עירוניים אינם חלק מהאפליקציה.", style = MaterialTheme.typography.bodySmall)
            Text("ביטול הרשאת מצלמה או אימות משאיר את המסך ללא פעולה. אין שמירת פרטים גלויים כחלופה.", style = MaterialTheme.typography.bodySmall)
        }
    }
    }
    }
    pendingImport?.let { file ->
        AlertDialog(
            onDismissRequest = {
                pendingImport = null
                scope.launch(Dispatchers.IO) { GalleryImport.deleteOrphaned(file) }
                status = "הייבוא בוטל, לא בוצעה שליחה"
            },
            title = { Text("נמצא מיקום בתמונה") },
            text = { Text("להשתמש במיקום מהתמונה לדיווח? ניתן גם בלי, ולסמן את המיקום ידנית.") },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; startReview(file, usePhotoLocation = true) }) { Text("השתמש במיקום") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null; startReview(file, usePhotoLocation = false) }) { Text("ללא מיקום") }
            },
        )
    }
    }
}
