package org.ascensionassociation.fltr.ui.box

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.ascensionassociation.fltr.BuildConfig
import org.ascensionassociation.fltr.data.FltrStatus
import org.ascensionassociation.fltr.data.Pairing
import org.ascensionassociation.fltr.data.parseTime
import org.ascensionassociation.fltr.ui.HomeState
import org.ascensionassociation.fltr.ui.components.Astronaut
import org.ascensionassociation.fltr.ui.components.Pill
import org.ascensionassociation.fltr.ui.components.ProblemBanner
import org.ascensionassociation.fltr.ui.components.SectionTitle
import org.ascensionassociation.fltr.ui.components.StickerCard
import org.ascensionassociation.fltr.ui.theme.FltrTheme
import org.ascensionassociation.fltr.util.formatClock
import org.ascensionassociation.fltr.util.formatDuration
import org.ascensionassociation.fltr.util.formatRelative

@Composable
fun BoxScreen(
    state: HomeState,
    pairing: Pairing,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onPairAgain: () -> Unit,
    onForget: () -> Unit,
) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.problem?.let { problem ->
            item { ProblemBanner(problem, onRetry = onRefresh, onPairAgain = onPairAgain) }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Astronaut(Modifier.widthIn(max = 160.dp).fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Your FLTR box", style = MaterialTheme.typography.headlineSmall)
                Text(
                    pairing.displayHost,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.lastUpdated?.let {
                    Text(
                        "Updated ${formatRelative(it, Instant.now())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        state.status?.let { status -> item { StatusCard(status) } }
        item { ConnectionCard(pairing, onForget = { confirmForget = true }) }
        item { AboutCard() }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this FLTR box?") },
            text = {
                Text(
                    "This phone stops managing FLTR until you scan its pairing code again. FLTR keeps all of its " +
                        "settings. To unpair every phone, set FLTR_ARGS=-unpair=<today's date> on the box."
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmForget = false; onForget() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(status: FltrStatus) {
    val extra = FltrTheme.colors
    StickerCard(Modifier.fillMaxWidth()) {
        Text("Protection", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Fact(
            "Filtering",
            when (status.mode) {
                "queue" -> "Inline: blocked traffic never gets through"
                "log" -> "Passive: the first packet may slip through"
                else -> status.mode.ifEmpty { "Unknown" }
            },
        )
        Fact(
            "If FLTR stops",
            if (status.failClosed) "New connections wait until it's back (safest)"
            else "Traffic passes unfiltered (fail-open)",
            warn = !status.failClosed,
        )
        Fact("Running for", formatDuration(status.uptimeSeconds))
        parseTime(status.nextReset)?.let { Fact("Daily time resets", formatClock(it)) }
        Fact(
            "Rules",
            listOf("ip" to "IP", "mac" to "MAC", "ja4" to "app fingerprint")
                .mapNotNull { (k, label) -> status.rules[k]?.takeIf { it > 0 }?.let { "$it $label" } }
                .joinToString(", ")
                .ifEmpty { "None" },
        )
        Spacer(Modifier.height(10.dp))
        Text("Managed apps", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            status.protocols.forEach {
                Pill(
                    it.removePrefix("category:"),
                    container = extra.goodContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            "Set with -protocols in FLTR_ARGS on the box.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Fact(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warn) FltrTheme.colors.warn else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConnectionCard(pairing: Pairing, onForget: () -> Unit) {
    StickerCard(Modifier.fillMaxWidth()) {
        Text("Connection", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Fact("Address", pairing.url)
        Text("Certificate pin", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            pairing.pin,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        OutlinedButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Forget this box")
        }
    }
}

@Composable
private fun AboutCard() {
    Column {
        SectionTitle("About")
        Text(
            "FLTR ${BuildConfig.VERSION_NAME}. Artwork by Catalyst Vibes. Nunito typeface by the Nunito Project " +
                "Authors (SIL Open Font License).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
