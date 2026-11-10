package il.sidewalks.reporter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import il.sidewalks.reporter.i18n.Strings

/**
 * One-time welcome shown before the main flow (issue #187): what the app
 * does, that the reporter details must be filled in before the first send,
 * and a start button. Every color comes from the theme, so the municipal
 * palette work (issue #161) applies to this screen automatically.
 */
@Composable
fun FirstRunScreen(strings: Strings, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(strings.t("firstrun.title"), style = MaterialTheme.typography.headlineMedium)
        Text(strings.t("firstrun.intro"), style = MaterialTheme.typography.bodyLarge)
        Text(strings.t("firstrun.step_capture"), style = MaterialTheme.typography.bodyLarge)
        Text(strings.t("firstrun.step_review"), style = MaterialTheme.typography.bodyLarge)
        Text(strings.t("firstrun.step_send"), style = MaterialTheme.typography.bodyLarge)
        Text(strings.t("firstrun.settings_note"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(strings.t("firstrun.start"))
        }
    }
}
