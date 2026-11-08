package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.ledger.ReportRecord

/**
 * Report history plus unapproved drafts. Local ledger only: no municipal
 * status queries - status comes from municipal email or user-provided info.
 */
@Composable
fun ReportsScreen(records: List<ReportRecord>, open: (ReportRecord) -> Unit) {
    val pending = records.filter { it.reviewedDigest == null }
    val approved = records.filter { it.reviewedDigest != null }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("הדיווחים שלי", style = MaterialTheme.typography.headlineMedium)
        Text("טיוטות שלא אושרו", style = MaterialTheme.typography.titleMedium)
        if (pending.isEmpty()) Text("אין טיוטות ממתינות", style = MaterialTheme.typography.bodySmall)
        pending.forEach { record ->
            OutlinedButton(onClick = { open(record) }, modifier = Modifier.fillMaxWidth()) { Text("פתח טיוטה ${record.stableId.take(8)}") }
        }
        Text("אושרו וממתינים לשליחה", style = MaterialTheme.typography.titleMedium)
        if (approved.isEmpty()) Text("אין דיווחים שאושרו וממתינים לשליחה", style = MaterialTheme.typography.bodySmall)
        approved.forEach { record ->
            OutlinedButton(onClick = { open(record) }, modifier = Modifier.fillMaxWidth()) { Text("פתח דיווח ${record.stableId.take(8)}") }
        }
        Text("היסטוריית דיווחים", style = MaterialTheme.typography.titleMedium)
        Text("אין דיווחים שנשלחו עדיין. שליחה חיה תופעל רק באישור נפרד, וסטטוס יגיע מדואר העירייה בלבד.", style = MaterialTheme.typography.bodySmall)
    }
    }
}
