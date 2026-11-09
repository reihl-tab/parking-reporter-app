package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.i18n.LocalStrings
import il.sidewalks.reporter.i18n.ProvideStringsIfMissing
import il.sidewalks.reporter.i18n.Strings
import il.sidewalks.reporter.ledger.ReportRecord

internal fun reportsOpenDraftText(strings: Strings, stableId: String): String =
    strings.t("reports.pending.open", mapOf("id" to stableId.take(8)))

internal fun reportsOpenApprovedText(strings: Strings, stableId: String): String =
    strings.t("reports.approved.open", mapOf("id" to stableId.take(8)))

/**
 * Report history plus unapproved drafts. Local ledger only: no municipal
 * status queries - status comes from municipal email or user-provided info.
 */
@Composable
fun ReportsScreen(records: List<ReportRecord>, open: (ReportRecord) -> Unit) {
    val pending = records.filter { it.reviewedDigest == null }
    val approved = records.filter { it.reviewedDigest != null }
    ProvideStringsIfMissing {
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.t("reports.title"), style = MaterialTheme.typography.headlineMedium)
        Text(s.t("reports.pending.title"), style = MaterialTheme.typography.titleMedium)
        if (pending.isEmpty()) Text(s.t("reports.pending.empty"), style = MaterialTheme.typography.bodySmall)
        pending.forEach { record ->
            OutlinedButton(onClick = { open(record) }, modifier = Modifier.fillMaxWidth()) { Text(reportsOpenDraftText(s, record.stableId)) }
        }
        Text(s.t("reports.approved.title"), style = MaterialTheme.typography.titleMedium)
        if (approved.isEmpty()) Text(s.t("reports.approved.empty"), style = MaterialTheme.typography.bodySmall)
        approved.forEach { record ->
            OutlinedButton(onClick = { open(record) }, modifier = Modifier.fillMaxWidth()) { Text(reportsOpenApprovedText(s, record.stableId)) }
        }
        Text(s.t("reports.history.title"), style = MaterialTheme.typography.titleMedium)
        Text(s.t("reports.history.empty"), style = MaterialTheme.typography.bodySmall)
    }
    }
}
