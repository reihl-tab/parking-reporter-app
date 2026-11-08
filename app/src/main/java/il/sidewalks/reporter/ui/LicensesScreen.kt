package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.i18n.LocalStrings

/**
 * Third-party licences and data attributions (owner decision 2026-11-08):
 * OpenStreetMap/ODbL address points, the national street registry, Google
 * ML Kit, and the open-source libraries. Linked at the bottom of Settings.
 * Every visible string comes from the i18n catalog: this file is not in
 * HardcodedUiStringLintTest's PENDING set.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val texts = remember {
        listOf("Apache-2.0.txt", "NOTICE.txt", "libjpeg.txt", "libpng.txt", "Leptonica.txt")
            .map { name ->
                name to try {
                    context.assets.open("licenses/$name").bufferedReader().readText()
                } catch (_: Exception) {
                    ""
                }
            }
            .filter { it.second.isNotBlank() }
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(strings.t("licenses.screen.title"), style = MaterialTheme.typography.titleLarge)
        Text(strings.t("licenses.screen.intro"), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(strings.t("licenses.osm.title"), style = MaterialTheme.typography.titleMedium)
        Text(strings.t("licenses.osm.body"), style = MaterialTheme.typography.bodySmall)
        Text(strings.t("licenses.registry.title"), style = MaterialTheme.typography.titleMedium)
        Text(strings.t("licenses.registry.body"), style = MaterialTheme.typography.bodySmall)
        Text(strings.t("licenses.mlkit.title"), style = MaterialTheme.typography.titleMedium)
        Text(strings.t("licenses.mlkit.body"), style = MaterialTheme.typography.bodySmall)
        Text(strings.t("licenses.apache.title"), style = MaterialTheme.typography.titleMedium)
        Text(strings.t("licenses.apache.body"), style = MaterialTheme.typography.bodySmall)
        Text(strings.t("licenses.tesseract.title"), style = MaterialTheme.typography.titleMedium)
        Text(strings.t("licenses.tesseract.body"), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text(strings.t("licenses.texts.title"), style = MaterialTheme.typography.titleMedium)
        for ((name, body) in texts) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(strings.t("licenses.back.label"))
        }
    }
}
